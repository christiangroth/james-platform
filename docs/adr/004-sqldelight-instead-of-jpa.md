# Using SQLDelight instead of JPA

* Status: superseded by [ADR-007](007-flyway-and-jooq.md)
* Deciders: Chris
* Date: 2025-02-13

## Context and Problem Statement

Defining and using database entities should be simple and type safe. Using the default stack consisting of JPA and a
schema migration tool like liquibase or flyway is cumbersome. Entities are written in Kotlin, database schema is
duplicated in schema migration tool and everything has to be kept in sync manually.

A database first approach might be helpful.

## Decision Drivers

* Generating entity classes form db schema
* Built-in schema migration support
* Kotlin support, obviously

## Considered Options

* [SQLDelight](https://github.com/sqldelight/sqldelight)

## Decision Outcome

**SQLDelight**: Super easy to use. Nice and slim API. Drawbacks: No out of the box paging.

## Open TODOs

* Check transaction handling
* Check result paging
