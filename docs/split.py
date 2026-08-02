#!/usr/bin/env python3
"""Split docs/User Guide.twee into one file per passage and binary images.

Output layout:
    docs/passages/NNN_<name>.txt       - one passage per file (header + body)
    docs/images/<name>-N.png        - binary images extracted from base64 src

Inside passage files, embedded images are replaced with markdown links:
    ![alt](../images/<name>-N.png){attr="value" ...}
The optional { ... } block preserves extra img attributes (e.g. style).
Run docs/stitch.py to reassemble the original twee file.
"""

import base64
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
TWEED_FILE = HERE / "User Guide.twee"
PASSAGES_DIR = HERE / "passages"
IMAGES_DIR = HERE / "images"

IMG_TAG_RE = re.compile(r'<img\s+([^>]*?)\s*/?>', re.IGNORECASE)
ATTR_RE = re.compile(r'(\w+)\s*=\s*"([^"]*)"')
SRC_B64_RE = re.compile(r'^data:image/(png|jpe?g|gif);base64,([A-Za-z0-9+/=]+)$')


def sanitize(name: str) -> str:
    """Filesystem-safe slug for a passage name: lowercase, underscores for spaces."""
    name = name.replace('"', '')
    name = re.sub(r'[/\\:*?<>|]', '-', name)
    name = re.sub(r'\s+', '_', name).strip('_')
    return name.lower() or "unnamed"


def split_passages(text: str):
    """Yield (header_line, body) for each passage, preserving order."""
    lines = text.splitlines(keepends=True)
    current_header = None
    current_body = []
    for line in lines:
        if line.startswith(":: "):
            if current_header is not None:
                yield current_header, "".join(current_body)
            current_header = line
            current_body = []
        else:
            if current_header is None:
                # Content before any passage header: attach to a synthetic header.
                current_header = ":: <preamble>\n"
            current_body.append(line)
    if current_header is not None:
        yield current_header, "".join(current_body)


def extract_images(passage_name: str, body: str):
    """Replace base64 <img> tags with markdown links; write binaries.

    Returns (new_body, image_count).
    """

    def repl(match):
        nonlocal count
        inner = match.group(1)
        attrs = dict(ATTR_RE.findall(inner))
        src = attrs.pop("src", "")
        m = SRC_B64_RE.match(src)
        if not m:
            return match.group(0)
        ext, b64 = m.group(1), m.group(2)
        if ext == "jpeg":
            ext = "jpg"
        count += 1
        fname = f"{passage_name}-{count}.{ext}"
        (IMAGES_DIR / fname).write_bytes(base64.b64decode(b64))
        alt = attrs.pop("alt", "")
        attrs_str = "".join(f' {k}="{v}"' for k, v in sorted(attrs.items()))
        return f'![{alt}](../images/{fname}){{{attrs_str.strip()}}}'

    count = 0
    return IMG_TAG_RE.sub(repl, body), count


def main() -> None:
    PASSAGES_DIR.mkdir(exist_ok=True)
    IMAGES_DIR.mkdir(exist_ok=True)
    text = TWEED_FILE.read_text(encoding="utf-8")
    passages = list(split_passages(text))
    for idx, (header, body) in enumerate(passages, start=1):
        name = sanitize(header[3:].split("{")[0].split("[")[0].strip())
        new_body, img_count = extract_images(name, body)
        out = PASSAGES_DIR / f"{idx:03d}_{name}.txt"
        out.write_text(header + "\n" + new_body, encoding="utf-8")
        print(f"{out.name}: {img_count} image(s)")
    print(f"Split {len(passages)} passages into {PASSAGES_DIR}/")


if __name__ == "__main__":
    main()
