# James Platform

James Platform is a personal Low Code system for building and running data-centric apps without writing boilerplate infrastructure code. It is deployed as a self-hosted,
single-developer tool on a personal VPS and provides a web UI for managing users, defining data models, and running data-centric applications.

## Features

- **Low-Code App Builder** – Developers define **Apps** as a series of semantically versioned **Entities**, each with typed, constrained **Properties** (`long`, `Double`,
  `boolean`, `String`, `date`, `time`, `datetime`, `ref`, `List`, `object`) — no infrastructure or CRUD boilerplate to write.
- **Generic Data UI** – A full create/edit/list UI is generated from each Entity definition: sortable list views, constraint-validated forms, Focus mode (carry values
  forward) and Snapshot mode (reusable form templates).
- **Computed Properties & Smart Defaults** – Derived values and form defaults backed by Developer-authored Kotlin scripts, executed backend-side with a timeout guard.
- **Aggregations** – Developers declare precomputed rollups (SUM/COUNT/AVG/MIN/MAX, optional day/week/month/year bucketing, optional grouping via a `ref`) over an Entity's
  data, shown directly on the app installation.
- **App Versioning & Migrations** – Semver version numbers are derived automatically from schema changes; breaking changes can be neutralized by a Developer-authored
  migration script that transforms existing data on upgrade.
- **Data Import (ETL)** – Users import external JSON data into an installed App through a guided fetch → detect → map → dry-run → accept flow, including SSRF-hardened
  fetching and unit-aware value mapping.
- **Data Sharing** – A User can invite others to share an installed App's data, either with full read/write/delete or read-all/edit-own permissions.
- **Developer Test Data** – Developers generate constraint-aware test data (or hand-craft it) in dedicated test installations, without touching real User data.
- **User Management** – Admins can register, activate/deactivate, set passwords, and delete user accounts. Roles (USER, DEVELOPER, ADMIN, MONITORING, DATA_IMPORT) are
  assigned per account.
- **Authentication** – Cookie-based login with bcrypt password hashing and persistent sessions (14-day lifetime with automatic renewal).
- **Profile** – Authenticated users can view and update their username, password, and account metadata.
- **Reliability** – Long-running operations (import accept, uninstall, app/user deletion, version auto-upgrade) run through a persistent outbox for at-least-once execution.
- **In-App Documentation** – Architecture docs, ADRs, and release notes are served and rendered directly in the UI.
- **Monitoring** – Health/config/logs/metrics pages in-app, plus Prometheus metrics and structured logs shipped to Grafana Cloud.

## Tech Stack

| Layer      | Technology                                                      |
|------------|-----------------------------------------------------------------|
| Backend    | Kotlin · Quarkus · Gradle                                       |
| Frontend   | Quarkus Qute (SSR) · Vanilla JS (fetch API) · Bootstrap 5 · SSE |
| Database   | MongoDB Atlas                                                   |
| Deployment | Docker Swarm · Traefik · VPS                                    |
| Monitoring | Grafana Cloud (Prometheus metrics + Loki logs)                  |

## Quick Start (Local Development)

**Prerequisites:** JDK 21+ and a MongoDB Atlas cluster.

1. Copy the required environment variables into a local `.env` file (see [arc42.md](docs/arc42/arc42.md) — *Deployment View* for the full list).
2. Start the application in dev mode with live reload:

```bash
./gradlew :application-quarkus:quarkusDev
```

## Documentation

| Document                                                                          | Description                     |
|-----------------------------------------------------------------------------------|---------------------------------|
| [Architecture (arc42)](docs/arc42/arc42.md)                                       | Full architecture documentation |
| [ADRs](docs/adr/)                                                                 | Architecture Decision Records   |
| [Release Notes](docs/releasenotes/RELEASENOTES.md)                                | Version history                 |
| [Coding Guidelines – Architect](docs/coding-guidelines/role-architect.md)         | Architectural conventions       |
| [Coding Guidelines – Backend](docs/coding-guidelines/role-backend-developer.md)   | Backend coding conventions      |
| [Coding Guidelines – Frontend](docs/coding-guidelines/role-frontend-developer.md) | Frontend coding conventions     |

## Building & Testing

```bash
# Full build (includes tests and static analysis)
./gradlew build

# Tests only
./gradlew test
```

## License

[MIT](LICENSE)
