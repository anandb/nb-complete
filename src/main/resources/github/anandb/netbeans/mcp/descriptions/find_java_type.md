Find Java types (classes, interfaces, enums, records, annotations) by name across the open projects, and resolve a name to its fully qualified name and source file. Use it before reading or editing a type, instead of grepping for its declaration or guessing a file path from a package name.

Java only: it searches the Java class index of the open projects, so types from other languages (PHP, C/C++, Groovy, ...) are not found — use search_project for those.

Matches are deduplicated by fully qualified name and sorted. Each match has "name" (the fully qualified name), "path" (absolute source file, present only for types declared in project sources) and "project" (root of the project that declared it). Pass "path" to read_file or open_pos to read it. "truncated": true means more matches existed than "limit" returned.
