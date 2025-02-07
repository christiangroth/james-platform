# Add dependency to tribune to core modules

* Status: in progress
* Deciders: Chris
* Date: 2023-03-16

## Context and Problem Statement

The core modules should be mostly free of dependencies to keep the business logic clean and to avoid dependencies on technical stuff.

In the first approach parsing, validating and error handling was self-implemented. But it turned out to be a lot of boilerplate code for something solved multiple times in the OSS world. Thus the self-implemented solution should be replaced by a library.

## Decision Drivers

* Have validation for business models without using exceptions
* Being able to return valid results or validation errors from business methods

## Considered Options

* [Tribune (based on Arrow)](https://github.com/sksamuel/tribune)
* [Arrow](https://arrow-kt.io/)
* [kotlin-result](https://github.com/michaelbull/kotlin-result)

## Decision Outcome (?)

**Tribune**: Due to the included parsing options I chose to go with tribune. Also kotlin-result did not feel that fluent, i.e. for object creation usecases
