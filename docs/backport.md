# Template Project Backport

This document collects generic (non-domain-specific) fixes, features, and enhancements made
in this repository ("James Platform", formerly "SpCtl") since it was bootstrapped from a
separate generic template project in commit `0ac4e9a8` ("[no ci] Copied form spctl (#133)").

The goal is to identify improvements to areas that originated from the template — build
process, CI/CD, Grafana/monitoring infrastructure, admin area & user management, in-app
monitoring views, general menu & session handling, general UI templates, and coding
guidelines/agent info — so they can be ported back into the template project for the benefit
of future projects bootstrapped from it.

Scope of the survey: all commits in `0ac4e9a8..HEAD` (295 commits at the time of writing).
Automated "`[Gradle Release Plugin] - new/pre tag commit`" version-bump commits are omitted
throughout as noise. Commits that only touch James-Platform's specific business domain (Apps,
Entities, Reports, Slack integration, Spotify integration, etc.) are intentionally excluded —
only generic, reusable infra/process/pattern changes are listed.

Each entry references the short commit hash and, where visible, the PR number, so the actual
diff can be inspected with `git show <hash>` in this repository.

## 1. Build process (Gradle / CI build tooling)

### Build performance & caching
- **Tune JVM heap size and enable CI cache writes** (`8f20d723`, PR #144): sets
  `org.gradle.jvmargs=-Xmx2g -XX:+UseParallelGC` in `gradle.properties` and switches
  `cache-read-only` to `false` on `gradle/actions/setup-gradle` so CI actually populates the
  shared Gradle build cache instead of only reading it. Also documents that the configuration
  cache is intentionally disabled (incompatible with the release-notes plugin).
- **Branch-scoped cache writes + concurrency guard** (`50f0d8ea`, PR #409): restricts the
  `push` trigger to `branches: [main]`, adds a `concurrency` group with
  `cancel-in-progress: true` to kill superseded runs, and makes `cache-read-only` conditional
  on the branch (`main` writes, feature branches read-only).
- **Parallelize test execution across JVM forks** (`093812b6`, PR #411): adds
  `maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)` to the
  shared `kotlin-project.gradle.kts` convention plugin's `test` task.

### Docker / packaging
- **Disable Quarkus dev services outside the dev profile** (`82c55ce0`, PR #311): changes
  `quarkus.devservices.enabled=true` to `%dev.quarkus.devservices.enabled=true`, fixing a
  common Quarkus footgun where dev services stay active in packaged/production containers.

### Release process / build hygiene
- **Remove accidentally duplicated releasenotes plugin code from `buildSrc`** (`3976cea7`, PR
  #184): the custom `de.chrgroth.gradle.plugins.releasenotes` plugin (`Configuration.kt`,
  `Plugin.kt`, `Processing.kt`) had been copy-pasted into `buildSrc` alongside the canonical
  externally-published plugin. Worth checking whether the template carries the same
  copy-paste artifact.

### Known regression (not a backport candidate, flagged for awareness)
- Commit `61332672` (PR #155, ostensibly a session-cookie fix) also stripped detekt entirely
  (`libs.versions.toml` entries, the `dev.detekt` plugin, `detekt-config.yaml`) and it was
  never reinstated — static analysis tooling is currently missing from this repo. This should
  be treated as a bug to fix here, not a pattern to propagate to the template.

## 2. GitHub workflows / CI automation

### Build CI (`gradle.yml`)
- Covered by `8f20d723` and `50f0d8ea` above (Gradle cache tuning + concurrency guard).
- **Tolerate flaky external SaaS dependency in post-release job** (`f83a5b22`, PR #301): adds
  `continue-on-error: true` to a Grafana Cloud provisioning step so third-party cold-start
  hibernation doesn't block the whole pipeline — a generically useful pattern for any
  non-critical job depending on an external SaaS API.

### Copilot automation
- **Fix Copilot setup-steps workflow location/structure** (`0f38f5a2`, PR #143): the original
  `.github/copilot-setup-steps.yml` was a bare list of `steps:` (not a valid workflow) and had
  to be moved to `.github/workflows/copilot-setup-steps.yml` with a proper `name`/`on`/`job`
  structure, `permissions: contents: read`, checkout, and Java/Gradle setup.

### Claude Code automation
- **Add Claude Code GitHub Action** (`99b47b2d`, PR #346): introduces
  `.github/workflows/claude.yml`, triggered on `@claude` mentions in issues/PR comments/reviews,
  running `anthropics/claude-code-action@v1` with Java/Gradle setup so the agent can build the
  repo. Also creates `CLAUDE.md` from scratch (see section 8).
- **Grant Claude workflow write permissions and allow-list tools** (`9ae01024`): upgrades
  `permissions` to `write` for `contents`/`pull-requests`/`issues`, and adds
  `claude_args: "--allowedTools Edit,MultiEdit,Write,Glob,Grep,LS,Read,Bash(git:*),Bash(./gradlew:*),Bash(gh:*)"`
  plus `GH_TOKEN`/`GHCR_PAT` env vars. When backporting, genericize the build-tool entry
  (e.g. `Bash(<build-tool>:*)`).

## 3. Grafana resources & monitoring infrastructure

- **Remove hardcoded dashboard `id` to fix Grafana provisioning** (`0c42637c`, PR #183):
  dropped the numeric `id` field from `monitoring/grafana/quarkus-logs.json`, keeping only
  `uid` — the generically correct way to author provisioned dashboards (a stale numeric id
  causes silent provisioning failures/overwrites).
- **Fix Alloy crash from invalid River syntax** (`36c21f45`, PR #241): renamed component
  labels in `deploy/alloy/config.alloy` from hyphenated (`"james-platform_containers"`) to
  underscored (`"james_platform_containers"`), since Grafana Alloy's River config language
  doesn't allow hyphens in component labels — a config-language gotcha worth documenting in
  the template's Alloy example.
- **Add Docker healthcheck for Quarkus service in Swarm stack** (`36c21f45`, PR #241, same
  commit): adds a `wget .../q/health/ready` healthcheck to `deploy/docker-stack.yml` so Swarm
  waits for readiness before routing traffic — a generic readiness-probe pattern (later
  removed again in this repo's own deployment context via `50260ad8`, but worth keeping as a
  template default).
- **Make Grafana Cloud provisioning non-blocking in CI** — see `f83a5b22` in section 2.
- See also section 4 for the new in-app Logs UI, and section 5 for monitoring-page
  role-gating/secret-masking hardening (`1d5ab9ef`, `ec115ea9`).

## 4. In-app monitoring links & views

The "Technical/Tools" nav dropdown (links to `/health`, `/config`, `/mongodb-viewer`, Grafana
logs/metrics) already existed before the template copy, so it isn't itself a backport
candidate. Genuinely new additions since the copy:

- **In-app Logs UI** (`2804e11f`, PR #343): adds a `/logs` page (`LogsResource.kt`,
  `UiLogBuffer.kt`, `logs.html`) backed by a bounded, in-memory ring buffer (max 200 entries,
  1h retention) capturing WARN/ERROR log records, rendered as a filterable table with
  expandable stacktraces. Fully generic, no domain coupling — a strong backport candidate as a
  lightweight complement to a Grafana/Loki stack for local/offline debugging.
- **Dedicated `MONITORING` role for ops-only Tools menu access** (`43c4d4b3`, PR #341):
  introduces a `UserRole.MONITORING` value and an `isMonitoring` template global so a
  low-privilege monitoring user can see the Technical dropdown without full admin rights. The
  role/gating mechanism is generic (the seeded username is app-specific and should be
  genericized).
- **Health page "additional metrics section" pattern** (`b58fb6b0`, PR #336): demonstrates
  bolting a new stats table/fragment onto `health.html` (`HealthResource.kt`) — the specific
  metric is domain-specific, but the pattern of extending the health page with new fragments
  is reusable.
- **Split "monitoring" i18n bundle out of "admin" bundle** (`1d5ab9ef` and follow-ups
  `4b12eb0a`/`ec115ea9`): extracts health/logs/mongodb-viewer/config translations into a
  dedicated `MonitoringMessages` bundle, separate from user-management admin strings — a
  reusable i18n-namespacing convention for ops/monitoring views.
- **Fix secret-masking gaps and a type-cast bug in health/monitoring pages** (`ec115ea9`, PR
  #436): extends `app.health.masked-config-keys` to also mask TLS keystore/credentials-provider
  keys, and fixes an `Int`→`Long` `ClassCastException` in the MongoDB document-count viewer.
  The underlying lesson (secret-masking allowlist must be kept in sync with new config keys)
  is generic.

## 5. Admin area & user management

- **Wipe Spotify domain, introduce local user management** (`743fb60c`, PR #135): the
  foundational commit — replaces Spotify-OAuth login with a local `User`/`LoginService`/
  `AdminUserInitializerService` model and a `CookieAuthMechanism`. The generic local-auth
  scaffolding this introduced is the single most important artifact to backport (independent
  of the Spotify-removal parts, which are domain-specific).
- **User administration: dashboard overhaul, user management page, admin nav, data
  migration** (`feff6034`, PR #160): introduces `AdminUserManagementResource`/
  `AdminUserManagementService`, the `ui/admin/users.html` page, an admin-only nav/dashboard
  tile, and `UserCreatedAtActiveMigrationStarter` — the core generic admin feature.
- **Profile UI — change username/password, track `createdAt`/`lastLoginAt`** (`89c04ed8`, PR
  #153): lets any logged-in user manage their own credentials via `ProfileResource`/
  `UserProfileService`, plus audit fields on the user document.
- **Fix Kotlin value-class JVM name mangling crash in Qute templates** (`1e6b9da2`, PR #166):
  a generic Kotlin/Qute interop gotcha when rendering value-class fields (e.g. `UserId`).
- **Role management, password confirmation, HTTP 405 fix** (`4f8ba49d`, PR #170).
- **Refactor user management to AJAX actions + inline messages + Qute fragment refresh**
  (`f11ff820`, PR #176): a solid generic pattern for admin CRUD tables without full page
  reloads.
- **Fix admin page JS-before-DOM ordering bug** (`c60359f7`, PR #193) — see also section 6/7
  (same root cause as the `layout.html` script-ordering issue).
- **Enforce single-admin constraint on role assignment** (`e743aa2e`, PR #195): generic
  business rule ensuring exactly one user can hold the `ADMIN` role at a time.
- **Login redirect by role** (`17120208`, PR #197): routes users to different dashboards
  post-login based on role — a reusable role-based routing pattern.
- **Real UUID user IDs + migration starter** (`362f116b`, PR #224): `UserIdMigrationStarter`
  demonstrates a reusable "migration starter" pattern for safely evolving the user schema on
  startup.
- **Add `MONITORING` role with Tools menu access** (`43c4d4b3`, PR #341) — see section 4.
- **Secure admin/monitoring pages by role** (`1d5ab9ef`, PR #429): adds a
  `@BlockAdminAccess` JAX-RS filter (`BlockAdminAccessFilter`) that denies access regardless of
  what *other* roles a principal holds, and enforces `@RolesAllowed("ADMIN")` on
  `/ui/admin/dashboard` — a reusable authorization-filter pattern for Quarkus apps with
  multi-role principals.
- **Align admin area UI with platform standards** (`3384fc02`, PR #361) and later polish
  (`1ffb86b3` icon change PR #362, `537441de` breadcrumb fix PR #435).

**Suggested backport priority:** `743fb60c` (foundation) → `feff6034` (admin dashboard + user
mgmt core) → `89c04ed8` (profile self-service) → `e743aa2e` / `43c4d4b3` / `1d5ab9ef` (RBAC
pattern) → `f11ff820` (AJAX admin UX pattern) → remaining polish/bugfix commits as a
lower-priority cleanup batch.

## 6. General menu & session handling

### Navigation menu / layout
- **Breadcrumb restructuring** (`c3564433`, PR #317) and **responsive breadcrumb truncation**
  (`81a939bf` PR #383, `ef20445d` PR #399, `298e3f43` PR #398): introduces
  `breadcrumb-utils.js`, a generic client-side algorithm that collapses breadcrumb trails based
  on actual line-wrapping rather than fixed viewport breakpoints.
- **Simplify admin breadcrumbs: home icon stays within current section** (`537441de`, PR
  #435): general "don't let the breadcrumb home icon escape the current role's section" fix.
- **Reorder navbar items, full-viewport background** (`75adabbf`, PR #425): generic navbar
  convention — left-align identity/preference controls, right-align action controls.
- **Remove app title text from navbar** (`1f9d7e99`, PR #192): generic navbar decluttering.
- **Fix `layout.html` script-insertion order** (`c60359f7`, PR #193): `{#insert scripts}` was
  placed before `{#insert content}` in the Qute base layout, so page JS ran before the DOM
  content existed — a generic Qute/template-layout footgun.

### Login/logout flow
- **Fix white page after login: HTTP 303 instead of 307** (`2239c264`, PR #158): `307`
  preserves the POST method/body on redirect, causing a blank page or re-submit prompt;
  switched to `303` (POST-redirect-GET). Generic and directly backportable.
- **Login redirect by role** (`17120208`, PR #197) — see section 5.
- **i18n foundation, login page as reference migration** (`9aaf38dd`, PR #405): establishes a
  Qute message-bundle pattern using the login page as the reference implementation — reusable
  i18n scaffolding regardless of which locales the template ships.
- **Login hero panel responsive fix** (`47c64c5d`, PR #426): general responsive-design fix.

### Session / cookie handling
- **Fix session cookie expiring on browser close; add sliding renewal** (`61332672`, PR #155):
  rewrote `CookieAuthMechanism` to encode `username|issuedAt` in the encrypted cookie, set an
  explicit `maxAge` (14 days), and auto-refresh when less than 5 days remain — a solid, generic
  "remember me with sliding expiration" session pattern. (Note: this same commit accidentally
  removed detekt — see section 1's regression note — so cherry-pick carefully.)
- **Local cookie-based auth mechanism** (`743fb60c`, PR #135) — see section 5; the generic
  `CookieAuthMechanism`/`LoginService`/`UserRole` skeleton is worth mining independent of the
  Spotify-removal parts.

### Security / role-based access control
- **`@BlockAdminAccess` filter + `@RolesAllowed` on dashboards** (`1d5ab9ef`, PR #429) — see
  section 5.
- **Role-gated nav-menu section** (`43c4d4b3`, PR #341) — see section 4/5.
- **Disable a UI toggle when it has no effect, role-gate advanced options** (`0c967751`, PR
  #444): generic UI pattern — grey out a navbar control (e.g. locale switcher) when only one
  option remains.

## 7. General UI templates & theming

### Base layout & theming
- **Basic theming: blue/red button classes, dark form controls, badge helpers** (`6f57b07d`,
  PR #157): the foundational shared CSS vocabulary later UI work builds on.
- **Light theme + OS-aware switch** (`563af6d0`, PR #403): full light theme plus
  `theme-utils.js` for OS-preference detection with manual override.
- **Dark theme border/contrast fixes** (`ec7beca5` PR #401, `dc22213b` PR #389,
  `ecb19c5b` PR #175 striped-table text, `6324b3f2` PR #164 `.app-card` contrast,
  `6fe6b51a` PR #449 form hint text).
- **Gradient background as page default** (`e3d63899`, PR #408): promotes the login page's
  gradient background to the default for all pages.
- **Navbar reorder + full-viewport background** (`75adabbf`, PR #425) — see section 6.

### Favicon / branding
- **Add app icon (favicon, nav, login)** (`0a09d37a`, PR #150): establishes the
  favicon/branding wiring *mechanism* (SVG favicon reused in navbar and login) — generic even
  though the specific icon art is not.
- **PNG favicon fallbacks for mobile** (`bdd37d3f`, PR #433): adds PNG/apple-touch-icon
  fallbacks since Safari/iOS doesn't support SVG favicons.
- **Fix logo invisible in light mode** (`a0b3298b`, PR #406): general "branding must work in
  both themes" fix, relevant once a template supports theme switching.
- **App name via i18n + head metadata** (`f51ad4b3`, PR #430): adds `application-name`/
  `description` meta tags to the base `<head>`, routing the app name through a single
  overridable i18n key instead of hardcoding it.

### Error pages
- **Styled HTML error page for unhandled exceptions** (`ccc43441`, PR #162): adds a
  `GlobalExceptionMapper` + generic `error.html` template rendering unhandled server errors as
  a styled page instead of a raw stack trace — directly reusable "500-style" error page infra.

### Shared UI components
- **Unified icon buttons** (`234fc434`, PR #263): reusable Qute tag templates
  (`tags/btn-icon-add.html`, `-delete.html`, `-edit.html`, `-publish.html`, later `-key.html`)
  for consistent icon buttons.
- **Generic breadcrumb-home tag** (`1b4cfedb`, PR #245): reusable `tags/breadcrumb-home.html`
  fragment.
- **Responsive tables & standard CSS components** (`4f5ed8cc`, PR #252): generic
  responsive-table CSS and shared component styling.
- **Cache-busting for custom JS assets** (`a869b57a`, PR #386): a version query-param helper
  (`AppTemplateGlobals.kt`) wired into `layout.html` for static-asset cache-busting — generic
  infra for any Quarkus/Qute app.

## 8. Coding guidelines & agent information

### CLAUDE.md / agent instructions
- **Add Claude Code GitHub Action and `CLAUDE.md`** (`99b47b2d`, PR #346): creates
  `CLAUDE.md` from scratch with build/test commands, the "green build required for PRs"
  policy, `.editorconfig` formatting rules, doc links, and the release-note-snippet
  convention. The foundational agent-instructions file, worth backporting close to verbatim.
- **Grant Claude workflow permissions + tool allow-list** (`9ae01024`) — see section 2.
- **Add missing test-engineer/ADR links to CLAUDE.md** (`1c5206d2`, PR #348): a doc-index
  completeness fix, generically applicable to any CLAUDE.md.
- **Clarify the green-build policy is enforced by Claude, not automatic CI** (`6884146b`, PR
  #410): rewrites the section to rely on local `./gradlew build` verification before every
  commit/push, since branch-scoped CI triggers may be disabled to save minutes — a valuable
  nuance for any template where CI isn't guaranteed on every push.
- **Fix Copilot setup-steps workflow location** (`0f38f5a2`, PR #143) — see section 2.

### Coding guideline docs (`docs/coding-guidelines/role-frontend-developer.md`)
- **Table/modal/Qute-tag/shared-JS-utility patterns** (`a7f077d0`, PR #339): the largest single
  addition — canonical HTML/Qute patterns for data tables, edit/confirm modals, a catalog of
  standard Qute tags, and shared JS helpers (`postWithButton`, `showBanner`, `connectSse`,
  `fadeUpdate`). Extremely generic for any Quarkus+Qute+Bootstrap SSR frontend.
- **"Form submissions and action results" convention** (`883c5754`, PR #208): AJAX+JSON
  `ApiResult(ok, message, redirectUrl)` pattern replacing query-param redirects.
- **CSS component catalog + breadcrumb rules + "no inline `style=`" rule** (`4f5ed8cc`, PR
  #252).
- **Consistent button placement/order/alignment rules** (`d1f7103a` PR #316, `9cc27946` PR
  #385): Cancel→Destructive→Constructive ordering with copy-paste HTML patterns.
- **Dark-mode contrast + "modals vs. navigation" decision guideline** (`dc22213b`, PR #389):
  prefer breadcrumb-navigated pages over modals for arbitrarily-nestable content; mobile-first
  icon-only responsive-button rule.
- **Semantic button-color convention** (`6f57b07d`, PR #157) — see section 7.
- **i18n architecture + build-generated pseudo-locale for QA** (`9aaf38dd` PR #405,
  `d4724dd0` PR #416): documents "no literal user-facing text in templates," per-area
  message-bundle splitting, and a build-generated pseudo-locale (`xx`, each letter replaced
  with `_`) to catch un-internationalized strings during UI testing — a strong, generically
  reusable QA technique.
- **"Sync docs with actual codebase" practice** (`5c9404d5`, PR #145): establishes the
  recurring practice of a dedicated PR that re-aligns `arc42.md`/coding guidelines/build docs
  with the current codebase — the practice itself, not the James-specific content, is worth
  adopting in the template's process.

### ADRs & release-note process
- No generic ADR content changed in range — `docs/adr/0000`–`0006` were copied in unchanged at
  bootstrap and never modified. The only ADR change (`0affc8fc`, PR #215) deleted the
  James-Platform-specific `0007-persistent-outbox-pattern.md`, which is correctly excluded.
- No changes to `docs/releasenotes/templates/*` in range; the refined release-note-snippet
  *convention* lives in `CLAUDE.md` (see `99b47b2d` above) rather than in the template files.
