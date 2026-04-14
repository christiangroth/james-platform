# james-platform

# Introduction and Goals

## Requirements Overview

James Platform is a personal Low Code system for building and running data-centric apps without writing boilerplate infrastructure code.

### Roles

| Role      | Description                                                                                         |
|-----------|-----------------------------------------------------------------------------------------------------|
| Admin     | Platform administrator. Manages users. Cannot be a User or Developer at the same time.             |
| Developer | Creates and maintains Apps. Defines entities, properties, and reports.                              |
| User      | Installs and uses App Versions. Enters, edits, deletes, and views data through the generic UI.     |

### User Management

- Self-registration is not supported; only an Admin can register new accounts.
- An Admin can: register users, delete accounts, block/unblock accounts, reset passwords.
- Every account has a unique username, a bcrypt password hash, and one or more roles.

### Apps and Versions

- A Developer creates an **App** and publishes it as a series of **Versions**.
- Each Version carries a **semver** number derived automatically from entity changes:
  - *Breaking change* (removed/renamed entity or property, changed immutable ID) → mandatory **Major** release.
  - *Non-breaking change* → Developer chooses between **Feature** or **Bugfix** release.
  - The version number is never entered manually.
- A released Version records a release date and release notes.

### Entities and Properties

- A Version defines **Entities** and **Reports**.
- An Entity has:
  - A name unique within the App.
  - A globally unique internal ID (immutable).
  - An ordered list of **Properties**.
- A Property has:
  - A name unique within the Entity (mutable).
  - An ID unique within the Entity (immutable).
  - A data type and associated constraints.
- **Computed properties** – a Developer may define derived properties by providing a piece of code that computes the value based on the entity and its other properties. Computed properties may depend on each other; the definition order determines the evaluation sequence. The execution environment (backend Kotlin Script vs. browser JavaScript) will be decided later – only one option will be implemented. Sandboxing and error-handling rules are to be defined.

### Supported Data Types

| Type       | Description                                                                                           |
|------------|-------------------------------------------------------------------------------------------------------|
| `long`     | 64-bit integer                                                                                        |
| `Double`   | 64-bit floating-point                                                                                 |
| `boolean`  | True/false                                                                                            |
| `String`   | Text                                                                                                  |
| `date`     | Calendar date                                                                                         |
| `time`     | Time of day                                                                                           |
| `datetime` | Combined date and time                                                                                |
| `ref`      | Reference to an object of the same or another Entity within the same App Version                      |
| `List`     | Ordered list of any type except `List`                                                                |
| `object`   | Inline nested object with its own property list (analogous to an anonymous Entity without a global ID) |

Cyclic reference graphs via `ref` are detected and rejected at schema-definition time.

### Constraints

| Constraint   | Applies to    | Description                                                     |
|--------------|---------------|-----------------------------------------------------------------|
| `NOT NULL`   | all types     | Value must be present                                           |
| `UNIQUE KEY` | all types     | All values across all objects of this Entity must be distinct   |

Additional type-specific constraints (e.g. min/max for numbers, regex for strings) are defined in future versions.

### Generic User Interface

- **List view** – shows all objects of an Entity; supports deletion, sorting by any column, and user-defined sort parameters. A Developer may configure default sort parameters; the User may override them at runtime.
- **Create / Edit form** – generated automatically from the Entity definition. Future versions will add multi-create workflows and creation templates.

### Data Sharing

A User can invite another User to share the data of an installed App Version.
The shared installation is treated as a separate installation. Supported sharing modes:

| Mode                | Description                                                                          |
|---------------------|--------------------------------------------------------------------------------------|
| Full sharing        | All participants can read, write, and delete all objects.                            |
| Read-all / Edit-own | All participants can see all objects; each can only modify their own.                |

### Reports

- A Report belongs to one App and has a unique name within that App.
- A Report contains at least one **Page**; each Page provides HTML markup and JavaScript logic.
- A Report may declare which entities to load and may define per-Entity filter expressions.
- A set of built-in helper functions (charts, aggregation, date handling, …) is available to every Report; this code is maintained as part of the platform and is not user-supplied.
- A Report may only access data from its own App installation (sandbox boundary).
- The platform must prevent Developers from embedding malicious code in Reports (concept to be finalised).

## Quality Goals

