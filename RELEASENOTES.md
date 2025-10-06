# 0.2.0 (2025.10.06)

## Highlights

Changed persistence technology stack to Flyway and JOOQ.

## New Features

* Using Flyway to handle schema migrations and evolution
* Configured JOOQ for persistence code generation to avoid duplication of database definitions
* Split up modules to use separate database schemas and have separate Flyway and JOOQ configurations backed by a single
  datasource

# 0.1.0 (2025.09.25)

## Highlights

Added quarkus runtime and first deployment infrastructure.

## New Features

* Implemented basic user management
* Added Quarkus runtime

# 0.0.10 (2024.02.20)

## New Features

* implemented versioning and releasenotes infrastructure
* almost complete domain logic for
    * app development
    * data storage and typesystem (beta)
    * simple user handling
    * workspaces
