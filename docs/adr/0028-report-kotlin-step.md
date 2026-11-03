# 0028 - Reports: Kotlin Script Step as Backend Escape Hatch

## Status

Accepted

## Context

Reports are declared in YAML and executed backend-side (arc42 "Reports"): queries, a fixed set of transformation steps (calculated fields,
running values, delta, as-of join, join, top-N, goals, ...), checks and panels. Analysing the 15 legacy james reports showed that the declarative
steps cover almost everything, but a few calculations do not fit any generic step - most prominently the PV break-even of Zählerstände (when do the
accumulated savings exceed the investment, extrapolated from the consumption so far) or projecting life insurance contributions to the contract end.
Adding a dedicated step for each of them would grow the step catalogue for one report each.

ADR [0008](0008-computed-property-script-execution.md) noted that Reports were then meant to run Developer code in the browser, so its timeout-only
model would not automatically transfer. That premise is gone: no Developer-authored code runs in the browser, the renderer only draws a
renderer-neutral panel spec built by the server.

## Decision

A transformation step may be a **Kotlin script**: `{ kotlin: <script>, fields: { <name>: <type or unit> } }`.

- **Execution** reuses the computed property model of ADR 0008: the JSR-223 Kotlin engine (`ReportScriptService`), each run on its own virtual
  thread bounded by `app.script.timeout-ms`, metered as script type `REPORT`. The step runs inside the query's pipeline on the server, after the
  steps before it.
- **Contract:** the script is the body of a function `(rows: List<Map<String, String?>>, config: Map<String, String?>, today: LocalDate) -> Any?`.
  `rows` are the result rows so far (raw storage values by field: numbers in the storage granularity, dates ISO, references as object ids, bucket
  keys), `config` the App configuration's values by field name (ADR 0027). Its last expression (or a `return`) is the new list of rows: an iterable
  of maps from field name to value; numbers, dates, booleans and texts are stored as raw values. `fields` declares the fields the script adds or
  retypes (LONG, DOUBLE, STRING, DATE, DATETIME, BOOLEAN or a granularity such as `CENTS` for a DOUBLE in that unit), so panels and later steps are
  validated against them like against any other step's output.
- **Failure handling:** an exception, a wrong result type or a timeout never fails the report: the query's result becomes empty with a German
  message, and every panel reading the query (directly or through `asOf`/`join`) shows that message instead of its data. The rest of the report
  renders normally.
- **Validation:** parsing checks the step's shape and declared fields on every validation (save, publish, execution). Compiling the script is
  expensive, so it only happens on save (`AppVersionManagementService`): the wrapping function is defined without calling it, so none of the
  Developer's statements run; compiler errors are reported as `KOTLIN_SCRIPT_INVALID` with the line within the script. A compile check that does
  not finish in time is skipped rather than reported.
- **Security model:** exactly the one of ADR 0008 - trusted Developers, no sandbox beyond the timeout. The script only receives the rows of its own
  query and the installation's configuration (the sandbox boundary of reports is drawn by loading only one installation's data), but it runs with
  full JVM access within its time window.

## Consequences

- The rare special calculations are expressible without growing the declarative step catalogue; the escape hatch is deliberately one step type
  with a plain data contract, so reports stay mostly declarative.
- Like computed properties, a report script compiles on every execution (no compiled-script cache); a slow cold start can hit the timeout, which
  shows as an error in the affected panels only.
- Scripts depend on raw storage values; a property changing its unit granularity (ADR 0016) changes what the script sees. Developers own that, as
  with computed properties.
- If Developer accounts ever become less trusted, this decision has to be revisited together with ADR 0008.
