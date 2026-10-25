Updates an existing task in a Beanbot Tasks repository.

Use when the user wants to change, edit, rename, reschedule, or
reopen a task, including:
- 'rename task t-3 to ...', 'change its priority to A'
- 'reschedule the deploy task to Friday', 'clear the due date'
- 'reopen task t-7'

Only the provided fields change; omitted fields keep their values.
Empty tags/projects/dueDate clear those values. status='closed'
records completion, 'open' clears it. Idempotent fields are
validated (status must be open|closed, priority a single A-Z).

Examples:
- 'Rename task t-3' -> taskId='t-3', summary='New title'
- 'Priority A for the login task' -> taskId='t-1', priority='A'
- 'Reopen the deploy task' -> taskId='t-7', status='open'