| Priority | Quality Goal     | Motivation                                                                                    |
|----------|------------------|-----------------------------------------------------------------------------------------------|
| 1        | Correctness      | Entity schema constraints and cyclic-reference detection must be enforced without exception.  |
| 2        | Security         | Role-based access control, cookie security, and Report sandboxing protect user data.          |
| 3        | Developer UX     | App and schema creation must feel lightweight; no boilerplate for common CRUD patterns.       |
| 4        | Reliability      | External operations (e.g. notifications) are reliably delivered via the outbox pattern.       |
| 5        | Maintainability  | Hexagonal architecture and clear module boundaries keep the codebase understandable.          |

## Stakeholders

| Role/Name        | Contact   | Expectations                                                             |
|------------------|-----------|--------------------------------------------------------------------------|
| Developer / User | (private) | Allow-listed user(s); operates and uses the application for personal use |

# Architecture Constraints

| Constraint                          | Rationale                                                                                                 |
|-------------------------------------|-----------------------------------------------------------------------------------------------------------|
| Single developer / hobby project    | Low operational overhead is paramount; no team conventions, no enterprise tooling.                       |
| No self-registration                | The platform is invite-only; all accounts are created by an Admin.                                       |
| No separate frontend deployment     | Qute SSR keeps the stack simple; no npm/Node.js build step.                                              |
| VPS + Docker Swarm deployment       | The platform runs on an existing personal VPS; no Kubernetes or cloud-managed container orchestration.   |
| MongoDB Atlas as data store         | Flexible document model suits dynamic entity schemas; cloud-managed removes operational burden.           |
| Reports must be sandboxed           | Developers must not be able to inject code that accesses data outside their own App installation.         |
| Version numbers are never manual    | Semver is derived automatically from schema changes to guarantee semantic accuracy.                      |
| No cyclic entity references         | Cycle detection is enforced at schema-definition time to prevent infinite loops during data traversal.    |

# Context and Scope

## Business Context

James Platform is a personal Low Code system. Its primary purpose is to let a single Developer define data models (Entities) and user-facing views (Reports), then let Users install and operate those App Versions to manage their own data – all without writing infrastructure code.

```
┌──────────────────────────────────────────────────────────────────┐
│                        James Platform                            │
│                                                                  │
│  Admin ──► User Management                                       │
│  Developer ──► App/Version/Entity/Report definition              │
│  User ──► App Version installation, data management, sharing     │
└──────────────────────────────────────────────────────────────────┘
```

**External actors:**

| Actor     | Interaction                                                              |
|-----------|--------------------------------------------------------------------------|
| Admin     | Registers, blocks, resets passwords for, and deletes user accounts       |
| Developer | Creates Apps, Versions, Entities (with properties), and Reports          |
| User      | Installs App Versions, manages objects via generic UI, shares data        |

## Technical Context

| Component          | Technology                     | Notes                                                         |
|--------------------|--------------------------------|---------------------------------------------------------------|
| Backend            | Quarkus (Kotlin, JVM / native) | Hexagonal architecture; all business logic in `domain-impl`   |
| Templating         | Qute (Quarkus SSR)             | Server-side rendering; no separate frontend project           |
| Database           | MongoDB Atlas                  | Document model for dynamic entity schemas                     |
| Authentication     | Cookie-based (AES session)     | Bcrypt password hashing; role-enforced via `QuarkusIdentity`  |
| Outbox             | `quarkus-outbox` library       | Reliable external API calls (e.g. Slack notifications)        |
| Reverse proxy      | Traefik                        | TLS termination, HTTPS, on existing VPS                       |
| CI/CD              | GitHub Actions                 | Build, test, native Docker image, deploy to Docker Swarm      |

# Solution Strategy

| Goal              | Design decision                                                                                                    |
|-------------------|--------------------------------------------------------------------------------------------------------------------|
| Correctness       | Constraint validation and cyclic-reference detection in the domain layer; enforced before any persistence write.   |
| Security          | Role-based access via `QuarkusSecurityIdentity`; Report sandbox (concept TBD); `HttpOnly` AES session cookie.      |
| Developer UX      | Generic CRUD UI generated from Entity metadata; semver auto-derived; no boilerplate for common patterns.           |
| Reliability       | All external operations (notifications, …) routed through the persistent outbox.                                   |
| Maintainability   | Hexagonal architecture with strict module-dependency rules; zero infrastructure in `domain-api` / `domain-impl`.   |
| Flexible schemas  | MongoDB document model maps naturally to dynamic Entity/Property definitions.                                      |
| Simple deployment | Quarkus native Docker image + Docker Swarm on existing VPS; MongoDB Atlas as managed database.                     |

