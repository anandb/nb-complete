#!/usr/bin/env python3
"""Verify every passage is reachable from Home without using the Topic Index.

Parses a Harlowe twee file, extracts passages and their links, then runs a
BFS from the start passage (default: Home). The Topic Index passage and the
special passages (StoryTitle/StoryData/StoryScript/StoryStylesheet/header)
are excluded from traversal. Reports passages that can only be reached via
the Index, plus any dangling links.

Usage:
    python3 check_reachable.py [path-to.twee]
"""

import re
import sys
from pathlib import Path

SPECIAL = {"StoryTitle", "StoryData", "StoryScript", "StoryStylesheet", "header"}
INDEX = "Topic Index"
START = "Home"


def parse_passages(text: str):
    """Return list of (passage_name, body) in file order."""
    lines = text.splitlines(keepends=True)
    result, cur, body = [], None, []
    for ln in lines:
        if ln.startswith(":: "):
            if cur is not None:
                result.append((cur, "".join(body)))
            cur, body = ln, []
        else:
            body.append(ln)
    if cur is not None:
        result.append((cur, "".join(body)))
    return [(passage_name(header), text) for header, text in result]


def passage_name(header: str) -> str:
    """Extract the passage name from a ':: Name {meta} [tags]' header."""
    name = header[3:].split("{")[0].split("[")[0].strip()
    return name


def extract_links(body: str):
    """Return set of link targets in a passage body.

    Handles [[Name]], [[Label->Target]] and [[Label|Target]].
    """
    found = set()
    for m in re.finditer(r"\[\[([^\]]+)\]\]", body):
        link = m.group(1).strip()
        if "->" in link:
            target = link.split("->", 1)[1].strip()
        elif "|" in link:
            target = link.split("|", 1)[1].strip()
        else:
            target = link
        found.add(target)
    return found


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "User Guide.twee"
    text = path.read_text(encoding="utf-8")

    passages = parse_passages(text)
    names = {name for name, _ in passages}
    links = {name: extract_links(body) for name, body in passages}

    if START not in names:
        print(f"ERROR: start passage {START!r} not found in {path}")
        return 1

    # BFS from the start passage, excluding the Index and special passages.
    exclude = SPECIAL | {INDEX}
    seen = set()
    stack = [START]
    while stack:
        cur = stack.pop()
        if cur in seen or cur in exclude:
            continue
        seen.add(cur)
        for target in links.get(cur, ()):
            if target in names and target not in seen:
                stack.append(target)

    content = names - exclude
    unreachable = sorted(content - seen)
    print(f"Passages checked: {len(content)}")
    print(f"Reachable from {START} without {INDEX!r}: {len(content) - len(unreachable)}")
    if unreachable:
        print(f"\nUNREACHABLE without the Index ({len(unreachable)}):")
        for name in unreachable:
            print(f"  - {name}")
    else:
        print("All passages reachable without the Index.")

    # Report links that point to nothing.
    dangling = sorted((src, tgt) for src, targets in links.items()
                      for tgt in targets if tgt not in names and tgt not in SPECIAL)
    print()
    if dangling:
        print(f"DANGLING LINKS ({len(dangling)}):")
        for src, tgt in dangling:
            print(f"  {src} -> {tgt}")
    else:
        print("No dangling links.")

    return 1 if unreachable else 0


if __name__ == "__main__":
    sys.exit(main())
