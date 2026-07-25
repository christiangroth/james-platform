# CLAUDE.md for james-platform

## Build & Test Commands

```bash
# Run full build (includes tests and static analysis)
./gradlew build

# Run tests only
./gradlew test

# Start application in dev mode (live reload)
./gradlew :application-quarkus:quarkusDev
```

## Green Build Requirement for PRs

**Every PR must have a green CI build before merging into `main`.** `gradle.yml` runs the full build on every push, to any branch — there is no `branches: [main]` filter, so every commit is validated automatically.

- **Run `./gradlew build` locally before pushing** to catch failures before they show up in Actions.
- **CI must be green** on the PR branch before requesting or completing a merge into `main`.
- If CI fails on a PR, fix the underlying issue — do not skip CI.
- **Exception for time-limited agent sessions:** see "Running Gradle in Time-Limited Agent Sessions" below.

## Running Gradle in Time-Limited Agent Sessions

This applies whenever a single command execution is time-limited (e.g. the Claude Code GitHub Action, whose Bash tool has a hard execution ceiling of a few minutes per call). A full multi-module `./gradlew build` (all modules, tests, static analysis) can exceed that ceiling and gets killed at an inconsistent point from run to run — do not rely on it in this context.

- **Never background a Gradle invocation** (no `run_in_background` / async execution). A build that keeps running after its tool call is considered "timed out" produces results that cannot be trusted or awaited.
- **Scope the build to the module(s) you touched** for a fast, reliable local signal, e.g. `./gradlew :module-name:test` or `:module-name:compileKotlin` instead of the whole-repo `build`.
- **Push the branch and open the PR**, then rely on the `gradle.yml` workflow (triggered automatically on every push) as the authoritative full-build gate. Poll with `gh pr checks` (short, non-blocking calls) to confirm it goes green rather than reproducing the whole build locally.

## Task Completion

A task is only complete once:
1. The relevant tests/checks for the module(s) you touched are green (see above), and CI on the pushed branch is green or at least running.
2. Changes are committed and pushed.
3. A pull request is open via `gh pr create` — check first with `gh pr list --head <branch>` whether one already exists for the branch; if so, use `gh pr edit` instead of opening a second one.

Never end a task with "PR still to be opened" as an open item, and never end silently after a partial change. If a step fails (e.g. `git push` or `gh pr create`), retry (e.g. with a different branch name); if it still fails, state the failure and the reason explicitly in a comment. If, after investigation, no code change turns out to be needed, say so explicitly in a comment instead of ending without any response.

## Incremental Commits

Don't wait until the very end and bundle everything into one commit: commit and push after each self-contained step. As soon as the first meaningful commit is pushed, open a draft pull request (`gh pr create --draft`) and keep updating its description as work progresses; only take it out of draft once everything is done. This way, even an interrupted run leaves a visible trace instead of ending without one.

## Formatting

All code must follow the formatting rules in `.editorconfig`. The most important rules for Kotlin:

- **2-space indentation** (not 4), no tabs
- **LF line endings**
- **Max line length:** 180 characters
- **Insert final newline** in every file

Always format new and edited files according to `.editorconfig` before committing.

## Documentation

- **Architecture:** [docs/arc42/arc42.md](docs/arc42/arc42.md)
- **Architecture decision records:** [docs/adr](docs/adr)
- **Architect role guidelines:** [docs/coding-guidelines/role-architect.md](docs/coding-guidelines/role-architect.md)
- **Backend developer role guidelines:** [docs/coding-guidelines/role-backend-developer.md](docs/coding-guidelines/role-backend-developer.md)
- **Frontend developer role guidelines:** [docs/coding-guidelines/role-frontend-developer.md](docs/coding-guidelines/role-frontend-developer.md)
- **Test engineer role guidelines:** [docs/coding-guidelines/role-test-engineer.md](docs/coding-guidelines/role-test-engineer.md)

## Release Note Snippets

**Snippet filename:** `docs/releasenotes/snippets/{branch-last-segment}-{type}.md` where `{type}` is one of `bugfix` or `feature`.

**Snippet content:** Briefly describe what was changed or added on the branch. Each line should follow the pattern `* Description of the change.` Feel free to use multiple short lines, describing the change without technical detail. Only include **user-facing or dependency changes** in release notes. Do not add implementation details, refactoring notes, or internal structural changes (e.g. package renames, build task additions).

**Type selection:** Use `feature` for new user-facing functionality. Use `bugfix` for fixes and chore/internal changes (e.g. refactoring, configuration restructuring, dependency updates).