# Building Block View

## Whitebox Overall System

*work in progress*

### Module Overview

Base package: `de.chrgroth.james.platform`

| Module                | Direction  | Responsibility                                                                        |
|-----------------------|------------|---------------------------------------------------------------------------------------|
| `domain-api`          | –          | Ports (interfaces) and domain model only – zero infrastructure                        |
| `domain-impl`         | –          | Business logic implementing the inbound port interfaces                               |
| `adapter-in-web`      | inbound    | HTTP endpoints, Qute SSR templates, SSE adapters, cookie auth mechanism               |
| `adapter-in-outbox`   | inbound    | Listens to outbox CDI events and notifies domain observers                            |
| `adapter-in-starter`  | inbound    | One-time startup beans (starters) for data migrations and one-time bugfixes           |
| `adapter-out-config`  | outbound   | Reads Quarkus/MicroProfile config and environment variables for health/config display |
| `adapter-out-mongodb` | outbound   | MongoDB persistence: user repository, MongoDB viewer, stats adapter                  |
| `adapter-out-outbox`  | outbound   | Wraps the outbox library; enqueues and queries outbox tasks                           |
| `adapter-out-scheduler` | outbound | Reads Quarkus scheduler metadata for health/cronjob display                          |
| `adapter-out-slack`   | outbound   | Slack notification adapter                                                            |
| `application-quarkus` | –          | Wiring only: CDI, configuration, integration tests                                   |

### External Dependencies

#### `de.chrgroth.quarkus.outbox`

