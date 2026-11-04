# James Platform

James Platform is a personal Low Code system for building and running data-centric apps without writing boilerplate infrastructure code. It is deployed as a self-hosted,
single-developer tool on a personal VPS and provides a web UI for managing users, defining data models, and running data-centric applications.

## Features

- **Low-Code App Builder** – Developers define **Apps** as a series of semantically versioned **Entities**, each with typed, constrained **Properties** (`long`, `Double`,
  `boolean`, `String`, `date`, `time`, `datetime`, `ref`, `List`, `object`) — no infrastructure or CRUD boilerplate to write. Several unique-key properties
  of an Entity form one composite key (e.g. time stamp + meter). A `List` of `object`s holds repeatable groups of fields, e.g. the sessions of a swimming
  day (lane length + lanes), entered with "+ add" in the form. Numeric properties can carry a unit (time, distance, currency, mass, energy, volume) and accept
  unit-suffixed input such as `15km 400m` or `75,3kg`.
- **Generic Data UI** – A full create/edit/list UI is generated from each Entity definition: sortable list views, constraint-validated forms, Focus mode (carry values
  forward) and Snapshot mode (reusable form templates). References can be entered as text with proposals, creating unknown targets on save.
- **Computed Properties & Smart Defaults** – Derived values (e.g. BMI, pace, sums, yes/no as number) and form defaults written in the platform's small expression
  language, checked on save (no scripting engine, works in the native image). Smart defaults make data entry quick: today, the day after the last entry
  (`next_day(datum)`), the last used value or reference (`last(schuhe)`), end of month, workdays between two dates without configured holidays
  (`workdays(start, ende, config.feiertage)`) - and they follow other fields of the form while it is filled in (e.g. vacation end = start).
- **Aggregations** – Developers declare precomputed rollups (SUM/COUNT/AVG/MIN/MAX, also over fields nested in object properties and over computed properties,
  optional day/week/month/quarter/year bucketing with a configurable period start,
  optional grouping via a `ref` or another property) over an Entity's data in the version editor. Ungrouped values are shown directly on the app installation;
  grouped and/or time-bucketed ones as a compact, capped table.
- **Reports** – Developers declare reports per App Version in YAML: named queries (Entity, filter rules, time range, fields incl. one `ref` hop and
  fields nested in object properties, sorting, limit), optionally aggregated on the fly (sum/average/count/min/max/last per day/week/month/quarter/year
  with a configurable period start and/or per `ref` or property), laid out as tabs, rows and panels: tables with unit-aware formatting, Markdown text,
  time series (line/column/area, several series, second axis, zoom), bar charts, pie/donut charts of shares per category, calendar heatmaps (one cell
  per day, e.g. journal ratings or work hours) and matrix heatmaps (e.g. month × category), and single values with comparison to the previous period and
  threshold colors. Variables (time range, selection from a query such as year or employer) appear as controls on top of the report and feed query
  ranges and filters; per-query transformations add calculated fields (the small safe expression language without scripting engine: arithmetic,
  conditions with `if`/`case`, comparisons, and/or/not, texts, yes/no values as 1/0, dates and times such as minutes between two times, days,
  weekday or today, configuration values; typed results as number with unit, text, yes/no or date), regroup rows after calculations (e.g. net
  working time per year and type of day), running totals,
  moving averages, year-over-year values, forecasts to the period end, resampling with fill-forward, consumption from meter readings (per day or
  spread over buckets), "value valid at date" lookups (e.g. weekly hours or vacation entitlement valid from a date), joins of two queries by key or
  reference, records / top-N per group or year and goals vs. actuals (also as stat with progress bar). Checks find gaps (e.g. a missing monthly
  payslip or meter reading, one entry per workday/week/month, skipping holidays and vacation from a configuration list or an Entity) and rule violations
  (values out of range, missing required values) and list them in a warnings panel with a counter on the tab. The version editor validates definitions with line
  numbers and previews them against a test installation; Users open the reports of their installed Apps. Queries run backend-side on the installation's
  data only.
