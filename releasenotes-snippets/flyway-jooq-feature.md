* Using Flyway to handle schema migrations and evolution
* Configured JOOQ for persistence code generation to avoid duplication of database definitions
* Split up modules to use separate database schemas and have separate Flyway and JOOQ configurations backed by a single
  datasource
