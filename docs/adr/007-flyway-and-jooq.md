# Using Flyway and jOOQ for Schema Evolution and Persistence Code Generation

* Status: accepted / reopened
* Deciders: Chris
* Date: 2025-10-01

## Context and Problem Statement

We needed a robust solution for database schema management and type-safe data access in our Quarkus-based application. The previous approach using SQLDelight proved problematic as it wasn't production-ready, had integration issues with JTA transaction management, and required manual schema evolution.

## Decision Drivers

* **Type Safety**: Ensure compile-time type safety for database operations
* **Single Source of Truth**: Avoid duplicate schema definitions between migration scripts and data access code
* **Quarkus Integration**: Seamless integration with Quarkus and Kotlin
* **Transaction Management**: Support for standard JTA transaction management
* **Developer Experience**: Good tooling and code generation to reduce boilerplate

## Considered Options

### 1. Exposed
* **Pros**: Native Kotlin DSL, type-safe queries
* **Cons**: Poor Quarkus integration, custom transaction management, own lifecycle management

### 2. Flyway & JPA
* **Pros**: De facto standard, good Quarkus integration
* **Cons**: Duplicate schema definition (SQL + JPA entities), runtime type safety only

### 3. Flyway & jOOQ
* **Pros**: Single source of truth (SQL migrations), compile-time type safety, good Quarkus integration
* **Cons**: Build-time code generation adds complexity

## Decision Outcome

**Chosen option**: Flyway & jOOQ

We chose Flyway for schema migration and jOOQ for type-safe data access because:

1. **Single Source of Truth**: Database schema is defined once in SQL migration scripts
2. **Type Safety**: jOOQ generates type-safe Kotlin code from the database schema
3. **Quarkus Integration**: Both tools integrate well with Quarkus and Kotlin
4. **Transaction Management**: Works seamlessly with standard JTA annotations

The build overhead of jOOQ code generation was deemed acceptable given the benefits of type safety and reduced maintenance. The application uses separate Gradle modules for subdomains, each with its own database schema, Flyway configurations, and jOOQ code generation, while sharing a single Quarkus default datasource to maintain simplicity.

### Positive Consequences

* Type-safe database access with compile-time validation
* No duplicate schema definitions
* Standard transaction management integration
* Better developer experience with code completion and refactoring support

### Negative Consequences

* Additional build step for jOOQ code generation
* Learning curve for developers unfamiliar with jOOQ
* Slightly more complex build configuration

## Implementation Details

The basic configuration and minimal glue code are located in the `adapter-out-postgres` Gradle module, which serves as the foundation for database access across subdomains. Each subdomain module contains:

1. Flyway migration scripts in `src/main/resources/db/migration`
2. jOOQ configuration for code generation
3. Domain-specific repository implementations using the generated jOOQ code
