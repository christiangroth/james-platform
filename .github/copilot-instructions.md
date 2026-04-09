# Copilot Instructions for james-platform

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

**Every PR must have a green CI build before merging.** Follow these rules:

- **Never use `[no ci]`** in commit messages to bypass CI. All commits to a PR branch must be validated by CI.
- **Run `./gradlew build` before pushing** to verify the build, tests, and static analysis all pass.
- **CI must be green** on the PR branch before requesting or completing a merge into `main`.
- If CI fails on a PR, fix the underlying issue — do not skip CI.

## Formatting

All code must follow the formatting rules in `.editorconfig`. The most important rules for Kotlin:

- **2-space indentation** (not 4), no tabs
- **LF line endings**
- **Max line length:** 180 characters
- **Insert final newline** in every file

Always format new and edited files according to `.editorconfig` before committing.

## Documentation

- **Architecture:** [docs/arc42/arc42.md](../docs/arc42/arc42.md)
- **Architect role guidelines:** [docs/coding-guidelines/role-architect.md](../docs/coding-guidelines/role-architect.md)
- **Backend developer role guidelines:** [docs/coding-guidelines/role-backend-developer.md](../docs/coding-guidelines/role-backend-developer.md)
- **Frontend developer role guidelines:** [docs/coding-guidelines/role-frontend-developer.md](../docs/coding-guidelines/role-frontend-developer.md)

## Release Note Snippets

**Snippet filename:** `docs/releasenotes/snippets/{branch-last-segment}-{type}.md` where `{type}` is one of `bugfix` or `feature`.

**Snippet content:** Briefly describe what was changed or added on the branch. Each line should follow the pattern `* Description of the change.` Feel free to use multiple short
lines, describing the change without technical detail. Only include **user-facing or dependency changes** in release notes. Do not add implementation details, refactoring notes, or
internal structural changes (e.g. package renames, build task additions).

**Type selection:** Use `feature` for new user-facing functionality. Use `bugfix` for fixes and chore/internal changes (e.g. refactoring, configuration restructuring, dependency
updates).
