Creates a task in a Beanbot Tasks repository.

Use when the user wants to add, store, track, or create a task or todo item, including:
- Explicit requests: 'add a task', 'create a todo', 'track this', 'log this as a task'
- Reminders: 'remind me about X tomorrow/next week/next Thursday'
- Quick captures: 'note this down', 'put this on my list', 'save for later'

Priority guidelines:
- User says 'critical', 'urgent', 'important', or 'high priority': use 'A'
- User says 'low priority', 'nice to have', or 'whenever': use 'T' (or 'S' for someday/maybe)
- Otherwise, infer importance from context and assign A-Z (default 'N' for normal tasks)

Due dates:
- If user specifies a date ('tomorrow', 'next week', 'Sept 5'), parse and set dueDate
- If no date is mentioned or cannot be inferred, omit dueDate (task has no deadline)

Examples:
- 'Add a task to fix the login bug' -> summary='Fix the login bug', priority='B'
- 'Remind me to run tests next Monday' -> summary='Run tests', dueDate='2026-09-07', priority='B'
- 'Critical: deploy the hotfix today' -> summary='Deploy the hotfix', dueDate='2026-08-27', priority='A'
- 'Add a todo: update API docs for v2 release' -> summary='Update API docs for v2 release', priority='B'
- 'Track this as a task for the backend project, tag it @api' -> summary='[user query]', projects='backend', tags='api'
