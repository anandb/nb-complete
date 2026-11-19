Opens a multi-file side-by-side diff of every file changed between two revisions, or between a
revision and the working tree, for Git or Mercurial repositories. Each changed file is listed
on the left; selecting one shows its before/after content in the diff viewer.

Use when the user wants to:
- Review everything changed in the last N commits
- Include uncommitted working-tree changes in the review
- Compare any two commits, branches, or tags
- See all files touched by a range before a merge or release

Parameters:
- fromRevision: start revision (hash, branch, tag, or expression such as HEAD~10).
- toRevision: end revision. Omit it to include uncommitted working-tree changes (modified,
  added, and untracked files); when set, the diff is commit-to-commit only.
- lastCommits: alternative to fromRevision. Shows the last N commits: the diff starts at the
  oldest of those N commits (lastCommits=5 starts at HEAD~4) and ends at toRevision or the
  working tree.
- repoDir: optional repository path; defaults to the first open project.

Only one of fromRevision and lastCommits is needed; when both are supplied, lastCommits wins.
Omit toRevision for phrases like 'what changed so far' or 'include my uncommitted changes'.

Trigger phrases:
- 'Show me what changed in the last 5 commits' -> lastCommits=5 (working tree included)
- 'What changed between abc123 and def456?' -> fromRevision='abc123', toRevision='def456'
- 'Diff the release tag against main' -> fromRevision='release-1.2', toRevision='main'
- 'Show all changes in this branch so far' -> fromRevision='main', no toRevision

Examples:
- 'Show me the last 3 commits' -> diff_range with lastCommits=3
- 'What changed since yesterday's merge?' -> diff_range with fromRevision='HEAD~10'
- 'Compare the last 5 commits in the backend repo' -> diff_range with lastCommits=5, repoDir='.../backend'
- 'Show my uncommitted changes against HEAD' -> diff_range with fromRevision='HEAD', no toRevision