- **App Configuration** – An App Version may mark one Entity as its configuration: a singleton with exactly one object per installation, created from
  the defaults on install and upgrade and maintained by the User under "Einstellungen". Reports read it as `config.<field>` (filter values, thresholds,
  constant single values), computed properties and smart defaults as well - goals and thresholds become data instead of code.
- **App Versioning & Migrations** – Semver version numbers are derived automatically from schema changes; breaking changes can be neutralized by declarative migration
  steps (convert a property's type or unit, carry a value over to its replacement, fill empty values, or adjust values to tightened constraints) that
  transform existing data on upgrade. The version editor suggests a matching migration step for every breaking change not
  yet covered by one, ready to create with a single click, and offers an interactive preview showing before/after values and validation issues for one existing
  object at a time, without requiring a real dry-run or publish.
- **Data Import (ETL)** – Users import external JSON data into an installed App through a guided fetch → detect → map → dry-run → accept flow, including SSRF-hardened
  fetching, unit-aware value mapping (also into fields nested in object properties) and optionally treating a numeric 0 as an empty value. Import definitions can be renamed, e.g. to tell apart several imports into the same Entity.
- **Data Sharing** – A User can invite others to share an installed App's data, either with full read/write/delete or read-all/edit-own permissions.
- **Developer Test Data** – Developers generate constraint-aware test data (or hand-craft it) in dedicated test installations, without touching real User data.
- **User Management** – Admins can register, activate/deactivate, set passwords, and delete user accounts. Roles (USER, DEVELOPER, ADMIN, MONITORING, DATA_IMPORT) are
  assigned per account.
- **Authentication** – Cookie-based login with bcrypt password hashing and persistent sessions (14-day lifetime with automatic renewal).
- **Profile** – Authenticated users can view and update their username, password, and account metadata.
- **Personal Access Tokens & JSON API** – Users create deactivatable, scoped tokens on their profile for scripts and AI agents: read/write the data of all or
  selected installed Apps, install Apps, prepare and dry-run imports, and (as Developer) create Apps, read all Apps, Versions and drafts, edit drafts, or
  release them via the `/api/v1` JSON API.
- **Reliability** – Long-running operations (import accept, uninstall, app/user deletion, version auto-upgrade) run through a persistent outbox for at-least-once execution.
- **In-App Documentation** – Architecture docs, ADRs, and release notes are served and rendered directly in the UI.
- **Monitoring** – Health/config/logs/metrics pages in-app, plus Prometheus metrics and structured logs shipped to Grafana Cloud.

## Tech Stack

| Layer      | Technology                                                      |
|------------|-----------------------------------------------------------------|
| Backend    | Kotlin · Quarkus · Gradle                                       |
| Frontend   | Quarkus Qute (SSR) · Vanilla JS (fetch API) · Bootstrap 5 · SSE |
| Database   | MongoDB Atlas                                                   |
| Deployment | Docker Swarm · Traefik · VPS                                    |
| Monitoring | Grafana Cloud (Prometheus metrics + Loki logs)                  |

## Quick Start (Local Development)

**Prerequisites:** JDK 21+ and a MongoDB Atlas cluster.

1. Copy the required environment variables into a local `.env` file (see [arc42.md](docs/arc42/arc42.md) — *Deployment View* for the full list).
2. Start the application in dev mode with live reload:

```bash
./gradlew :application-quarkus:quarkusDev
```

## Documentation

| Document                                                                          | Description                     |
|-----------------------------------------------------------------------------------|---------------------------------|
| [Architecture (arc42)](docs/arc42/arc42.md)                                       | Full architecture documentation |
| [ADRs](docs/adr/)                                                                 | Architecture Decision Records   |
| [Release Notes](docs/releasenotes/RELEASENOTES.md)                                | Version history                 |
| [Coding Guidelines – Architect](docs/coding-guidelines/role-architect.md)         | Architectural conventions       |
| [Coding Guidelines – Backend](docs/coding-guidelines/role-backend-developer.md)   | Backend coding conventions      |
| [Coding Guidelines – Frontend](docs/coding-guidelines/role-frontend-developer.md) | Frontend coding conventions     |

## Building & Testing

```bash
# Full build (includes tests and static analysis)
./gradlew build

# Tests only
./gradlew test
```

## License

[MIT](LICENSE)
