#!/usr/bin/env python3
"""Reassemble target/User Guide.twee from the split passages and images,
then compile to HTML with tweego.

Reads docs/passages/NNN_<name>.txt in filename order, converts markdown
image links back to base64 data URIs, and writes target/User Guide.twee.
Then compiles to HTML using tweego with Harlowe format.

Markdown image link syntax produced by split.py:
    ![alt](../images/<name>-N.ext){style="max-width:100%;"}
The optional { ... } block restores extra img attributes (alt is always
taken from the ![...] text). Use `--output PATH` to write elsewhere.
"""

import argparse
import base64
import gzip
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PASSAGES_DIR = HERE / "passages"
IMAGES_DIR = HERE / "images"
DEFAULT_OUTPUT = HERE.parent / "target" / "User Guide.twee"
IFID = "3F375208-DB5B-4371-8FB8-9949032993D8"
HARLOWE_VERSION = "3.3.9"

MD_IMG_RE = re.compile(
    r'!\[([^\]]*)\]\(([^)]+)\)(?:{([^}]*?)})?', re.IGNORECASE
)
EXT_MIME = {"png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg", "gif": "image/gif", "webp": "image/webp"}

POM_PATH = HERE.parent / "pom.xml"
# The project's own version, not a dependency's: the <version> right after the
# beanbot <artifactId>.
POM_VERSION_RE = re.compile(
    r"<artifactId>beanbot</artifactId>\s*<version>([^<]+)</version>", re.DOTALL
)
# "Last updated for BeanBot v1.26.0" in the Home passage; the trailing tail
# catches suffixes like -SNAPSHOT or rc1 so the stamp never leaves a stale one.
VERSION_STAMP_RE = re.compile(r"(Last updated for BeanBot v)[0-9][\w.\-]*")

# Harlowe reads `_name` in passage text as a temp variable and prints
# "There isn't a temp variable named _name" at play time. A name with no letter
# (`alpha_1`) is not a variable; `_2beta`, `__b` and `_API_KEY` are.
HARLOWE_TEMP_VAR_RE = re.compile(r"_[A-Za-z0-9_]+")
PASSAGE_TAG_RE = re.compile(r"\[([^\]]*)\]\s*$")
RAW_TAGS = {"script", "stylesheet"}
# Regions where an underscore is literal, not passage text: fenced/inline
# verbatim, HTML comments, HTML tags (attributes), and markdown link targets.
FENCED_BLOCK_RE = re.compile(r"```.*?```", re.DOTALL)
BACKTICK_SPAN_RE = re.compile(r"`[^`]*`", re.DOTALL)
HTML_COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)
RAW_BLOCK_RE = re.compile(r"<(style|script)\b.*?</\1>", re.DOTALL | re.IGNORECASE)
MD_IMAGE_RE = re.compile(r"!\[[^\]]*\]\([^)]*\)(?:\{[^}]*\})?")
MD_LINK_TARGET_RE = re.compile(r"\]\([^)]*\)")
HTML_TAG_RE = re.compile(r"<[^>]*>")


def _blank(match: re.Match) -> str:
    """Remove a match but keep its newlines so reported line numbers stay true."""
    return "\n" * match.group(0).count("\n")


def _temp_vars(text: str, first_line: int):
    text = FENCED_BLOCK_RE.sub(_blank, text)
    text = BACKTICK_SPAN_RE.sub(_blank, text)
    text = HTML_COMMENT_RE.sub(_blank, text)
    text = RAW_BLOCK_RE.sub(_blank, text)
    text = MD_IMAGE_RE.sub(_blank, text)
    text = MD_LINK_TARGET_RE.sub("]", text)
    text = HTML_TAG_RE.sub(_blank, text)
    for offset, line in enumerate(text.splitlines()):
        for match in HARLOWE_TEMP_VAR_RE.finditer(line):
            token = match.group(0)
            if any(ch.isalpha() for ch in token):
                yield first_line + offset, token


def find_temp_vars(passages_dir: Path = PASSAGES_DIR) -> list[str]:
    """Return `file:line: token` for underscores Harlowe would read as variables."""
    problems = []
    for passage_file in sorted(passages_dir.glob("*.txt")):
        lines = passage_file.read_text(encoding="utf-8").splitlines()
        if not lines or not lines[0].startswith(":: "):
            continue
        tags = PASSAGE_TAG_RE.search(lines[0])
        if tags and RAW_TAGS.intersection(tags.group(1).split()):
            continue
        for lineno, token in _temp_vars("\n".join(lines[1:]), first_line=2):
            problems.append(f"{passage_file.name}:{lineno}: {token}")
    return problems


