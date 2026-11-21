# Contributing to BeanBot

Welcome — including if you're an AI. BeanBot is built with AI assistance and we
expect that to continue. Contributions generated, drafted, or reviewed with AI
tools are welcome on equal footing with hand-written ones. What we require is not
disclosure of the tool but verification of the output: tests, a green CI run,
and a human who has read the diff and takes responsibility for it.

BeanBot is built with AI assistance and reviewed by humans. Every change that lands
on `main` goes through a pull request with a review, a green CI run, and a
conventional-commit subject. That review gate — not the authorship — is what makes
the output trustworthy.

## Before you start

- Read [AGENTS.md](AGENTS.md) for the architectural layer rules and coding conventions.
- Read [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for the build, test, and user-guide workflow.

## Process (solo maintainer)

There is no second reviewer. The review gate is therefore a *self-review* discipline,
not a team ritual — but it must still leave evidence in the record, or it does not
counter the "unverified output" charge.

1. Open an issue or pick an existing one. Describe the problem, not the solution.
2. Create a branch. Make small, focused commits — one concern per commit.
3. Run the full suite locally before pushing:
   ```
   ./mvnw verify -P random-test-order
   ```
4. Open a pull request against `main`. The PR description must state: what changed,
   why, how it was tested, and the coverage delta.
5. **Self-review pass**: re-read the full diff on GitHub (not in your editor) after
   CI is green. Check it as if someone else wrote it. Leave at least one review
   comment on your own PR, even if it is "looks good, merging."
6. **Time delay**: for non-trivial changes, leave the PR open overnight and re-read
   it the next day before merging. Fresh eyes catch what tired eyes miss.
7. Merge with `--no-ff` so the review gate is visible in history.
8. For releases and large features, get one external review (paid, credited, or a
   trusted community member) and publish their notes, including disagreements.

### Automated gates (no human required)

- CI must be green (`./mvnw verify`).
- Commit subjects must pass the `commit-msg` hook.
- Coverage on core modules (`contract/`, `support/`, `manager/`) must not decrease
  on a PR (ratchet).

## Commit messages

Conventional commits are enforced by the `commit-msg` hook in `.githooks/`.
Activate it once:

```
git config core.hooksPath .githooks
```

Format: `type(scope): description`, subject ≤ 72 characters.
Allowed types: `feat fix docs refactor perf test chore style build ci revert`.

## AI-assisted contributions

AI-assisted contributions are welcome. The requirement is not disclosure of the
tool — it is verification of the output:

- The contributor (human) is responsible for correctness.
- Every PR must include tests covering the changed behavior, or an explicit
  justification for why tests are not added.
- Architecture rules in `AGENTS.md` (hexagonal layers, no upward dependencies)
  are enforced by `LayerReferencesTest` and must not be violated.
- Reviewers evaluate the diff, not the authorship.

## Code standards

- Lines ≤ 120 characters.
- No I/O on the EDT; wrap background work in `SwingUtilities.invokeLater()` or
  `CompletableFuture`.
- Use `ThemeManager.getCurrentTheme()` for colors; dark-mode icons use `_dark.svg`.
- Logging uses index-based placeholders (`{0}`); exceptions are the last argument.
- Structural edits use parser-aware tooling, not sed/awk one-liners.

## Tests

- Headless only; mock UI instead of constructing windows/dialogs.
- `*Test` runs under Surefire; `*IT` runs under Failsafe during `verify`.
- Coverage is tracked by JaCoCo. Target is 96% on core modules (`contract/`, `support/`,
  `manager/`); `ui/` is excluded (Swing code is hard to unit-test headlessly). Current: core
  ~82%, excluding `ui/` ~81%, overall ~58%.
