# Plan: UI zum einmaligen/periodischen Ausführen bestehender Import-Definitionen

Bezieht sich auf issue #665, Punkt "es fehlt die Ui um den Import einmalig oder periodisch
auszuführen". Reiner Plan, noch nicht umgesetzt.

## Ist-Zustand

- `ImportDefinition` (domain-api) trägt bereits `schedule: String?` (Cron), `lastRunAt: Instant?`,
  `lastKnownSchema` und `notifyOnSlack` — eingeführt in Import Job Reuse (2/3) und (3/3) (#667, #663).
- `ImportPort.updateSchedule(userId, definitionId, schedule)` ist implementiert und getestet, aber
  an **keinem** `@Path`-Endpunkt verdrahtet.
- `ImportPort.triggerScheduledImport(definitionId)` (kein `userId`-Parameter) wird ausschließlich vom
  Cron-Poller (`ImportDefinitionScheduleJob` → `ImportScheduleService`) aufgerufen und führt Fetch →
  Filter → Mapping → Dry-Run → Auto-Accept unbeaufsichtigt aus (`ImportTrigger.SYSTEM`).
- Es gibt **keine eigene Definitionen-Ansicht**. Definitionen werden aktuell nur indirekt über die
  Job-Tabelle (`imports.html`, `UserImportResource.kt`) sichtbar, indem `ImportJobRow` Felder aus der
  zugehörigen `ImportDefinition` einblendet (Quelle/URL-Postfix seit #669).
- Da ein `ImportJob` nach `acceptDryRun` gelöscht wird (ADR [0021](../adr/0021-import-definition-job-split.md)),
  verschwindet nach dem Accept auch die einzige bisherige Detailseite (`/ui/user/imports/{jobId}/...`).
  Eine spätere Definition lässt sich über die heutige UI **gar nicht mehr wiederfinden**, geschweige
  denn erneut ausführen oder mit einem Zeitplan versehen.
- Der zweite Teil des Issue-Punkts — "Dadurch wird sich nicht klar dass man bis zum dry run
  eigentlich nicht den Job bearbeitet sondern die Import Definition an sich" — ist ein UX-Klarheits-
  problem: der Wizard (`import-filter.html`, `import-mapping.html`) spricht durchgehend von "Import"/
  "Job", obwohl Filter und Mapping laut ADR 0021 tatsächlich auf der `ImportDefinition` gespeichert
  werden.

## Zielbild

Eine neue Ansicht **"Import-Definitionen"**, analog zu `import-connections.html`, unter
`/ui/user/imports/definitions`, erreichbar über einen Header-Button in `imports.html` (neben dem
bestehenden "Connections"-Link). Pro Definition:

- **"Jetzt ausführen"** — führt die Definition einmalig mit ihrer gespeicherten Konfiguration aus,
  ohne den Wizard erneut zu durchlaufen.
- **Zeitplan bearbeiten** — Cron-Ausdruck setzen/ändern/löschen (Pause = `schedule = null`) plus
  Slack-Benachrichtigung an/aus (`notifyOnSlack`).
- Anzeige von Quelle (Connection-Name + URL-Postfix, wie in der Job-Tabelle), Ziel-App/-Entity,
  aktueller Zeitplan (Cron-String oder "manuell"), letzter Lauf (`lastRunAt`).

## Offene Entscheidungen (bitte vor Umsetzung klären)

1. **Verhalten von "Jetzt ausführen":** unbeaufsichtigtes Auto-Accept wie beim Cron-Trigger
   (konsistent mit `triggerScheduledImport`, nur mit `ImportTrigger.USER` statt `SYSTEM`), oder soll
   der Nutzer noch einmal den Dry-Run-Report vor dem Accept sehen? Vorschlag: Auto-Accept, weil die
   Definition per Definition bereits einmal interaktiv bis zum Accept durchlaufen wurde — sonst
   bräuchte man wieder den vollen Wizard. Schema-Drift-Guard greift ohnehin als Sicherheitsnetz.
2. **Löschen von Definitionen:** `ImportPort` hat aktuell keine `deleteImportDefinition`-Methode;
   Definitionen akkumulieren dauerhaft (siehe ADR 0021, "Negative Consequences"). Eine eigene
   Definitionen-Übersicht macht das sichtbar. Im Scope dieses Plans mit aufnehmen (Löschen-Button,
   analog zur Connections-Seite) oder bewusst als separates Follow-up zurückstellen?
3. **"Nächster Lauf"-Anzeige:** `ImportDefinition` speichert kein `nextRunAt`; `CronSchedule`
   (domain-impl) kennt nur `isValid`/`isDue`. Für eine reine Anzeige bräuchte es eine zusätzliche
   `nextFireTime(schedule, after)`-Hilfsfunktion. Nice-to-have, nicht blockierend — kann in Teil 2
   entfallen und später nachgezogen werden.
4. **Wizard-Klarheit:** reicht es, in `import-filter.html`/`import-mapping.html` einen Hinweistext
   ("diese Einstellungen werden für spätere Läufe wiederverwendet") zu ergänzen, oder soll
   Terminologie/Breadcrumb konsequent auf "Import-Definition" umbenannt werden? Vorschlag: kleiner,
   nicht-invasiver Hinweistext statt Umbenennung, um den bestehenden Wizard-Flow (Job-zentriert bis
   zum Accept) nicht zu verwirren.

## Vorgeschlagene Umsetzung, in zwei unabhängig releasbaren Schritten

### Import-Ausführung UI (1/2): Backend-Endpunkte

- `ImportPort`: neue Methode `triggerDefinitionRun(userId: String, definitionId: String): Either<DomainError, ImportJob>`
  — teilt die Kernlogik mit `triggerScheduledImport` (Fetch/Filter/Mapping/Schema-Drift-Guard/
  Auto-Accept), ergänzt um Ownership-Check und `ImportTrigger.USER`. Kein Duplizieren der
  `ImportService`-internen Logik, sondern Extraktion einer gemeinsamen private Methode, die beide
  Trigger-Pfade aufrufen.
- Neue REST-Endpunkte auf `UserImportResource.kt` (oder ausgelagert in eine eigene
  `UserImportDefinitionResource.kt`, analog zu `UserImportConnectionResource.kt`):
  - `GET /ui/user/imports/definitions` — Seite + Tabellen-Fragment (analog `imports.html`/`/table`).
  - `POST /ui/user/imports/definitions/{id}/run` — ruft `triggerDefinitionRun` auf.
  - `POST /ui/user/imports/definitions/{id}/schedule` — Wrapper um `updateSchedule` (Form-Felder
    `schedule`, `notifyOnSlack`); leeres `schedule` löscht den Zeitplan (Pause).
  - Optional (siehe offene Entscheidung 2): `POST /ui/user/imports/definitions/{id}/delete`.
- Neue Domain-Fehlercodes sind nicht nötig — `DEFINITION_NOT_FOUND`, `INVALID_CRON_SCHEDULE`,
  `DEFINITION_NOT_CONFIGURED` existieren bereits (`domain-api/.../error/DomainError.kt`).
- Tests: neue `ImportServiceTests`-Fälle für `triggerDefinitionRun` (Erfolg, fremder User, fehlende
  Konfiguration, Schema-Drift-Abbruch — gespiegelt von den bestehenden `triggerScheduledImport`-Tests),
  neue `UserImportResourceTests` für die Endpunkte.
- Für sich release-fähig, ohne UI-Konsument — gleiches Muster wie `updateSchedule` in #667, das
  bewusst "ready for a future UI issue to consume" ausgeliefert wurde.

### Import-Ausführung UI (2/2): Neue Seite "Import-Definitionen"

- Neues Template `import-definitions.html` (Struktur/JS-Stil an `import-connections.html` und die
  bestehenden `imports.html`-Patterns angelehnt: `showMessage`, `fetchAction`/`fetchForm`,
  Tabellen-Fragment-Refresh).
- Header-Button in `imports.html` neben dem Connections-Link, der auf `/ui/user/imports/definitions`
  verlinkt.
- Tabelle mit den oben genannten Spalten, "Jetzt ausführen"-Button (mit Ladezustand/Deaktivierung
  während der Anfrage, Erfolg/Fehler-Feedback wie beim bestehenden "URL testen"-Muster) und
  Zeitplan-Modal (Cron-Eingabe als Textfeld + Format-Hinweis + `notifyOnSlack`-Checkbox; Validierung
  server-seitig über `CronSchedule.isValid`, Fehleranzeige über das bestehende
  `showImportModalMessage`-Muster).
- Kleiner Hinweistext in `import-filter.html`/`import-mapping.html` gemäß offener Entscheidung 4.
- Neue i18n-Keys in `messages/user_de.properties` (und ggf. weiteren Sprachdateien) für Titel,
  Spaltenköpfe, Button-Labels, Zeitplan-Modal-Texte, Erfolgsmeldungen.
- Neue `*_de.properties`-Keys und Playwright/UI-Tests (falls vorhanden, analog zu bestehenden
  Import-UI-Tests) für: Tabelle rendert Definitionen, "Jetzt ausführen" löst Lauf aus, Zeitplan
  setzen/löschen, ungültiger Cron-Ausdruck zeigt Fehler.

## Doku-Folgearbeiten (im Rahmen von Teil 2)

- `docs/arc42/arc42.md`, Abschnitt "Data Import (ETL)": neuen Absatz zur Definitionen-Übersicht und
  zum manuellen/periodischen Re-Run ergänzen (aktueller Stand beschreibt nur Job-Wizard + Cron-Poller,
  nicht die geplante UI).
- Kein neues ADR nötig, sofern Entscheidung 1 (Auto-Accept beim manuellen Re-Run) wie vorgeschlagen
  konsistent zum bereits in ADR 0021/#667 beschriebenen Verhalten bleibt. Falls stattdessen ein
  interaktiver Review-Schritt gewählt wird, sollte das kurz in ADR 0021 als Ergänzung oder eigenes
  ADR festgehalten werden, da es von der bisherigen Cron-Semantik abweicht.
- Release-Note-Snippet (`docs/releasenotes/snippets/`) pro Teilschritt, Typ `feature`.

## Vorschlag für Issue-Zuschnitt

Falls gewünscht, als zwei Folge-Issues anlegen, gemäß Namenskonvention:

- `Import-Ausführung UI (1/2): Backend-Endpunkte für einmaliges/periodisches Ausführen bestehender Import-Definitionen`
- `Import-Ausführung UI (2/2): Neue Übersicht "Import-Definitionen" mit Ausführen- und Zeitplan-UI`

Beide referenzieren #665 und sind, wie oben beschrieben, unabhängig voneinander mergbar (Teil 1 vor
Teil 2 sinnvoll, aber Teil 1 allein ist bereits sicher release-fähig).
