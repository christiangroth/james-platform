# Integrate with Quarkus authentication system

* Status: accepted
* Deciders: Chris
* Date: 2025-03-05

## Context and Problem Statement

The first version should contain a simple authentication mechanism.
A form based login and authentication data captured in a cookie are sufficient.
No additional api keys or tokens need to be supported.

## Decision Drivers

* Do not code it yourself
* Easy integration into incoming adapter

## Considered Options

* [Quarkus authentication integration](https://quarkus.io/guides/security-authentication-mechanisms)

## Decision Outcome

**Quarkus authentication integration**: Easy to configure and use. Provides basic cookie handling and redirect after
login feature. Drawbacks: Logout has to be self-implemented in case of http-only cookies.
