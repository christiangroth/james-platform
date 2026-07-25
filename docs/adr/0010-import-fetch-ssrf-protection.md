# Data Import Fetch: SSRF Protection

* Status: accepted
* Deciders: Chris
* Date: 2026-07-25

## Context and Problem Statement

The Data Import (ETL) feature lets a User with the `DATA_IMPORT` role supply an arbitrary
source URL and Bearer token; the server fetches that URL on the User's behalf
(`ImportFetchAdapter`) and stores the response as an import document. Since the target URL is
fully User-controlled, the fetch is a textbook Server-Side Request Forgery (SSRF) vector: a
malicious or careless User could point it at internal infrastructure (localhost services, the
cloud metadata endpoint `169.254.169.254`, other hosts inside the deployment network) or use it
to exhaust server resources via a huge or slow response.

How should the outbound fetch be constrained so that a User-supplied URL cannot be used to
reach internal/local network targets or exhaust server resources, while keeping the feature
usable for legitimate public JSON APIs?

## Decision Drivers

* The role that can trigger imports (`DATA_IMPORT`) is less trusted than Developer/Admin — it
  is meant for ordinary Users, so the fetch must defend against hostile input, not just
  careless input.
* Must block the common SSRF targets: loopback, link-local (including the `169.254.169.254`
  cloud metadata address), site-local/private ranges, and DNS rebinding across multiple
  resolved addresses.
* Must not let a validated public host redirect the request to a blocked target afterwards.
* Must bound resource usage: connection/response time and response size.
* Implementation and operational simplicity — no additional infrastructure (proxy, allow-list
  service) for a single-developer, personal-use platform.

## Considered Options

1. **In-process validation before fetch: scheme allow-list + resolved-address range check + no
   redirects + timeouts + size cap** (`ImportFetchAdapter`)
2. **Outbound HTTP proxy / dedicated egress gateway enforcing network-level allow-listing**
3. **Host allow-list (User or Admin configures specific permitted hosts/domains)**

## Decision Outcome

Chosen option: **"In-process validation before fetch"**. `ImportFetchAdapter.validate()`
rejects the request before any network call is made unless: the URL scheme is `http` or
`https`; the host resolves (via `InetAddress.getAllByName`) to at least one address; and *none*
of the resolved addresses are loopback, link-local, site-local, multicast, or "any local".
Checking *all* resolved addresses (not just the first) covers DNS-rebinding attempts that
return multiple A/AAAA records. If validation passes, the actual request is made with
`HttpClient.Redirect.NEVER` (a validated host must not be able to redirect to a blocked target
after the check), a 5s connect timeout, a 10s request timeout, and the response body is read in
fixed-size chunks and aborted once it exceeds 5 MiB, so nothing is buffered without bound.

### Positive Consequences

* Blocks the standard SSRF target set, including the cloud metadata endpoint, without any
  additional infrastructure or configuration.
* No redirect-based bypass of the address check.
* Bounded connection time, request time, and memory usage per fetch.
* Self-contained in one adapter class (`ImportFetchAdapter`), easy to test in isolation (see
  `ImportFetchAdapterTests`).

### Negative Consequences

* **DNS rebinding between validation and the actual request remains possible in principle**:
  `validate()` resolves the host once to check the address range, but the subsequent
  `HttpClient.send()` call performs its own DNS resolution and could, in theory, hit a
  different (attacker-controlled, time-of-check/time-of-use) address if the attacker's DNS
  server changes the answer between the two lookups. This residual gap is explicitly accepted:
  closing it fully would require pinning the validated IP for the actual request (e.g. a custom
  resolver, or connecting directly to the validated IP with the original `Host` header), which
  is disproportionate complexity for a single-developer, invite-only, low-traffic platform where
  an attacker would already need to control both the target URL's DNS and its Bearer token.
* Exotic address encodings that might not be flagged by `InetAddress`'s range checks are not
  explicitly tested for; the existing test suite covers the common cases (loopback,
  `localhost`, the link-local metadata IP) but not adversarial address-encoding edge cases.
* No allow-list of "known good" hosts — any public host passes, which is intentional (the
  feature must work with arbitrary public JSON APIs) but means the blocked-range check is the
  only defense.

## Pros and Cons of the Options

### In-process validation before fetch

* Good, because it requires no additional infrastructure to operate.
* Good, because it is co-located with the fetch logic, easy to reason about and test.
* Good, because it covers the standard SSRF target set and DNS-rebinding-via-multiple-records.
* Bad, because it cannot fully close the time-of-check/time-of-use DNS-rebinding gap between
  validation and the actual request (accepted risk, see Negative Consequences).

### Outbound HTTP proxy / dedicated egress gateway

* Good, because network-level enforcement is harder to bypass than in-process checks.
* Bad, because it requires operating additional infrastructure, disproportionate for a
  single-developer personal project.
* Bad, because it was not pursued — no such infrastructure exists in the deployment (see
  [Deployment View](../arc42/arc42.md#deployment-view)).

### Host allow-list

* Good, because it would eliminate SSRF risk entirely for hosts not on the list.
* Bad, because it defeats the feature's purpose: importing from arbitrary public JSON APIs the
  User discovers, not a pre-configured set.
* Bad, because it was not pursued — no such configuration exists.

## Links

* [`ImportFetchAdapter.kt`](../../adapter-in-web/src/main/kotlin/de/chrgroth/james/platform/adapter/in/web/ImportFetchAdapter.kt)
* [`ImportFetchAdapterTests.kt`](../../adapter-in-web/src/test/kotlin/de/chrgroth/james/platform/adapter/in/web/ImportFetchAdapterTests.kt)
* [arc42: Data Import (ETL)](../arc42/arc42.md#data-import-etl)