def img_to_base64(match: re.Match) -> str:
    alt, path, attrs = match.group(1), match.group(2), (match.group(3) or "").strip()
    # Resolve relative to the images dir regardless of the ../ prefix used in links.
    img_path = (IMAGES_DIR / Path(path).name).resolve()
    if not img_path.is_file():
        raise FileNotFoundError(f"Missing image for link: {path}")
    data = img_path.read_bytes()
    mime = EXT_MIME.get(img_path.suffix.lstrip(".").lower(), "application/octet-stream")
    b64 = base64.b64encode(data).decode("ascii")
    extras = f' {attrs}' if attrs else ""
    return f'<img src="data:{mime};base64,{b64}" alt="{alt}"{extras}>'


def pom_version() -> str | None:
    """Read the beanbot project version from pom.xml, or None when unavailable."""
    try:
        match = POM_VERSION_RE.search(POM_PATH.read_text(encoding="utf-8"))
        return match.group(1).strip() if match else None
    except OSError as exc:
        print(f"Warning: could not read {POM_PATH} for the version stamp: {exc}")
        return None


def stitch() -> str:
    problems = find_temp_vars()
    if problems:
        raise SystemExit(
            "Error: Harlowe reads these as temp variables (use &#95; inside "
            "<code>, or backticks for verbatim text):\n"
            + "\n".join(f"  {problem}" for problem in problems)
        )
    version = pom_version()
    parts = []
    for passage_file in sorted(PASSAGES_DIR.glob("*.txt")):
        text = passage_file.read_text(encoding="utf-8")
        if version:
            text = VERSION_STAMP_RE.sub(r"\g<1>" + version, text)
        parts.append(MD_IMG_RE.sub(img_to_base64, text))
    return "\n\n\n\n".join(parts) + "\n"


def find_tweego() -> str | None:
    """Locate the tweego binary: TWEEGO env var, then PATH, then ~/apps/tweego."""
    candidates = []
    env = os.environ.get("TWEEGO")
    if env:
        candidates.append(env)
    on_path = shutil.which("tweego")
    if on_path:
        candidates.append(on_path)
    candidates.append(str(Path.home() / "apps" / "tweego" / "tweego"))
    for candidate in candidates:
        if candidate and os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    return None


def compile_html(twee_path: Path) -> Path | None:
    """Compile .twee to HTML using tweego with Harlowe format."""
    html_path = twee_path.with_suffix(".html")
    tweego_path = find_tweego()
    if not tweego_path:
        raise SystemExit(
            "Error: tweego not found (set TWEEGO, add it to PATH, or install it at "
            "~/apps/tweego/tweego). Install tweego, or skip the user guide with "
            "-DskipUserGuide=true (e.g. ./mvnw package -DskipUserGuide=true)."
        )
    cmd = [
        tweego_path,
        "-f", "harlowe-3",
        "-o", str(html_path),
        str(twee_path)
    ]
    print(f"Compiling {twee_path.name} → {html_path.name}...")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Error: tweego failed with exit code {result.returncode}")
        print(f"stderr: {result.stderr}")
        sys.exit(1)
    # Ship the guide as gzip only: the plain .html sitting in target/ gets
    # flagged by the IDE's HTML validator, and the compressed copy is what the
    # resources block packages. Delete the plain file after compressing.
    gz_path = html_path.with_suffix(".html.gz")
    with html_path.open("rb") as f_in, gzip.open(gz_path, "wb") as f_out:
        shutil.copyfileobj(f_in, f_out)
    plain_size = html_path.stat().st_size
    html_path.unlink()
    print(f"Gzipped {gz_path.name} ({plain_size} → {gz_path.stat().st_size} bytes)")
    return gz_path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT,
                        help="output twee file (default: target/User Guide.twee)")
    parser.add_argument("--no-html", action="store_true",
                        help="skip HTML compilation")
    args = parser.parse_args()
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(stitch(), encoding="utf-8")
    print(f"Wrote {output} ({output.stat().st_size} bytes)")
    if not args.no_html:
        compile_html(output)


if __name__ == "__main__":
    main()
