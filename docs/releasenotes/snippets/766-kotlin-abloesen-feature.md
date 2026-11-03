* Berechnete Eigenschaften werden jetzt als Ausdruck geschrieben (z. B. `round(gewicht / (groesse / 100) ^ 2, 1)`), mit Ergebnistyp und optionaler Einheit, und funktionieren damit auch im Produktivbetrieb.
* Smart-Standardwerte sind Ausdrücke mit neuen Funktionen für die Erfassung: zuletzt verwendeter Wert `last(feld)`, Tag nach dem letzten Eintrag `next_day(feld)`, `end_of_month`, `add_days`, `now()` und Werktage `workdays(start, ende, config.feiertage)`.
* Smart-Standardwerte gibt es auch für Referenzen, z. B. die zuletzt verwendeten Laufschuhe.
* Smart-Standardwerte, die andere Felder lesen, rechnen im Formular mit, sobald sich diese Felder ändern (z. B. Urlaubsende = Start, Urlaubstage = Werktage).
* Ausdrücke werden beim Speichern geprüft, Fehler erscheinen mit verständlicher Meldung.
* Kotlin-Skripte werden nicht mehr unterstützt: Migrationsskripte und der Report-Schritt `kotlin` entfallen, gespeicherte Skripte werden im Editor als ungültig markiert.
* Der Bereich „Skripting“ im Monitoring entfällt.
