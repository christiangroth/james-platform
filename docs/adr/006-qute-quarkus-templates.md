# Integrate with Quarkus authentication system

* Status: accepted
* Deciders: Chris
* Date: 2025-03-05

## Context and Problem Statement

Server side rendering templates should be integrated with Quarkus and support WebJARs for dependencies.

## Decision Drivers

* Native Quarkus support
* Template composition using includes

## Considered Options

* [Qute - Quarkus Templating Engine](https://quarkus.io/guides/qute)

## Decision Outcome

**Qute**: Designed for Quarkus. Compile time cheks. Deep integration.
