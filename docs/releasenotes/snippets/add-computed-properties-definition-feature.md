* Developers can now define computed properties on entities.
* Computed properties have a name, a type (String, Boolean, Long, Double, Date, Time, DateTime, Duration), and an optional Kotlin script.
* The script is evaluated in the order of the computed properties and may access entity data via `it`, previously computed values via `computed`, and the current instant via `now`.
* Computed properties can be reordered via drag-and-drop in the version editor.
