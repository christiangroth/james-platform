* Version editor: Migration steps now also cover unit conversion, filling empty values, and adjusting values to
  tightened constraints, in addition to the existing type conversion and value takeover steps.
* Adding a unit, making a property non-nullable, or tightening a constraint no longer forces a mandatory major
  version bump if the existing data can be converted, filled, or adjusted successfully.
