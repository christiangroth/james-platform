* Developers can now attach a Kotlin migration script to an Entity within a Version, transforming existing data entries when a User upgrades past that Version.
* Migrations run automatically on upgrade and re-validate the transformed data against the current schema; if a migration fails, only that installation's upgrade is aborted and it stays on its previous Version.
