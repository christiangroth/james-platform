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

**Every PR must be in a known-green state before merging into `main`.** Automatic CI (`.github/workflows/gradle.yml`) may not run on every push to a PR branch (e.g. branch-scoped triggers can be disabled to save CI minutes), so this requirement is primarily enforced by Claude itself — not by waiting for a CI badge:

- **Run `./gradlew build` locally before every commit and push.** This runs tests and static analysis and must pass; treat a local failure exactly like a red CI check.
- **Never skip this local verification** to save time. There is no local equivalent of `[no ci]` — every commit must be built and tested first.
- If the local build fails, fix the underlying issue before committing — do not work around it, disable checks, or push additional "fix" commits on top of a known-red state.
- Automatic CI on `main` remains the final safety net after merge. If it ever goes red, treat it as a priority bug and fix it immediately.
- If a human reviewer wants an independent CI run on a PR branch before merging, it can still be triggered manually via `workflow_dispatch` in the Actions tab.

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
