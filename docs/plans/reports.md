# Reports: declarative, low-code report definitions

**Status: plan, not yet implemented.** Elaborates the Reports epic (#364). The current `Report` model (`html` + `script`) and the
"Reports" section in [arc42](../arc42/arc42.md#reports) describe an earlier, code-based idea that this plan replaces.

## Motivation

The legacy james reports (15, used regularly) are fully custom code: each one loads *all* items of a type, filters them by hand
(up to 15 `.filter()` calls), computes its own derived data, and renders Highcharts and HTML itself. Running has ~73k characters of
JavaScript, Zählerstände ~59k. Analysing them shows the same building blocks over and over:

| Building block | Legacy reports | Today |
|---|---|---|
| Time series with zoom (line/column/area) | almost all | Highcharts code per report |
| Tables, overview tables with ratio coloring | Zählerstände, Arbeitszeiten, Konzerte, Urlaubsplaner | custom HTML |
| Tabs / selectors (year, employer) | 8 | custom HTML |
| Error messages (gap detection, cross checks) | Zählerstände, Arbeitszeiten, Journal | custom `errors` code |
| Filtering | all | load everything, filter client-side |
| Constants in code (goals, holidays, target hours, vacation days) | 7 | hard-coded |
| Derived data: cumulative, year over year, forecast, records, meter deltas, "value valid at date" | Running, Zählerstände, Arbeitszeiten, Lebensversicherung, Verdienst | hand-written |

Goal: a report is *declared*, Grafana-like, from queries, transformations, checks and panels that the platform executes and renders.
Code is the exception (a small Kotlin escape hatch), not the rule.

## Decisions

* **Definition format:** YAML, edited in the version editor with schema validation and a live preview against a test installation.
  A visual builder may follow later.
* **Execution:** queries, transformations and checks run backend-side; the browser only receives the panel data it renders. No
  Developer-authored JavaScript runs in the browser, which also settles the open sandboxing question from arc42.
* **Rendering:** a platform-provided renderer. Panels produce a renderer-neutral *panel spec* (series, axes, units, formatting); a
  single client-side adapter maps it to the chart library. The first adapter uses **Apache ECharts** (Apache-2.0, calendar heatmap,
  zoom, multiple axes); the library stays swappable by replacing the adapter only.
* **Escape hatch:** a transformation step may be a **Kotlin script** with a timeout, like computed properties (ADR 0008), for the
  few genuinely special calculations (e.g. the PV break-even). Kept deliberately small.
* **Scope boundary:** a report only reads data of its own App installation (plus its configuration, see below). Cross-app reports
  are out of scope for now - see "Out of scope".

## Model

```yaml
variables:
  zeitraum: { type: timeRange, default: last_12_months }
  jahr:     { type: select, query: jahre, field: jahr }
queries:
  laeufe:   { entity: Lauf, timeField: datum, range: $zeitraum,
              filter: [ { field: distanz, op: GREATER_THAN, value: 0 } ],
              fields: [datum, distanz, dauer, schuhe.name] }
  km_monat: { entity: Lauf, aggregate: { sum: distanz, bucket: MONAT, timeField: datum } }
transforms:
  laeufe: [ { calc: pace, expr: "dauer / (distanz / 1000)", unit: SECONDS } ]
checks:
  - gap: { entity: Lauf, timeField: datum, every: WEEK, message: "Keine Läufe in KW {week}" }
tabs:
  - name: Übersicht
    rows:
      - [ { stat: "Kilometer", query: km_monat, reduce: sum, compare: previous_period },
          { stat: "Ø Pace", query: laeufe, field: pace, reduce: avg } ]
      - [ { timeseries: "km pro Monat", query: km_monat, style: column, yoy: true } ]
      - [ { table: "Letzte Läufe", query: laeufe, limit: 25 } ]
```

### Queries

Named, executed backend-side so only the needed data leaves the server:

* one Entity, a filter (same rule model as import filters), an optional time range (fixed, relative, or a variable), fields incl.
  values reached through a `ref` (`schuhe.name`), sorting and a limit;
* optionally an aggregation: SUM/AVG/COUNT/MIN/MAX/LAST, grouped by time bucket (TAG/WOCHE/MONAT/QUARTAL/JAHR, with period start),
  by a `ref` or by a property - computed on the fly, or read from an existing `AggregationDefinition` when one matches;
* computed properties are available like any other field; units are carried along for formatting.

### Transformations

A per-query pipeline of predefined steps:

* calculated field via a small, safe expression language (`dauer / (distanz / 1000)`), not arbitrary code;
* cumulative sum, moving average, year over year (shifted series), forecast to period end (linear);
* delta/consumption from meter readings, resample with fill-forward;
* "value valid at date" (as-of join), e.g. a work day against the contract valid at that day;
* join two queries by key (e.g. pay slips and the tax certificate per year);
* records / top-N (best time per distance), goals vs. actuals;
* Kotlin script step (escape hatch), input and output are tables.

### Checks

Declarative error/warning rules, shown in a warnings panel and as a counter on the tab:

* gap detection: "one entry per weekday/week/day of Entity X", excluding dates from a calendar (configuration list or Entity);
* value rules (range, required combination of values).

### Layout and panels

Report → tabs → rows → panels. Panel types: stat (value, comparison, threshold colors), time series (line/column/area, several series
and axes, zoom), bar, pie, table (units, conditional colors), calendar heatmap, text (Markdown), warnings. Variables (time range,
selects fed by a query) are shown on top and can be referenced in query filters.

## App configuration

Constants move out of the code into data. Per App Version a Developer may define a **configuration**: a *singleton Entity*
(exactly one object per installation), defined like any Entity - typed properties, units, constraints, defaults, nested objects.
On install the object is created from the defaults; the User maintains it on a "Einstellungen" tab of the App. Reports, checks and
computed properties read it as `config.<property>`. Being an Entity, it reuses the generic data UI, validation, versioning and
migration steps.

Rule of thumb: settings without a time dimension (annual goal, holidays as a list of dates, thresholds) belong in the
configuration; values that change over time and are "valid from" (target hours, vacation entitlement) belong in a data Entity.

## Porting notes

* Arbeitszeiten (target hours) and Urlaubsplaner (vacation entitlement) need values that are "valid from" a date. They are stored at
  the employment contract today, which lives in the *Finanzen* App while work days and vacation live in *Arbeit*. Until the two
  Apps are merged (planned once the parallel operation with legacy james ends), *Arbeit* needs its own "valid from" Entity for
  these values, or the merge happens first.
* The Journal report's two cross checks against runs and bike rides are dropped for now (cross-app).

## Out of scope

* **Cross-app reports.** Only the Journal cross checks need them today. If needed later, the preferred direction is *personal
  dashboards*: reports owned by the User instead of an App, built with the same toolkit and reading the User's installed Apps
  directly - without published App interfaces/exports (too much complexity for the benefit).
* A visual report builder.

## Implementation steps

Each step is releasable on its own, in this order; after each step the matching legacy reports are ported to validate the toolkit.

1. **Reports (1/7): foundation** - YAML definition per App Version replacing `html`/`script`, schema validation, editor with
   preview, report page in the installation with tabs and rows, queries with filter and time range, table and text panels,
   renderer-neutral panel spec + ECharts adapter skeleton.
2. **Reports (2/7): time series and stat panels** - aggregation queries, units, stat comparison. Ports Body Metrics, Fahrrad,
   Rente, Blutwerte, Arbeitsverträge, Konzerte.
3. **Reports (3/7): App configuration** - singleton Entity, "Einstellungen" tab, `config.*` in reports and computed properties.
4. **Reports (4/7): variables and basic transformations** - time range and select variables, calculated field, cumulative,
   year over year, forecast. Ports Lebensversicherung, Walking Pad.
5. **Reports (5/7): checks and warnings panel** - gap detection with calendars, value rules.
6. **Reports (6/7): advanced transformations** - delta, as-of join, join, fill-forward, records and goals. Ports Zählerstände,
   Arbeitszeiten, Verdienst, Urlaubsplaner, Running.
7. **Reports (7/7): heatmap, pie and Kotlin step** - calendar heatmap and pie panels, Kotlin script transformation. Ports Journal
   (without the cross checks).
