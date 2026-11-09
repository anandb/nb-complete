Find a file path by name. Answers from the IDE's file index — no disk scan — with up to 5 matches, each carrying the absolute "path" that read_file and open_pos expect.

Use it the moment you need a path you do not already have, before list_directory, run_command (ls, find, glob) or search_project. A plain query matches a file-name prefix or any substring of the project-relative path; a query with glob characters (*, ?, [, {) is matched bash-style against the file name or the relative path, so *Test finds MyTestFile.java and **/main/** stays inside a directory.

It searches this session's working directory by default and says so with "scope": "session" and "directory". Pass "scope": "projects" to search every open project instead; a session with no working directory already searches them all, and if nothing inside the working directory matches the search widens to all open projects and reports "widened": true. The index respects the project's VCS ignore rules (.gitignore, .hgignore, .bbignore), so ignored files and build output are never returned.

File-name hits are ranked first. "truncated": true means more files matched than the 5 returned — narrow the query. "indexing": true means the index is still building, so retry shortly.
