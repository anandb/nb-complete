#!/usr/bin/env python3
"""Reassemble docs/User Guide.twee from the split passages and images.

Reads docs/passages/NNN_<name>.txt in filename order, converts markdown
image links back to base64 data URIs, and writes docs/User Guide.twee.

Markdown image link syntax produced by split.py:
    ![alt](../images/<name>-N.ext){style="max-width:100%;"}
The optional { ... } block restores extra img attributes (alt is always
taken from the ![...] text). Use `--output PATH` to write elsewhere.
"""

import argparse
import base64
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
PASSAGES_DIR = HERE / "passages"
IMAGES_DIR = HERE / "images"
DEFAULT_OUTPUT = HERE / "User Guide.twee"

MD_IMG_RE = re.compile(
    r'!\[([^\]]*)\]\(([^)]+)\)(?:{([^}]*?)})?', re.IGNORECASE
)
EXT_MIME = {"png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg", "gif": "image/gif"}


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


def stitch() -> str:
    parts = []
    for passage_file in sorted(PASSAGES_DIR.glob("*.txt")):
        text = passage_file.read_text(encoding="utf-8")
        parts.append(MD_IMG_RE.sub(img_to_base64, text))
    return "\n\n\n\n".join(parts) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT,
                        help="output twee file (default: docs/User Guide.twee)")
    args = parser.parse_args()
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(stitch(), encoding="utf-8")
    print(f"Wrote {output} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
