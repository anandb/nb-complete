Closes (marks as done) a task in a Beanbot Tasks repository.

Use when the user wants to complete, finish, close, or mark a task as done, including:
- 'close that task', 'mark it as done', 'task is complete'
- 'finish the login-bug task', 'that todo is done'

Only sets status to 'closed'; other task fields (summary, priority, tags, due date)
are left untouched. The completion timestamp is recorded automatically.

Examples:
- 'Close task t-3' -> taskId='t-3'
- 'The deploy task is done, close it' -> taskId=<id from add_task or list>
