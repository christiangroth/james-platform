* Version editor: Entities can now have "migration steps" that convert a property's type or carry a value over
  to its replacement when an app version is published, without writing a migration script.
* A property type change or a deleted-and-replaced property no longer forces a mandatory major version bump if
  the existing data converts successfully.
