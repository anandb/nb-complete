Opens the side-by-side stash diff viewer for a given stash index. Shows a visual comparison of
changes in the stash vs. the current working tree. Only works with Git repositories.

Use when the user wants to:
- Review what's stashed before applying or popping
- Compare stashed changes with current code
- Inspect a specific stash entry
- See what changes were stashed

Trigger phrases:
- 'Show me what's in stash 0'
- 'Diff the stash'
- 'What did I stash?'
- 'Compare stash with current'
- 'Open the stash diff'

Examples:
- 'Show me what's in stash 0' -> diff_stash with stashIndex=0
- 'What's in my latest stash?' -> diff_stash with stashIndex=0
- 'Diff stash 2 in the backend repo' -> diff_stash with stashIndex=2, repoDir='.../backend'
