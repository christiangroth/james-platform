* Entity properties in app version definitions may now define smart defaults: Kotlin script expressions that are evaluated when a user opens the "create new item" form, pre-filling field values automatically.
* Smart defaults have access to the accumulated data object (`it`) and current timestamp (`now`), enabling computed defaults such as today's date or derived values.
* Smart defaults are evaluated in property order, allowing later properties to reference earlier computed values.
