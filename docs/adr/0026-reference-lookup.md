# 0026 - Reference Lookup: Entering References by Text

## Status

Accepted

## Context

A `ref` property was entered by picking the target object from a select listing all objects of the target Entity. For lookup-like targets with many
objects that are created on the fly - artists of concerts, venues, running shoes - this is slow (hundreds of options) and forces the User to leave the
form, create the target object first and come back. String properties already offer value proposals; references had nothing comparable.

## Decision

A top-level `ref` property may declare a **reference lookup**: a `String` property of its target Entity (`ReferenceLookup.propertyId`) plus an optional
**auto-create** flag.

* The lookup property must be the target Entity's **only** `UNIQUE KEY` property, so a lookup text identifies at most one object. This is checked when
  configuring the lookup and again on publish (`InvalidReferenceLookupError`); changing the reference's target Entity drops the lookup.
* The create/edit form replaces the select by a text input (`reflookup_<propertyId>`) proposing the target's existing lookup values, most referenced by
  this property first. On save, the text resolves to the target object with that value - exact match first, else a single case-insensitive match.
  The stored value stays the target's id, so reads, aggregations, imports and the API are unaffected; API clients keep sending ids.
* Without auto-create, an unknown text is a `ReferenceLookupNotFoundViolation`.
* With auto-create, an unknown text creates a new target object **on save, not while typing**: the form shows the target Entity's further properties
  (`refnew_<propertyId>.<targetPropertyId>`, rendered like an `object` property's nested fields, prefilled with defaults) for values beyond the lookup
  text. The new object is validated with the target Entity's constraints first; only when the referencing object is valid too, the new target is saved
  before the referencing object. Violations of the new target are reported on the reference property (path `reference › property`), nothing is saved.
* Several lookups in one form naming the same new target create it once.

## Consequences

* Cancelling a form never leaves behind a target object, since nothing is created before saving.
* The two saves are not transactional (no MongoDB transactions are used): if the server fails between them, a target object without referencing object
  remains. It is found again by the next save with the same text instead of being created twice.
* Nested `ref` properties (inside `object`) and `List<ref>` do not support lookups; target objects' own references are entered via selects in the
  new-object fields, without nested auto-create.
* Like everywhere else in the platform, required-ness of the new target's properties is enforced by the form, not by the backend.