Provided via [christiangroth/quarkus-outbox](https://github.com/christiangroth/quarkus-outbox) (GitHub Packages). Three artifacts:

- `domain-api` – outbox contracts: `OutboxPartition`, `OutboxEvent`, `OutboxTaskDispatcher`, `OutboxTaskResult`, `RetryPolicy`, and associated types
- `domain-impl` – Quarkus implementation: `OutboxImpl`, `OutboxProcessor`, `OutboxWakeupService`, `OutboxStartupRecovery`, `OutboxPartitionWorker`
- `adapter-out-persistence-mongodb` – MongoDB persistence: at-least-once delivery, atomic claim, partition-level pause/resume, task deduplication, priority ordering

#### `de.chrgroth.quarkus.starters`

Provided via [christiangroth/quarkus-one-time-starters](https://github.com/christiangroth/quarkus-one-time-starters) (GitHub Packages). Three artifacts:

- `domain-api` – contracts: `Starter`, `StarterSkipPredicate`, `StarterCompletionFlag`
- `domain-impl` – execution orchestration and startup observer
- `adapter-out-persistence-mongodb` – MongoDB persistence for starter execution state

## Level 2

*work in progress*

# Runtime View

*work in progress*

# Deployment View

## Infrastructure Level 1

The application is deployed on an existing VPS running Docker Swarm. Traefik handles routing, TLS termination, and HTTPS. MongoDB is hosted externally on MongoDB Atlas.

| Component     | Technology              | Notes                                      |
|---------------|-------------------------|--------------------------------------------|
| Application   | Quarkus (native Docker) | Deployed as a Docker Swarm service         |
| Reverse Proxy | Traefik                 | TLS via Let's Encrypt, already provisioned |
| Database      | MongoDB Atlas           | Two projects: prod + dev                   |

## Infrastructure Level 2

Secrets are never stored in deployment configuration – always provided via environment variables from a `.env` file that is not checked into Git.

### Environments

|                 | Local                     | Production         |
|-----------------|---------------------------|--------------------|
| MongoDB         | Atlas Dev Cluster         | Atlas Prod Cluster |
| Quarkus Profile | `dev`                     | `prod`             |
| Container       | no (direct Quarkus start) | Docker Swarm       |

Quarkus profile is controlled via environment variable:

```bash
QUARKUS_PROFILE=prod
```

### Deployment Workflow

Build the application as a Quarkus native Docker image, push to the GitHub Container Registry, copy the Docker stack file to the VPS via SCP, and deploy via Docker Swarm stack.

### Release Process

- **Release plugin** – `net.researchgate.release` manages version bumping and Git tagging
- **Release-Notes plugin** – custom Gradle plugin (`de.chrgroth.gradle.plugins.release-notes`) maintained in https://github.com/christiangroth/gradle-release-notes-plugin
- **CI/CD** – the GitHub Actions workflow (`gradle.yml`) runs `./gradlew build` on every push; runs `./gradlew release` only on pushes to `main`; after release, the Docker stack
  file is copied to the VPS via SCP and the stack is deployed via SSH. All secrets (including `SLACK_WEBHOOK_URL`) must be configured as GitHub Actions repository secrets.
- **Snippet requirement** – every branch that is not `main` or `dependabot/*` **must** contain at least one release note snippet in `docs/releasenotes/snippets/`; the build fails
  without it. Create snippets with the corresponding Gradle tasks (`releasenotesCreateFeature`, `releasenotesCreateBugfix`, …); filenames follow the pattern
  `{branch-last-segment}-{type}.md`. The `releasenotesEnsureVersion` task (automatically hooked to `beforeReleaseBuild`) scans snippets at release time and bumps the project
  version to the correct semver level: minor bump when feature snippets are present, major bump when update-notice snippets are present.

# Cross-cutting Concepts

## Testing Strategy

Tests follow the *Test Your Boundaries* principle mapped to the hexagonal architecture:

| Layer                   | Entry point                            | Test doubles                                            | Module                    | Framework                     |
|-------------------------|----------------------------------------|---------------------------------------------------------|---------------------------|-------------------------------|
| 1 – Domain logic        | Inbound port (`*Port` in `domain-api`) | MockK mocks for all outbound ports                      | `domain-impl`             | JUnit 5 + MockK               |
| 2 – Outbound adapters   | Outbound port interface                | None – real infra (MongoDB dev-service, external mocks) | `application-quarkus`     | `@QuarkusTest`                |
| 3 – Inbound adapters    | HTTP endpoint / scheduler `run()`      | CDI mocks via `@InjectMock`                             | `application-quarkus`     | `@QuarkusTest` + REST Assured |
| 4 – App wiring          | Health/metrics endpoints               | None                                                    | `application-quarkus`     | `@QuarkusTest`                |
| 5 – Adapter-local logic | Class under test                       | MockK mocks                                             | individual adapter module | JUnit 5 + MockK               |

Layer 5 applies to adapter modules where the logic is pure (e.g. `adapter-in-starter`, `adapter-out-scheduler`).

## Authentication and Access Control

Authentication is cookie-based:

- The user logs in via a username/password form (`POST /login`).
- On success, a `LoginServicePort` validates the credentials; the password hash is verified against the stored bcrypt hash.
- An encrypted session token (AES via `TokenEncryptionPort`) is written into an `HttpOnly` cookie named `james-session`.
- Every subsequent request is authenticated by `CookieAuthMechanism`, which decrypts the cookie, loads the user from `UserRepositoryPort`, and builds a `QuarkusSecurityIdentity` with the user's roles.
- On logout (`GET /logout`), the cookie is invalidated by setting it to an empty value with `maxAge=0`.
- Users have one of three roles (`USER`, `DEVELOPER`, `ADMIN`), which control which dashboard is shown after login.

## Error Handling

All domain failures are represented as typed `DomainError` values wrapped in Arrow's `Either<DomainError, T>`.

- Port interfaces return `Either<DomainError, T>` instead of raw domain objects or throwing exceptions.
- Infrastructure adapters (`adapter-out-*`) catch all exceptions at the adapter boundary and convert them to typed `Either.Left<DomainError>` values – no exceptions cross port
  boundaries.
- Domain services compose multiple fallible operations using the Arrow `either { }` DSL with `bind()`.
- Web adapters translate `Either.Left<DomainError>` to HTTP error responses (redirect with `?error=<code>`).
- Error codes follow the convention `<AREA>-<NNN>` (e.g. `LOGIN-001`). Codes are stable once published.

## Outbox Pattern

All external API operations and domain-level async tasks are routed through a persistent outbox. This ensures reliability and decouples producers from consumers.

Successfully processed events are moved to `outbox_archive` (audit log). Internal triggers between services use CDI events (not the outbox).

## Server-Sent Events (SSE) and Live Updates

Backend services notify SSE streams via CDI events. The SSE endpoint delivers the initial state on connect, then pushes named update events to connected clients via per-user
reactive streams.

## Scheduler Jobs

*work in progress*

## Starters

One-time startup beans for data migrations, schema changes, and one-time bugfixes. Each starter executes exactly once in `NORMAL` (prod) mode; failed starters are retried on the
next application start. The Quarkus scheduler is blocked until all starters succeed.

## Frontend Approach

No separate frontend project. The UI is rendered server-side using Quarkus Qute templates. Dynamic interactions are handled via vanilla JS with the fetch API. No React, Vue, npm,
Node.js, or build steps are required.

**Technology stack:**

- Templates: Qute (Quarkus SSR)
- CSS: Bootstrap 5 via WebJar
- Interactivity: Vanilla JS (fetch API)
- Icons: Font Awesome via WebJar
- Live Updates: Server-Sent Events via native `EventSource` API
- Markdown rendering: marked via WebJar (docs and release notes pages)

## Documentation and Release Notes Serving

Architecture documentation (`docs/arc42`), ADRs (`docs/adr`), and release notes (`docs/releasenotes`) are served to the logged-in user directly from the application. A Gradle copy
task bundles the Markdown files into the `adapter-in-web` classpath at build time. A `DocsResource` endpoint reads and passes the raw Markdown to Qute templates; the `marked`
WebJar renders it in the browser.

## Configuration

All sensitive configuration is provided via environment variables:

*work in progress*

# Architecture Decisions

| ADR                                                         | Title                                              |
|-------------------------------------------------------------|----------------------------------------------------|
| [0001](../adr/0001-using-arc42-as-project-documentation.md) | Using arc42 as Project Documentation               |
| [0002](../adr/0002-backend-hexagonal-architecture.md)       | Backend: Hexagonal Architecture                    |
| [0003](../adr/0003-no-separate-frontend-project.md)         | No Separate Frontend Project                       |
| [0004](../adr/0004-using-ai-coding-agents.md)               | Using AI Coding Agents                             |
| [0005](../adr/0005-markdown-rendering-library.md)           | Markdown Rendering Library: marked                 |
| [0006](../adr/0006-error-handling-concept.md)               | Error Handling: Arrow Either&lt;DomainError, T&gt; |
| [0007](../adr/0007-persistent-outbox-pattern.md)            | Persistent Outbox for external API Operations      |

# Quality Requirements

## Quality Requirements Overview

*work in progress*

## Quality Scenarios

*work in progress*

# Risks and Technical Debts

## Risks

*work in progress*

## Technical Debts

*work in progress*

# Glossary

| Term              | Definition                                                                                                        |
|-------------------|-------------------------------------------------------------------------------------------------------------------|
| App               | A named, reusable data application defined by a Developer. Contains one or more Versions.                         |
| Version           | A released snapshot of an App. Carries a semver number, release date, and release notes.                          |
| Entity            | A named, typed data model within a Version. Has a globally unique ID and a list of Properties.                    |
| Property          | A named, typed field within an Entity. Has an immutable intra-entity ID, a data type, and constraints.            |
| Data type         | One of: `long`, `Double`, `boolean`, `String`, `date`, `time`, `datetime`, `ref`, `List`, `object`.               |
| Ref               | A property type representing a reference to an object of the same or another Entity in the same App Version.      |
| Object            | An inline nested structure with its own property list. Analogous to an anonymous Entity without a global ID.      |
| Constraint        | A validation rule attached to a Property (e.g. `NOT NULL`, `UNIQUE KEY`).                                         |
| Report            | A named view within an App. Contains one or more Pages; may load filtered entity data.                            |
| Page              | A single HTML + JavaScript unit within a Report.                                                                  |
| Installation      | A User's personal instance of an App Version, containing that User's data objects.                                |
| Data sharing      | A feature allowing a User to invite another User to share data within a shared installation.                      |
| Semver            | Semantic versioning (Major.Minor.Patch). Version numbers in James Platform are derived automatically.              |
| Breaking change   | A schema change that is incompatible with existing data (e.g. removing an Entity or renaming an immutable ID).    |
| Starter           | A one-time startup bean that executes exactly once (data migrations, schema fixes).                               |
| Outbox            | A persistent queue for reliable delivery of external API operations (at-least-once).                              |
