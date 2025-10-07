# 0.3.0 (2025.10.06)

## Basic configurations for health and matrics

## New Features

* Added Smallrye Health for default health checks
* Added Smallrye Metrics for default prometheus metrics

---

# 0.2.0 (2025.10.06)

## Changed persistence technology stack to Flyway and JOOQ.

## New Features

* Using Flyway to handle schema migrations and evolution
* Configured JOOQ for persistence code generation to avoid duplication of database definitions
* Split up modules to use separate database schemas and have separate Flyway and JOOQ configurations backed by a single
  datasource

---

# 0.1.0 (2025.09.25)

## Added quarkus runtime and first deployment infrastructure.

## New Features

* Implemented basic user management
* Added Quarkus runtime

---

# 0.0.10 (2024.02.20)

## New Features

* implemented versioning and release notes infrastructure
* almost complete domain logic for
    * app development
    * data storage and typesystem (beta)
    * simple user handling
    * workspaces
