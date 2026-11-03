# 0027 - App Configuration as a Singleton Entity

## Status

Accepted

## Context

Legacy james reports hard-code constants: annual goals, holidays, thresholds, target hours. Ported to declarative reports (see
[arc42 "Reports"](../arc42/arc42.md#reports)) they need a place per installation where the User maintains such values, and reports, checks and computed
properties need to read them. The values have the same needs as any App data: typed properties with units, constraints, defaults and nested objects,
versioning, and migration when the App evolves.

## Decision

An App Version may mark **one Entity as its configuration** with the flag `EntityDefinition.configuration` (versioned and diffed like every other
Entity field, rendered as `configuration: true` in the version DSL). A configuration Entity is a normal Entity - same properties, units, constraints,
defaults, nested objects, migration steps and migration script - with exactly **one data object per installation**:

* **Validation:** setting the flag fails if another Entity of the version already is the configuration (`APPVER-073`) or a REF property targets the
  Entity (`APPVER-074`); a REF property cannot target the configuration Entity either. Publishing re-checks all of it plus that the object can be
  created from defaults: every required property has a default (top level also a smart default), a required OBJECT has defaults for its required nested
  properties, BOOLEAN needs none; required REF/LIST properties are not possible (`InvalidConfigurationError`, `APPVER-075`).
* **Lifecycle** (`AppConfigurationService`): the object is created from the defaults when an App is installed, a test installation is created and an
  installation is upgraded (manually or automatically) - so also on upgrading to the version that introduces the configuration. Creation is idempotent;
  the "Einstellungen" page also creates a still missing object (e.g. a test installation whose draft got a configuration later). Upgrades migrate the
  object like any other data (migration steps and script). It can be edited but not deleted (`APPDATA-009`), a second one cannot be created
  (`APPDATA-008`), imports and the test data generator cannot target the configuration Entity.
* **UI:** the developer toggles "Konfiguration" on the Entity in the version editor (token API: `PUT …/entities/{entityId}/configuration` with
  `{ "value": true|false }`). For the User the configuration Entity is no data tile or list and not counted on the dashboard; the App page offers an
  "Einstellungen" button (`/ui/user/apps/{installedAppId}/settings`) opening the generic edit form of the object.
* **Access:** reports reference configuration fields as `config.<field>` (in query filter values, in stat thresholds and as constant stat panel
  `{ stat: Jahresziel, config: zielKm }`); numbers are converted into the unit of the value they are compared with. While the object does not exist
  yet, reports read the defaults. Computed property scripts get a `config` binding (property id to stored value, like `it`).

Rule of thumb: settings without a time dimension (annual goal, holidays, thresholds) belong in the configuration; values that are "valid from" a date
(target hours, vacation entitlement) belong in a data Entity.

## Considered Options

* **`EntityDefinition.configuration` flag (chosen):** the property lives where the Entity lives; deleting, renaming and reordering Entities need no
  extra bookkeeping, and the version diff shows the change inside the Entity's section.
* **`AppVersion.configurationEntityId`:** makes "at most one" structural, but needs a separate field in the version model, diff and API, and must be
  cleared when the Entity is deleted. The "at most one" rule is cheap to validate instead.
* **A separate key/value settings model:** no units, constraints, nested objects or migration - everything the Entity model already offers.

## Consequences

* The generic data UI, validation, versioning and migration are reused unchanged; only listing views skip the configuration Entity.
* A filter value or threshold starting with `config.` always refers to the configuration; a literal text `config.…` cannot be filtered for.
* REFs to the configuration Entity are not supported (keeps it simple); a configuration may itself reference other Entities (nullable REF).
