# User Guide (docs/)

Part of the agent instructions. Index and always-apply rules: [`AGENTS.md`](../../AGENTS.md).

Read this when: editing the user guide under docs/passages or its build.

`docs/passages/NNN_<name>.txt` and `docs/images/` are the guide sources; never edit generated
`target/User Guide.twee` directly. Passages start with `:: Name {attrs}` and the standard title/index
header. Add new pages at the next number, link them from `051_topic_index.txt` (alphabetically), and
cross-link related pages. Images use `![alt](../images/<name>-N.webp){style="max-width:100%;"}`.

After edits, run `python3 docs/stitch.py` and
`python3 docs/check_reachable.py "target/User Guide.twee"`; all passages must be reachable from
Home through page links and there must be no dangling links. Build output is gzipped at
`target/generated-guide/UserGuide.html.gz`. `UserGuideOpener` opens the extracted temp file off the
EDT; do not move that I/O onto the EDT.
