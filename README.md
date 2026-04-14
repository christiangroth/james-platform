# James Platform

James Platform is a personal Low Code system for building and running data-centric apps without writing boilerplate infrastructure code. It is deployed as a self-hosted,
single-developer tool on a personal VPS and provides a web UI for managing users, defining data models, and running data-centric applications.

## Features

- **User Management** – Admins can register, activate/deactivate, set passwords, and delete user accounts. Roles (USER, DEVELOPER, ADMIN) are assigned per account.
- **Authentication** – Cookie-based login with bcrypt password hashing and persistent sessions (14-day lifetime with automatic renewal).
- **Profile** – Authenticated users can view and update their username, password, and account metadata.
- **Outbox** – Reliable delivery of external operations (e.g. Slack notifications) via a persistent outbox with at-least-once semantics.
- **In-App Documentation** – Architecture docs and coding guidelines are served and rendered directly in the UI.
- **Monitoring** – Prometheus metrics and structured logs shipped to Grafana Cloud.

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
