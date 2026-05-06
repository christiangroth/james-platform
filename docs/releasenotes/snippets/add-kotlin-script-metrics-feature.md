* Added execution metrics (count, error count, total duration) for Kotlin smart default and computed property script evaluations.
* Prometheus metrics are exported under the `script.execution` metric with tags for type, entity, and property.
* Added Scripting section to the health UI overview, listing script executions categorised by type, entity, and property.
* Added configurable execution timeout (default 500 ms, `app.script.timeout-ms`) for all Kotlin script evaluations.
