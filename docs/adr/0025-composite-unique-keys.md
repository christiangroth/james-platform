# 0025 - Composite Unique Keys

## Status

Accepted

## Context

A `UNIQUE KEY` constraint used to apply to each property on its own: every value of that property had to be distinct across all objects of the
Entity. Real data is often identified by a *combination* of values - a meter reading by time stamp and meter, a payslip by date and employer, a
concert by date, venue and artist. The legacy james system, whose data is migrated into the platform, models exactly this: several attributes
marked unique together form one key. With per-property uniqueness such Entities could not be modelled at all (a meter could only ever have one
reading), and imports of such data discarded almost every record.

## Decision

All properties of an Entity carrying `UNIQUE KEY` together form the Entity's single, possibly composite, key. An object violates it only if
another object of the same Entity holds equal values in *all* key properties. With a single key property this is exactly the former behaviour.

* No new constraint type and no Entity-level key definition: the existing per-property flag is reinterpreted, so persistence, the property editor
  and the API stay unchanged. The editor explains the composite meaning next to the checkbox.
* `uniqueKeyProperties` / `uniqueKeyComparisonValues` (domain-api) are the single place computing which existing values a key property is
  compared against: the values of those objects that match the candidate on every *other* key property. The per-property `UniqueKey` check then
  flags a violation exactly when the whole key collides - on every key property, so the user sees which fields form the clashing key.
* Data entry (create, update, re-validation) and the import dry run / accept (including fan-in and the rule that only saved records claim a key)
  use this same comparison.
* There is deliberately no way to declare several independent keys on one Entity.

## Consequences

* Entities with several `UNIQUE KEY` properties become *less* restrictive: data that was valid stays valid, so this needs no migration.
* An App relying on two independent unique properties on one Entity can no longer express that; none is known.
* Breaking-change detection is unchanged: adding or removing `UNIQUE KEY` is still not treated as restrictive, as before.
