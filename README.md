# James Platform

...

## Features

- ...

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
