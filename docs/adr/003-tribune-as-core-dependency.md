# Arrow as core dependency

* Status: accepted
* Deciders: Chris
* Date: 2025-10-12

## Context and Problem Statement

We needed a robust approach to error handling that avoids using exceptions for control flow. Exceptions are problematic because they:

- Break referential transparency
- Make error handling implicit rather than explicit
- Can lead to unexpected control flow
- Are often used for business logic validation, which is an anti-pattern

## Decision Drivers

* **Explicit Error Handling**: Make errors part of the function signature
* **Type Safety**: Leverage Kotlin's type system for compile-time safety
* **Simplicity**: Choose the simplest solution that meets our needs
* **Composability**: Support for combining validations and operations
* **Minimal Dependencies**: Only include what's necessary

## Considered Options

### 1. Arrow

* **Pros**: Comprehensive functional programming toolkit, `ValidatedNel` for error accumulation, good Kotlin integration
* **Cons**: Steeper learning curve, more features than we currently need

### 2. kotlin-result

* **Pros**: Simpler, more focused on basic Result types
* **Cons**: Lacks built-in error accumulation, fewer functional programming features

### 3. Tribune (previous choice)

* **Pros**: Specialized for validation
* **Cons**: More complex than needed, adds another layer on top of Arrow

## Decision Outcome

**Chosen option**: Arrow

We chose Arrow's `ValidatedNel` for its ability to:

1. Accumulate multiple validation errors
2. Keep error handling explicit in the type system
3. Compose validation logic cleanly
4. Integrate well with our domain model

### Positive Consequences

- Clear, type-safe error handling
- No more exceptions for business logic
- Better composition of validation logic
- Explicit error types that are part of the function signature

### Negative Consequences

- Learning curve for developers unfamiliar with functional programming
- Slightly more verbose than exception-based code
- Need to handle errors explicitly at call sites

## Implementation Details

We're using Arrow's `ValidatedNel` in our domain models to handle validation and error accumulation. Here are two examples:

1. **Single Error Case** (from User domain model):

```kotlin
fun changePassword(passwordHash: String): ValidatedNel<DomainError, User> =
  ensure(UserStatus.ACTIVE, UserDomainErrorCodes.USER_DEACTIVATED)
    .map {
      it.copy(
        passwordHash = passwordHash,
        passwordStatus = PasswordStatus.PERMANENT,
        deactivationCounter = 0u,
      )
    }
```

2. **Multiple Error Accumulation** (example for user registration):

```kotlin
fun registerUser(
  username: String,
  email: String,
  password: String
): ValidatedNel<DomainError, User> =
  validateUsername(username)
    .zip(
      validateEmail(email),
      validatePassword(password)
    ) { _, _, _ ->
      // Only executed if all validations pass
      User.create(
        username = username,
        email = email,
        passwordHash = hashPassword(password)
      )
    }

private fun validateUsername(username: String): ValidatedNel<DomainError, String> =
  when {
    username.isBlank() ->
      DomainError(
        code = UserDomainErrorCodes.USERNAME_EMPTY,
        errorMessage = "Username cannot be empty"
      ).invalidNel()
    username.length < 3 ->
      DomainError(
        code = UserDomainErrorCodes.USERNAME_TOO_SHORT,
        errorMessage = "Username must be at least 3 characters"
      ).invalidNel()
    else -> username.validNel()
  }

// Similar validateEmail and validatePassword functions...
```

In the second example, if multiple validations fail (e.g., both username and email are invalid), all errors will be collected in the `NonEmptyList` and returned together, rather
than failing fast after the first error.

Errors are represented as a sealed hierarchy with `DomainError` as the base class, making error handling explicit and type-safe throughout the application.
