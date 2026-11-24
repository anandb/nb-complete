# Contributing to BeanBot

## Commit messages

Conventional commits are enforced by the `commit-msg` hook in `.githooks/`. Activate it once per
clone:

```
git config core.hooksPath .githooks
```

Format: `type(scope): description`, subject ≤ 72 characters.
A `!` before the colon marks a breaking change: `feat(acp)!: …`.
`Merge …` and `Revert …` subjects are accepted verbatim.

### Types (enforced)

The hook accepts exactly these:

| Type | Use |
|---|---|
| `feat` | new user-visible behavior |
| `fix` | bug fix |
| `docs` | documentation only (README, ADRs, changelog, user guide) |
| `refactor` | no behavior change |
| `perf` | performance |
| `test` | tests only |
| `chore` | tooling, dependencies, housekeeping |
| `style` | formatting, imports |
| `build` | Maven/packaging |
| `ci` | workflows |
| `revert` | revert a commit |

### Scopes (not enforced — pick the subsystem)

The hook does not validate the scope against a list; it only requires it to be non-empty when
present, and it is optional (`fix: …` is valid). Use the layer, package, or feature name. The
established vocabulary:

- **Layers/packages:** `ui`, `manager`, `mcp`, `contract`, `support`, `model`, `db`, `tasks`,
  `project`, `session`
- **UI areas:** `chat`, `options`, `stats`, `permission`, `config`, `editor`, `composer`, `export`,
  `preamble`, `welcome`, `prefs`
- **Subsystems:** `harness`, `acp`, `steering`, `store`, `diff`, `vcs`, `attachments`, `usage`,
  `wsl`, `wirelog`, `onboarding`, `security`
- **Meta:** `docs`, `guide`, `release`, `deps`, `agents`, `ci`

A new subsystem may add a new scope without changing the hook.
