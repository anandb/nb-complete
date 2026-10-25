Searches open tasks in Beanbot Tasks repositories by a substring of their summary.

Returns at most the top 10 matching open tasks, each with its repoId and taskId so the
result can be passed directly to close_task. Closed tasks are excluded.

Use when the user wants to find, look up, or list tasks, especially before closing one:
- 'which tasks mention login?'
- 'find the bug task', 'search for the deploy task'
- 'list all the open tasks'

Examples:
- 'search_task query=login' -> matches tasks whose summary contains 'login'
- 'search_task repoId=backend query=test' -> search only the backend repo
