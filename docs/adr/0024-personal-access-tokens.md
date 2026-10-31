# Personal Access Tokens for a Scoped JSON API

* Status: accepted
* Deciders: Chris
* Date: 2026-10-31

Technical Story: [#729 Personal access tokens with scopes for the JSON API](https://github.com/christiangroth/james-platform/pull/729)

## Context and Problem Statement

All of James Platform's functionality was only reachable through the server-rendered UI with a cookie session (see ADR
[0007](0007-local-cookie-based-authentication.md)). Scripts and AI coding agents need to read (and partly change) a User's app data and a
Developer's apps, versions and drafts as well, e.g. to inspect structures and data during a migration from the predecessor system, or to let an
agent prepare and release a draft. Handing them a session cookie (or the password) would grant everything the account can do, without a way to
narrow it down or to switch access off independently.

How can a User grant a script or agent narrowly scoped, revocable access without sharing their credentials?

## Decision Drivers

* Least privilege: read vs. write, User vs. Developer side, optionally only some Apps.
* Access must be switchable off and on without rotating the secret (grant access only for limited periods).
* A leaked token must never reach the UI, and in particular must never be able to manage tokens (no privilege escalation).
* No new infrastructure or external identity provider (same reasoning as ADR 0007); same pattern as the sibling project spotify-control.
* The existing domain ports and their ownership checks must keep working unchanged.

## Considered Options

1. **Per-User personal access tokens with scopes, accepted only on a dedicated `/api/v1` JSON API** — chosen.
2. Accept tokens on the existing `/ui` endpoints, mapping scopes to roles.
3. OAuth2 client credentials via an external authorization server.

## Decision Outcome

Chosen option 1.

* **Token**: `ApiToken` belongs to exactly one User (`userId`), has a name, a set of `ApiTokenScope`s, an optional App restriction (`appIds`)
  and an `active` flag. Only the SHA-256 hash of the secret (`jp_` + 32 random bytes, base64url) is stored in the `api_token` collection,
  plus an 8-character prefix for recognition in the UI. The plain token is shown exactly once after creation. Usage is recorded in `lastUsedAt`
  with minute granularity.
* **Scopes**: `USER_READ`, `USER_WRITE` (data of the owner's installed Apps, optionally restricted to `appIds`), `DEVELOPER_READ` (all own Apps,
  all Versions including drafts), `DEVELOPER_WRITE` (create, edit and delete drafts) and `DEVELOPER_RELEASE` (publish the draft). Write and release
  imply read. A scope only takes effect while the owner still has the matching role (`USER` resp. `DEVELOPER`) and is active; a token without any
  effective scope is rejected. Tokens of deleted users are deleted along with the user.
* **Authentication**: `ApiTokenAuthMechanism` (priority above `CookieAuthMechanism`) authenticates `Authorization: Bearer <token>` requests as the
  token owner (principal = username, so all User/Developer ports apply their usual ownership checks), with one `API_*` role per effective scope —
  never the owner's own roles.
* **Authorization**: `ApiTokenAccessFilter` rejects every token-authenticated request outside `/api/` (default deny); every `/api/v1` endpoint
  declares its required scope via `@RolesAllowed`. Cookie sessions carry no `API_*` roles, so the API is reachable with a token only, and the UI
  (including token management on the profile page) is reachable with a session only. Developer endpoints additionally check App ownership before
  delegating to the version ports, which have no ownership check of their own.
* **API**: plain JSON DTOs (`ApiDtos.kt`), never the domain model (value classes, sealed hierarchies, contract stability). Errors are JSON
  (`code`, `message`, `details`), domain "not found" errors map to 404, all other domain errors to 400. Installations outside a token's App
  restriction behave as if they did not exist.

### Positive Consequences

* Scripts and agents get exactly the access they need, can be switched off at any time, and a token never grants UI access.
* No change to the domain's authorization model: the API reuses the same ports and ownership checks as the UI.

### Negative Consequences

* A second, parallel inbound adapter surface (`/api/v1`) has to be kept in sync with new domain capabilities.
* Tokens have no expiry date; switching them off is a manual action.
* Uninstall/install, App management (create/rename/delete Apps) and data import are deliberately not part of the API.

## Pros and Cons of the Options

### Per-User personal access tokens on a dedicated JSON API

* Good, because default-deny outside `/api/` keeps the whole UI (and token management) out of reach for tokens.
* Good, because a stable JSON contract is easier for scripts and agents than form posts and HTML.
* Bad, because the API endpoints are additional code to maintain.

### Tokens on the existing `/ui` endpoints

* Good, because no new endpoints are needed.
* Bad, because those endpoints answer with HTML or UI-specific JSON (redirect URLs, i18n messages) and take form posts.
* Bad, because every UI endpoint would need a scope mapping, and forgetting one silently widens what a token can do.

### OAuth2 client credentials with an external authorization server

* Good, because it is a standard with expiry and rotation built in.
* Bad, because it is an unjustified external dependency for an invite-only, single-Developer system (see ADR 0007).
