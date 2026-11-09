Locate files by name across the open projects, using the same index as Navigate > Jump to File: every file in an open project that the project's VCS ignore rules (.gitignore, .hgignore or .bbignore) do not exclude. Use it instead of grepping for a file name or guessing a path.

Matching is case-insensitive. A plain query (3 or more characters) matches a file-name prefix or any substring of the project-relative path, so "chat" finds ChatThreadPanel.java and src/ui/chat/MessageBubble.java. A query containing glob characters (*, ?, [, {) is matched bash-style against the file name or the relative path, with an implicit trailing wildcard, so *Test* finds MyTestFile.java and **/main/** stays inside a directory.

The search is scoped to this session's working directory by default — the files you are meant to touch — and the reply echoes it as "scope": "session" with "directory". Pass "scope": "projects" to search every open project instead, for example when the file you want lives outside your working directory. A session with no working directory searches the projects anyway and reports "scope": "projects".

At most 5 matches are returned: file-name hits first, then ordered by project and relative path, so the same query returns the same list. Each match has "name" (file name), "project" (project name), "relativePath" (path inside that project) and "path" (absolute path, the value read_file and open_pos expect). "truncated": true means more files matched than were returned — narrow the query instead of paging. "indexing": true means the index is still being built, so retry shortly; the matches you got are still real.

Results only cover open projects. A file outside them is not in the index — use run_command with grep for that.
