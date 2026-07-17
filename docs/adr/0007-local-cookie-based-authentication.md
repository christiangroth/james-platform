# Authentication: Local Cookie-Based Sessions

* Status: accepted
* Deciders: Chris
* Date: 2026-04-11

## Context and Problem Statement

The application originally authenticated users via Spotify OAuth, a leftover from its previous
identity as a Spotify-control tool. James Platform has no relationship to Spotify and is an
invite-only, single-Admin-managed system for a small, known set of users — an external OAuth
provider is both irrelevant and an unnecessary dependency.

How should users authenticate, given the platform is invite-only (no self-registration) and
has no external identity provider it can or should delegate to?

## Decision Drivers

* No self-registration — all accounts are created by an Admin (see [Architecture
  Constraints](../arc42/arc42.md#architecture-constraints)).
* No dependency on a third-party identity provider (Spotify was domain-irrelevant; adding a
  generic OAuth provider is unjustified operational overhead for a single-developer app).
* Sessions must survive browser restarts without forcing frequent re-logins.
* Must integrate cleanly with Quarkus's `SecurityIdentity`/`@RolesAllowed` model.

## Considered Options

1. **Local username/password with encrypted session cookie**
2. **Third-party OAuth (e.g. GitHub, Google) as identity provider**
3. **Quarkus built-in form-based auth with server-side session store**

## Decision Outcome

Chosen option: **"Local username/password with encrypted session cookie"**. An Admin creates
accounts with a bcrypt-hashed password. On successful login (`POST /login`), a
`CookieAuthMechanism` issues an `HttpOnly` cookie (`james-session`) containing
`username|issuedAt`, encrypted with AES/GCM/NoPadding via a `TokenEncryptionPort` implementation
keyed by `APP_TOKEN_ENCRYPTION_KEY`. The cookie has a 14-day `maxAge` and is transparently
re-issued whenever fewer than 5 days remain, giving a sliding "remember me" expiration without
a server-side session store. Every request is authenticated by decrypting the cookie, loading
the user via `UserRepositoryPort`, and building a `QuarkusSecurityIdentity` with the user's
roles (`USER`, `DEVELOPER`, `ADMIN`, `MONITORING`).

### Positive Consequences

* Zero external dependencies or outbound calls on the authentication path.
* Stateless on the server — no session store to provision, back up, or scale.
* Sliding expiration means active users are never forced to re-login, while idle sessions
  still expire.
* Straightforward integration with `@RolesAllowed`/`SecurityIdentity` via a single
  `HttpAuthenticationMechanism`.

### Negative Consequences

* `APP_TOKEN_ENCRYPTION_KEY` must never change in production — rotating it invalidates every
  active session at once, and there is no key-rotation mechanism.
* No server-side session revocation: an issued cookie remains valid until it expires; there is
  no way to force-logout a specific session before then (deleting/blocking the user account is
  the only immediate mitigation).
* Password reset is Admin-driven only (`AdminUserManagementService`), acceptable given the
  invite-only, single-Admin model but would not scale to self-service users.

## Pros and Cons of the Options

### Local username/password with encrypted session cookie

* Good, because no external dependency or network call is on the authentication path.
* Good, because stateless — no session store required.
* Bad, because there is no key-rotation or server-side session revocation story.

### Third-party OAuth (e.g. GitHub, Google)

* Good, because password storage/hashing is delegated to a specialized provider.
* Bad, because it is an unjustified external dependency for an invite-only, single-Admin app.
* Bad, because it ties account identity to an external provider account the Admin doesn't
  control (provider account loss/ban locks the user out).

### Quarkus built-in form-based auth with server-side session store

* Good, because it uses framework-provided, well-tested primitives.
* Bad, because it requires a session store (in-memory loses sessions on redeploy; external
  store adds operational overhead disproportionate to a personal-use app).

## Links

* [`CookieAuthMechanism.kt`](../../adapter-in-web/src/main/kotlin/de/chrgroth/james/platform/adapter/in/web/CookieAuthMechanism.kt)
* [`TokenEncryptionAdapter.kt`](../../adapter-in-web/src/main/kotlin/de/chrgroth/james/platform/adapter/in/web/TokenEncryptionAdapter.kt)
* [arc42: Authentication and Access Control](../arc42/arc42.md#authentication-and-access-control)
