# 0.61.3 (2026.07.04)

## Bugfixes / Chore
* The admin area breadcrumb home icon now links directly to the admin dashboard, and the separate "Administration" breadcrumb entry was removed so admins can no longer accidentally navigate into the user area.
* Fixed a missing translation for the "Smart Default" badge in the system status scripting table.
* Added missing secret masking for additional TLS keystore and Slack webhook configuration keys.
* Fixed an error in the MongoDB Viewer when the total document count was very large.
* The version diff now shows all property details, including list item type, list item constraints, referenced entity and nested object properties.
* Fixed the version status badge (Draft/Published) not being translated.
* Property data types and constraints shown in the read-only property tables are now translated instead of showing raw internal values.
* Fixed untranslated hover texts on the "set password" and "delete user" action buttons in the admin user management page.
* Fixed several other untranslated breadcrumb and button labels caused by the same underlying template issue.



---

# 0.61.2 (2026.07.03)

## Bugfixes / Chore
* Fixed the browser tab icon not showing up on mobile Safari-based browsers (e.g. Firefox on iOS), which don't support SVG favicons, by adding PNG fallback icons.



---

# 0.61.1 (2026.07.03)

## Bugfixes / Chore
* Reordered the navigation bar: profile, language switch and theme switch are now left-aligned, while app store, tools and logout are right-aligned.
* The page background now stretches over the full page height without repeating, no matter the screen resolution.
* Login page now shows the app name, tagline and feature highlights on small screens too, not just on wider screens.
* Fixed several texts in the nested object property editor (data entry forms) that were shown in English instead of being translated.
* The breadcrumb navigation label is now translated instead of always showing the English word "breadcrumb".
* Admin accounts no longer have access to the App Store, in the navigation or directly via URL.
* Admin now always lands on the Admin Dashboard, and can no longer open the User Dashboard directly; non-admins can no longer open the Admin Dashboard directly.
* Deleting a user now shows a confirmation dialog inside the app instead of a browser popup.
* Health, logs, MongoDB viewer and configuration pages now use their own translation texts, separate from user administration.
* The app name "James Platform" shown in the browser tab title, the login page heading and the navigation logo now comes from the translation files instead of being hardcoded.
* Added a browser tab description and app name to the page metadata.



---

# 0.61.0 (2026.07.02)

## New Features
* Added a language switch to the navbar. German remains the default; the second option is an artificial "Underscore" test locale, generated automatically from the German texts, that replaces every letter and digit with `_` while keeping spaces and punctuation - useful for spotting texts that are not yet translatable.
* The chosen language is remembered in the browser (cookie) so it stays selected across visits.



---

# 0.60.1 (2026.07.02)

## Bugfixes / Chore
* Fixed a few remaining English texts and labels that were not yet translated to German (breadcrumb links, close buttons, version and diff page titles).
* Fixed the admin navigation menu showing inconsistent labels (e.g. "Health" instead of "Systemstatus") depending on which admin page was open.



---

# 0.60.0 (2026.07.02)

## New Features
* Translated the shared layout (navigation, footer), error page, and the admin area (dashboard, user management, health, logs, MongoDB viewer, configuration) into German.



---

# 0.59.0 (2026.07.02)

## New Features
* Translated the app store, app dashboard, app data forms, and profile pages into German.



---

# 0.58.0 (2026.07.02)

## New Features
* Added German as the default language, starting with the login page and the developer app/version/property management screens.



---

# 0.57.0 (2026.07.02)

## New Features
* Added the foundation for translated texts (i18n); the login page is the first page migrated to it.
* The login page (labels, headings, buttons, error messages) is now displayed consistently in German instead of a mix of English and German.



---

# 0.56.0 (2026.07.02)

## New Features
* The subtle gradient background from the login page is now used as the default background on every page.



---

# 0.55.1 (2026.07.01)

## Bugfixes / Chore
* Fixed the app logo/brand mark not rendering in light mode on some browsers.
* Removed the unused Spotify-green color and button style left over from the starting template.



---

# 0.55.0 (2026.07.01)

## New Features
* Added a new app icon/logo, used in the browser tab (favicon), the navbar, and the login page.
* Redesigned the login page with a landing-page-style layout: a short product introduction next to the login form on wider screens, and a subtle background instead of a flat color.



---

# 0.54.0 (2026.07.01)

## New Features
* Added a light theme alongside the existing dark theme.
* Added a theme toggle in the navbar to switch between light and dark mode.
* The app now follows your OS/browser color scheme by default, and remembers a manual choice for future visits.



---

# 0.53.5 (2026.07.01)

## Bugfixes / Chore
* The button for adding a new data entry now uses the primary action color for better visibility.



---

# 0.53.4 (2026.07.01)

## Bugfixes / Chore
* Improved contrast of borders and dividers on dark cards, tables and form controls for better readability.



---

# 0.53.3 (2026.07.01)

## Bugfixes / Chore
* Hid the browser's native number input up/down arrows so they no longer appear next to the custom step +/- buttons.



---

# 0.53.2 (2026.07.01)

## Bugfixes / Chore
* Breadcrumbs now only shorten when there isn't enough space to show every entry on one line, and only as many entries as needed are hidden behind the "…" instead of always collapsing down to home, "…" and the last two entries.



---

# 0.53.1 (2026.07.01)

## Bugfixes / Chore
* Breadcrumb truncation now only kicks in with more items, so wide breadcrumbs stay fully visible on desktop screens more often.
* Moved the Nested Properties section on the property editor page below the constraints and above the action buttons.



---

# 0.53.0 (2026.07.01)

## New Features
* Merged adding/editing a property and defining its nested structure (for Object properties) into a single, unified property editor view.
* The Object property editor now lets you add and edit nested properties directly, including further nesting, without leaving the page.



---

# 0.52.0 (2026.06.29)

## New Features
* The property editor and the version publish dialog are now dedicated pages with breadcrumb navigation, instead of modals.



---

# 0.51.1 (2026.06.28)

## Bugfixes / Chore
* The "add new entry" button on data entry tables now spans the full width of the table.



---

# 0.51.0 (2026.06.28)

## New Features
* Improved contrast of borders and table dividers in the dark theme, making tables and cards easier to read.
* Logout button now shows an icon and hides its text label on small screens to save space.



---

# 0.50.2 (2026.06.28)

## Bugfixes / Chore
* Fixed an issue where browsers could keep serving an outdated, cached version of JavaScript files (e.g. after an update) by appending a cache-busting version parameter to all custom script URLs.
* Nested OBJECT properties in data entry forms are now visually set apart with a bordered card, and the breadcrumb showing the current nesting level is more clearly highlighted.
* Moved the delete button for entities in the app version draft editor into the header row, now shown as an icon-only button.
* Aligned the buttons in the "New App" creation dialog to the right.
* Right-aligned the buttons on the "new data entry" form, with Cancel on the left and Save on the right.
* Replaced the round "add data" button with a standard button styled like the other action buttons.



---

# 0.50.1 (2026.06.28)

## Bugfixes / Chore
* Standardized button order and alignment across all pages and modals: Cancel/Back, then destructive (red), then save/positive (blue) actions.



---

# 0.50.0 (2026.06.28)

## New Features
* Data entry for Object properties now uses the same breadcrumb-based navigation as the developer area: nested structures are entered by drilling into a property, with a breadcrumb trail to navigate back, instead of deeply nested boxes.



---

# 0.49.0 (2026.06.28)

## New Features
* Breadcrumbs now collapse responsively on narrower screens, keeping the home icon and the last two entries visible and replacing hidden entries with "…".



---

# 0.48.0 (2026.06.28)

## New Features
* Object properties can now be edited by descending into them via breadcrumbs instead of a nested popup editor.
* Nested properties support the same constraints, defaults and other settings as top-level properties.
* Editing a nested property level now only shows its properties table; computed properties, sort order and display text are only available on the entity's top level.



---

# 0.47.4 (2026.06.28)

## Bugfixes / Chore
* Moved the "add data" button into the top row of the data table for a more compact display.
* Fixed step buttons on numeric fields so they actually add or subtract the configured step from the current value.
* Step buttons now respect configured minimum/maximum constraints and no longer go below or above them.



---

# 0.47.3 (2026.06.28)

## Bugfixes / Chore
* Fixed step increment/decrement buttons on numeric fields not responding to clicks when creating or editing objects.
* New properties in the developer area no longer default to "Nullable" being checked.
* Fixed the "Define structure" button for Object properties not opening the structure editor.
* Cleaned up the entity data table on the app detail page.
* Removed the table header row.
* Replaced the "add more data" link with a centered gray plus button below the table.



---

# 0.47.2 (2026.06.28)

## Bugfixes / Chore
* Duration property min/max constraints now accept the same textual format as duration values (e.g. `1d 2h 30m 15s` or `02:30:15`) instead of requiring ISO-8601 notation.



---

# 0.47.1 (2026.06.28)

## Bugfixes / Chore
* Fixed increment/decrement step buttons not showing up on numeric fields when editing existing data entries.



---

# 0.47.0 (2026.06.28)

## New Features
* Numeric input fields with a configured Step constraint now show plus/minus buttons and use the native HTML step behavior for entering values.



---

# 0.46.0 (2026.06.28)

## New Features
* Duration property values now show an input hint for the accepted text format (e.g. `1d 2h 30m 15s` or `02:30:15`).
* Duration values entered in app data and property defaults are now validated against this format and rejected if invalid.



---

# 0.45.0 (2026.06.28)

## New Features
* Added min/max constraints for date, time, datetime and duration properties.
* Added a step constraint for integer and decimal properties.
* The "Add Property" and "Edit Property" dialogs are now unified into a single dialog, so constraints, defaults, smart defaults and value proposals can be configured directly when creating a new property.



---

# 0.44.0 (2026.06.27)

## New Features
* Object properties can now define their own nested structure, including nested objects, and are validated recursively when entering or saving data.
* Reference properties nested inside object properties are now supported in data entry forms.



---

# 0.43.1 (2026.06.27)

## Bugfixes / Chore
* Replaced the pencil icon with a key icon on the "Set Password" action in the admin User Management table, to avoid suggesting that the whole user can be edited.



---

# 0.43.0 (2026.06.27)

## New Features
* Admin area now shows breadcrumb navigation for easier orientation.
* Removed the App Store and Profile links from the top menu in the admin area, since they are not relevant for administrators.



---

# 0.42.2 (2026.06.27)

## Bugfixes / Chore
* Fixed list properties losing their configured list item type after being saved and reloaded.



---

# 0.42.1 (2026.06.27)

## Bugfixes / Chore
* Fixed an issue where the list item type could not be properly set when editing a List property.
* The list item type is now required and must be set directly when creating a List property, just like the target entity for Reference properties.



---

# 0.42.0 (2026.06.27)

## New Features
* List properties can now define the type of their contained items (String, Long, Double, Date, Time, Datetime, Duration, Reference, Object) in the Developer area.
* For scalar item types, constraints for the individual list items can be configured alongside the list's own min/max length constraints.
* The configured list item type is now shown directly under the property's type, both in the developer area and in data entry forms.
* Data entry forms now support adding and removing multiple values for a list property, with each value validated against the configured item constraints on save.



---

# 0.41.1 (2026.06.27)

## Bugfixes / Chore
* The target entity for a Reference property is now selected directly when adding or editing the property, right below its type.
* The target entity is now mandatory for Reference properties; saving without one shows a validation error.



---

# 0.41.0 (2026.06.27)

## New Features
* Reference properties can now be configured with a target entity in the Developer area.
* Data entry forms now show a dropdown to select a reference to an existing entity instance, displayed using its configured Display Text or, if none is defined, its generic reference text.



---

# 0.40.0 (2026.06.27)

## New Features
* Added a generic reference text for objects without a display text, composed of entity name, ID, and the values of all unique properties.
* The reference text and the display text are now shown in the metadata panel of object views and edit views in app installations.

## Bugfixes / Chore
* Completed the project's CLAUDE.md by linking the test engineer guidelines and architecture decision records.



---

# 0.39.0 (2026.05.08)

## New Features
* Added a second Logs view that groups entries by class and log level.
* Logs now support switching between chronological and grouped views directly in the UI.



---

# 0.38.0 (2026.05.07)

## New Features
* Added a new Logs UI in the tools menu to view recent WARN and ERROR application logs.



---

# 0.37.0 (2026.05.06)

## New Features
* Added a new `monitoring` role that grants access to the Tools menu in the navigation bar.
* The Tools menu is now visible to users with the `monitoring` role in addition to the `admin` role.
* The user `chris` is automatically assigned the `monitoring` role on startup.
* A property can no longer have both a static default value and a smart default set at the same time.
* Properties can now be reordered via drag-and-drop in the version editor, giving developers control over the order in which smart defaults are evaluated.
* Smart default scripts now receive a pre-populated map that includes all static default values from other properties, allowing smart defaults to reference them.

## Bugfixes / Chore
* Updated frontend coding guide with complete table pattern, modal pattern, standard Qute tags reference, and shared JS utilities documentation.



---

# 0.36.0 (2026.05.06)

## New Features
* Computed properties are now displayed on the data object edit page as a collapsed panel below Metadata.
* Added execution metrics (count, error count, total duration) for Kotlin smart default and computed property script evaluations.
* Prometheus metrics are exported under the `script.execution` metric with tags for type, entity, and property.
* Added Scripting section to the health UI overview, listing script executions categorised by type, entity, and property.
* Added configurable execution timeout (default 500 ms, `app.script.timeout-ms`) for all Kotlin script evaluations.



---

# 0.35.1 (2026.05.06)

## Bugfixes / Chore
* Added missing MongoDB indexes on `app.developerId` and `app_user.username` to reduce slow queries.



---

# 0.35.0 (2026.05.06)

## New Features
* Developers can now define computed properties on entities.
* Computed properties have a name, a type (String, Boolean, Long, Double, Date, Time, DateTime, Duration), and an optional Kotlin script.
* The script is evaluated in the order of the computed properties and may access entity data via `it`, previously computed values via `computed`, and the current instant via `now`.
* Computed properties can be reordered via drag-and-drop in the version editor.
* App installations are now automatically upgraded when a new non-breaking version is published.
* Breaking version upgrades require manual confirmation by the user.
* Clicking the upgrade button now opens a confirmation dialog showing the old and new version numbers and the release notes before upgrading.
* Entity property editor now only shows the Default Value, Smart Default, and Value Proposals sections when they are applicable for the selected property type.



---

# 0.34.1 (2026.05.06)

## Bugfixes / Chore
* Application startup now creates MongoDB indexes for frequently queried fields, reducing query times.
* Smart default evaluation no longer recreates the Kotlin script engine on every form load.
* Loading a single installed app page no longer queries every installed app of the user.



---

# 0.34.0 (2026.05.06)

## New Features
* Developer dashboard now shows a breadcrumb trail (User Dashboard → Development) instead of a page heading.
* Developer dashboard link moved from the navigation bar to the user dashboard, displayed right-aligned next to the "My Apps" heading (visible only for users with the developer role).
* User dashboard heading changed to "My Apps"; installed app tiles now show data object counts per entity.
* App data view: "add more data ..." link added as the first row in the data table instead of a separate button above the table.
* Action buttons are now consistently placed at the bottom of pages and modals.
* Destructive actions (Delete) are left-aligned; positive actions (Save, Publish) and neutral actions (Cancel) are right-aligned.
* Delete button removed from entity and report table rows in the draft editor; use the dedicated edit view to delete.
* Property rows in the entity editor are now clickable to open the edit modal.
* Delete button added to the entity edit view, entity property edit modal, and report edit view.
* Delete and Publish buttons moved to the bottom of the draft version overview.



---

# 0.33.0 (2026.05.06)

## New Features
* String properties can now have value proposals configured.
* When editing or creating data, String fields with value proposals show autocomplete suggestions based on previously entered values.
* Value proposals can be filtered by other property values, so suggestions are context-sensitive.



---

# 0.32.1 (2026.05.06)

## Bugfixes / Chore
* Smart defaults (predefined date/time/datetime Kotlin scripts) are now reliably calculated when opening the "New" data form.
* Fixed a performance issue where the Kotlin scripting engine was re-initialised on every request, causing the form to load slowly.



---

# 0.32.0 (2026.05.06)

## New Features
* Predefined smart defaults are now available for DATE, TIME, and DATETIME property types in the version editor.
* DATE properties offer a "Today" predefined option that automatically fills in the current UTC date.
* TIME properties offer "Now", "Now (Current Second)", "Now (Current Minute)", and "Now (Current Hour)" options.
* DATETIME properties offer the same four predefined options as TIME.
* Predefined options appear as quick-fill buttons in the Edit Property modal and set the smart default script automatically.
* Clicking a data entry now opens the edit form directly instead of a separate read-only detail view.
* The delete button is now available directly in the edit form.

## Bugfixes / Chore
* Fixed application not starting in Docker container.
* Fixed Kotlin scripting engine not found at runtime due to classloader isolation in Quarkus.
* Disabled Quarkus dev services in production (were incorrectly enabled globally).
* Removed unused HTTP_AUTH_ENCRYPTION_KEY from deployment configuration.
* Property edit modal now shows validation errors (e.g. default value violating constraints) inline instead of hiding them behind the modal.
* Editing a property with a type that does not support default values (e.g. Reference, List, Object) no longer fails silently.



---

# 0.31.0 (2026.05.05)

## New Features
* Entity properties in app version definitions may now define smart defaults: Kotlin script expressions that are evaluated when a user opens the "create new item" form, pre-filling field values automatically.
* Smart defaults have access to the accumulated data object (`it`) and current timestamp (`now`), enabling computed defaults such as today's date or derived values.
* Smart defaults are evaluated in property order, allowing later properties to reference earlier computed values.



---

# 0.30.0 (2026.05.05)

## New Features
* Developer breadcrumb home icon now links to the User Dashboard.
* A 'Development' breadcrumb item linking to the Developer Dashboard has been added after the home icon on all developer sub-pages.



---

# 0.29.0 (2026.05.05)

## New Features
* Entity property definitions may now include a default value.
* Default values are validated against configured constraints when saved.
* The new item form is pre-filled with default values when creating data entries.

## Bugfixes / Chore
* Fix CI build: Grafana dashboard provisioning no longer blocks the pipeline.



---

# 0.28.1 (2026.05.05)

## Bugfixes / Chore
* Navigating back to an app's data view with a `?tab=` URL parameter now correctly pre-selects the matching entity tab on the server side.



---

# 0.28.0 (2026.05.05)

## New Features
* Sort order of app entities is now displayed as readable text (comma-separated property name and direction).
* Sort order can now be edited in a dedicated modal dialog, consistent with the display text editing pattern.
* Fixed HTTP 500 error when saving the sort order of an app entity.



---

# 0.27.0 (2026.05.05)

## New Features
* Each tab on the data page now has its own URL, so refreshing or sharing the link preserves the active tab.
* Navigating back from a data detail page or after creating/deleting a record now returns to the correct tab.



---

# 0.26.1 (2026.05.05)

## Bugfixes / Chore
* Inserting a placeholder token into the Display Text field now inserts at the cursor position (or end), not at the beginning.
* Publishing a draft version is now possible even when only the entity order was changed.
* The Sort Order field dropdown now shows all available properties even when no sort criteria have been defined yet.
* Removed the "Data" heading from the app detail page.
* Data table display text column header is now blank.
* Entity tab titles are no longer blue and tabs are more compact.
* The active entity tab is now remembered when navigating to and from the new data dialog.



---

# 0.26.0 (2026.05.05)

## New Features
* Deleting a data record now checks for references from other records via reference properties.
* Deletion is rejected if the record is referenced by a required (non-nullable) reference property in another record.
* If all referencing properties are nullable, those references are automatically cleared before deleting the record.
* The delete confirmation message reports how many references were cleared.



---

# 0.25.0 (2026.05.05)

## New Features
* Developer can reorder entities via drag-and-drop in the app version editor.
* Developer can define sort criteria (property and direction) per entity in the app version editor.
* User app data view shows separate tabs per entity type when multiple entities are defined; single-entity apps keep the flat view.
* Each entity tab has its own add button and displays data sorted according to the developer-defined sort order.
* Data tables support client-side pagination with 50 rows per page; navigation buttons are only shown when more than one page exists.



---

# 0.24.1 (2026.05.05)

## Bugfixes / Chore
* App version published view now shows constraint text representations (e.g. "min:0", "max:100") instead of constraint count in entity property rows.
* App version published view: HTML and Script labels for reports are now clearly readable.
* App version draft view: Display Text editing is now done via a modal; the template is shown as plain text with an edit button.
* App version draft view: Entity properties table now shows constraint text per line instead of a count badge; shows "n/a" when no constraints are defined.
* App version draft view: Entity overview table now shows property names (comma-separated) instead of a count badge.
* App version draft view: Entity and report table rows are now fully clickable (no blue link text).



---

# 0.24.0 (2026.05.05)

## New Features
* Display text placeholders now use property names instead of internal IDs, making templates more readable for developers.
* Already used property tokens are hidden from the placeholder badge list in the display text editor.
* Unknown property references in display texts are rendered as `<?>` at runtime.
* Publishing a version is now blocked when any entity's display text references unknown or nullable properties.



---

# 0.23.0 (2026.05.05)

## New Features
* Developers can now define a display text template for each entity.
* The template may include the entity ID and any non-nullable property as clickable badge placeholders.
* The computed display text is shown in the user app data table.



---

# 0.22.1 (2026.05.04)

## Bugfixes / Chore
* Delete report action now uses a confirmation modal instead of the browser's native confirmation dialog.
* Constraint violations during app data creation and editing now show specific error messages per field instead of only a generic message.
* Field-level constraint errors are displayed persistently next to the affected input, while the generic error banner continues to auto-dismiss.



---

# 0.22.0 (2026.05.04)

## New Features
* App data entries are now clickable and navigate to a detail view.
* The detail view shows all properties and metadata (ID, entity type, version, timestamps).
* An edit button allows updating property values directly from the detail view.
* A delete button with confirmation allows removing a data entry.



---

# 0.21.0 (2026.05.04)

## New Features
* Added app data list view to installed app detail page, showing stored objects with ID, entity type, and last modified date.
* Added an Add button to create new data entries for an installed app's entities.
* If an app has only one entity, the Add button opens the form directly; otherwise a dropdown lets you choose the entity.
* A generic form generates inputs for all entity properties and validates constraints before saving.
* App data is persisted in MongoDB in the `app_data` collection.



---

# 0.20.0 (2026.05.04)

## New Features
* Replaced text-only edit, add, delete, and publish buttons in developer UIs with consistent icon-only buttons (pencil, plus, trash, upload).
* Merged the separate "Edit Property" and "Edit Constraints" actions into a single combined modal.
* Removed the diff button from the draft version header; removed delete-draft and publish-version buttons from entity and report edit views.
* Applied the new reusable button components in the admin user management area.



---

# 0.19.1 (2026.05.03)

## Bugfixes / Chore
* Fixed entity property Edit and Constraints modals not opening in the draft version editor.



---

# 0.19.0 (2026.05.03)

## New Features
* Entity property type is now shown as plain text with a `?` suffix for nullable properties (e.g. `STRING?`).
* The separate Nullable column has been removed from entity property tables.
* Editing a property or its constraints now correctly closes the modal and reloads the view on success.



---

# 0.18.0 (2026.05.03)

## New Features
* Add edit button and modal on App UI to update name and description.
* Remove status badge from App UI and App List UI.
* Add Draft badge and latest version with publish date to App List UI.
* Change entity property action buttons to small blue buttons, red for delete, in Draft Version UI.
* Show entity properties table always expanded (non-collapsible) in Published Version UI.



---

# 0.17.0 (2026.05.03)

## New Features
* Deleting an entity now shows a confirmation modal instead of a browser alert.
* Removing a property now shows a confirmation modal instead of a browser alert.
* Adding a new property now uses a modal dialog instead of an inline form below the properties table.

## Bugfixes / Chore
* App version view: entity and report accordion items are now expandable.
* App version view: removed the published status badge and read-only banner.
* App draft version view: removed the draft status badge, version number placeholder, and creation date.



---

# 0.16.0 (2026.05.03)

## New Features
* Tables scroll horizontally on narrow screens (smartphone-friendly).
* Fixed invisible text on dark background caused by undefined CSS variable.
* Added standard CSS component classes for modals, accordions, and breadcrumb links.
* Removed scattered inline styles; all dark-theme components now use shared CSS classes.
* Updated coding guidelines with navigation/breadcrumb concept and mobile-first quality standard.



---

# 0.15.0 (2026.05.02)

## New Features
* App store app detail page now shows the installation status or install button between the app info and the latest version info.



---

# 0.14.0 (2026.05.02)

## New Features
* App Store detail page now shows a version history section with release notes for each published version, sorted by most recently published.
* Draft versions now show a Diff button, allowing comparison against the latest published version from the app overview and the version editor.



---

# 0.13.0 (2026.05.02)

## New Features
* The red delete button is no longer shown on draft versions in the app versions list; deletion is only available from within the draft version editor.
* A Diff button is now shown for released versions that have a predecessor version, allowing comparison of entity and report changes between releases.
* The diff view highlights added lines in green and removed lines in red, using a DSL representation for entities and line-based comparison for reports.
* App Store pages now use a consistent breadcrumb navigation with a home icon, matching the rest of the developer and user views.



---

# 0.12.1 (2026.05.02)

## Bugfixes / Chore
* Fixed deployment stability: Grafana Alloy container no longer crashes on startup due to invalid River syntax (hyphens in component labels are now underscores).
* Added health check for the Quarkus service in the Docker Swarm stack so deployments reliably wait for the application to be ready.



---

# 0.12.0 (2026.05.02)

## New Features
* Replace back-to-dashboard button with a house icon breadcrumb in user and developer views.
* Add breadcrumb navigation with "/" separation across all app views, showing entity and report names.
* Remove the sideboard from the Developer App Draft view for better mobile support.
* Show entity and report lists as alphabetically sorted tables in the Draft version view.
* Add new entity or report via a small name-input modal instead of an inline form.
* Creating a new draft version now navigates directly to the editor without showing a modal.
* Draft versions can now be deleted with a confirmation modal (button next to Publish).
* Fix table text colors to avoid black text on dark backgrounds.



---

# 0.11.0 (2026.05.02)

## New Features
* App Store link added to the navigation menu, to the left of the developer dashboard link.
* App Store tile removed from the user dashboard.
* App versions are now sorted by release date, with the newest on top and the draft version always first.
* Release notes are now required when publishing a new version.
* Release notes are shown in the version list and the version detail view.
* Publishing is no longer possible when there are no changes in entities or reports compared to the previous version.
* Fixed the version type selection buttons incorrectly displaying `:null` instead of the version number.
* The version detail view now shows all entity properties and reports in expandable accordions.



---

# 0.10.2 (2026.05.02)

## Bugfixes / Chore
* Fixed app store not showing developer name correctly.
* Fixed dashboard error when displaying installed apps.



---

# 0.10.1 (2026.05.02)

## Bugfixes / Chore
* Fixed app store and dashboard version number display error.



---

# 0.10.0 (2026.05.01)

## New Features
* Users now have a unique real identifier (UUID) in addition to their username.
* Username changes no longer require re-authentication after the change.
* App collection renamed for consistency; all existing apps are removed as part of migration.
* App version collection renamed for consistency.
* Developer apps now reference the developer's unique user identifier instead of their username.



---

# 0.9.0 (2026.05.01)

## New Features
* App version entity editor now supports editing existing properties to change their name, type and nullability.
* App version entity editor now supports managing constraints per property (unique key, min/max values, length limits, patterns, size limits).

## Bugfixes / Chore
* Fixed publish dialog for the first app version: no bump type selection is required when publishing the first version of an app.



---

# 0.8.0 (2026.04.18)

## New Features
* Version numbers are now assigned automatically on publish using semver rules.
* When publishing without breaking changes, a choice between feature and bugfix release type is presented.
* Breaking changes in a draft version are now highlighted with a red badge in the version editor.



---

# 0.7.0 (2026.04.18)

## New Features
* Added `DURATION` as a new property type for durations. UI input format is `[[h:]m:]s`.
* User dashboard now shows an App Store tile as the first tile, followed by one tile per installed app showing the app name and version.
* If a newer version of an installed app is available, an upgrade button is shown on the dashboard tile.
* Clicking an installed app tile leads to the app's page.
* New App Store page lists all available apps ordered by name, showing name, version, and developer.
* App Store detail page shows app info, current version release notes, entities, and reports; an install button is shown if the app is not yet installed.



---

# 0.6.2 (2026.04.18)

## Bugfixes / Chore
* Fixed entity editor UI crashing with a template error when displaying properties.
* Report editor now shows a single page editor with a save button.



---

# 0.6.1 (2026.04.18)

## Bugfixes / Chore
* Removed the outbox feature and related UI (outbox viewer page, health page outbox section).



---

# 0.6.0 (2026.04.15)

## New Features
* App versions are now created without a version number (as a draft).
* Version numbers are assigned when publishing a draft version.
* New publish workflow includes automatic version bump suggestions.
* Added entity editor: add and remove entities and properties in draft versions.
* Added report editor: add and remove reports and pages in draft versions.



---

# 0.5.0 (2026.04.15)

## New Features
* Each app is now associated with the developer who created it.
* Developers can only see and manage their own apps on the developer dashboard.



---

# 0.4.0 (2026.04.15)

## New Features
* Developer Dashboard: new page listing all your apps, with a tile per app and a "New App" shortcut tile.
* App Overview: per-app page listing all versions, with a "New Version" shortcut tile when no unpublished version exists.
* Version Editor: draft versions show a left vertical navigation menu with entries for each entity and report; published versions are read-only.



---

# 0.3.0 (2026.04.15)

## New Features
* Introduce apps domain with support for apps and app versions.
* Apps can be created, updated, and deleted; names must be unique.
* App versions follow semantic versioning and progress through draft, published, and deprecated states.



---

# 0.2.0 (2026.04.15)

## New Features
* Ensure only one user can have the admin role at a time.
* After login, admin users are redirected to the admin dashboard. All other users are redirected to the user dashboard.
* Users with the developer role see a coding icon in the navigation bar linking to the developer dashboard.
* Updated README with project description and features overview.
* Password confirmation field removed from the "Set Password" dialog in user management.



---

# 0.1.22 (2026.04.14)

## Bugfixes / Chore
* Fixed user management page: create user, set password and manage roles actions now work correctly.
* Fixed user management page: status badge and role changes now show feedback and re-render the user table after changes.
* Fixed user management page: set password modal now correctly displays the username in the header.



---

# 0.1.21 (2026.04.14)

## Bugfixes / Chore
* Improved user management: username in "Set Password" dialog is now shown in the title instead of a readonly form field.
* User management action buttons replaced with icons: key icon for Set Password, trash icon for Delete.
* Fixed user table refresh after status toggle and role changes to always fetch fresh data.



---

# 0.1.20 (2026.04.14)

## Bugfixes / Chore
* Removed app title text from the navigation bar.



---

# 0.1.19 (2026.04.14)

## Bugfixes / Chore
* Version is now automatically bumped to the correct semver level (minor for features, major for breaking changes) based on snippet types when releasing, even when snippets were created manually.



---

# 0.1.18 (2026.04.14)

## Bugfixes / Chore
* Removed outbox status badge from the navigation bar.



---

# 0.1.17 (2026.04.14)

## Bugfixes / Chore
* Removed unused OAuth redirect URI configuration parameter.
* Masked HTTP auth encryption key in the Config UI.



---

# 0.1.16 (2026.04.14)

## Bugfixes / Chore
* Fixed Grafana logs dashboard provisioning by removing the numeric id from the dashboard definition.



---

# 0.1.15 (2026.04.14)

## New Features
* Extended arc42 documentation with requirements overview, quality goals, architecture constraints, context and scope, solution strategy, and glossary.



---

# 0.1.14 (2026.04.14)

## New Features
* Status badge on user management page is now clickable to toggle active/inactive status.
* Role badges on user management page are now clickable to open the manage roles dialog.
* User actions replaced by two dedicated buttons: set password and delete.
* User management actions now show immediate feedback with messages that auto-dismiss after 5 seconds.
* Users table refreshes in place after each action without a full page reload.



---

# 0.1.13 (2026.04.14)

## New Features
* Changed application logo to a stylized person in a dark circle, inspired by the GitHub logo style.



---

# 0.1.12 (2026.04.14)

## Bugfixes / Chore
* Fixed striped table text color in dark theme so all rows display readable light text.



---

# 0.1.11 (2026.04.13)

## New Features
* User management table now shows "Since" column before "Last Login".
* User management actions button now displays a gear icon instead of text.
* User management actions dropdown no longer clips inside the scrollable table area.
* User management table is now striped for better readability.



---

# 0.1.10 (2026.04.13)

## New Features
* Admins can now assign roles (USER, DEVELOPER, ADMIN) to users from the User Management UI.
* Added password confirmation field when setting a user's password in the User Management UI.

## Bugfixes / Chore
* Fixed HTTP 405 error when creating or deleting users in the User Management UI.
* Fixed HTTP 405 error when changing username or password in the Profile UI.



---

# 0.1.9 (2026.04.12)

## Bugfixes / Chore
* Session cookies are now persistent and survive browser restarts (14-day lifetime).
* Session cookies are automatically renewed when the user is active within the last 5 days before expiry, keeping active users logged in without interruption.



---

# 0.1.8 (2026.04.12)

## Bugfixes / Chore
* Fixed user management page not rendering usernames correctly.



---

# 0.1.7 (2026.04.12)

## New Features
* Added a custom HTML error page that displays the exception type, message, and stack trace when an unhandled error occurs.

## Bugfixes / Chore
* Fixed user count on admin dashboard not being readable due to poor text contrast on the dark card background.



---

# 0.1.6 (2026.04.12)

## New Features
* Added user administration to the admin dashboard: the greeting has been replaced by a user count tile that links to the user management page.
* User management page shows all users with their status, last login, and creation date, plus an action menu to activate, deactivate, set password, or delete users.
* New users can be created by admins directly from the user management page.
* The profile icon in the navigation menu has been moved to the left of the Tools dropdown.
* The Tools dropdown in the navigation menu is now only visible to admin users.



---

# 0.1.5 (2026.04.12)

## New Features
* Introduced basic dark-theme CSS component classes: blue primary action buttons, red destructive buttons, muted secondary buttons, dark form controls and select dropdowns, and status badge helpers.
* Applied consistent button and form styling across login, profile, MongoDB viewer, and outbox viewer pages.

## Bugfixes / Chore
* Fixed white page displayed after login by using the correct HTTP 303 redirect, so the browser navigates to the dashboard with a GET request.



---

# 0.1.4 (2026.04.12)

## New Features
* Add profile page for authenticated users to view and change their profile details.
* Users can change their username (must be unique) and password from the profile page.
* Profile page shows read-only account metadata: creation date and last login time.
* Last login time is now recorded in the database on each successful login.



---

# 0.1.3 (2026.04.12)

## New Features
* Added a butler app icon (serving tray with bow tie) used as favicon, in the navigation bar, and on the login page.



---

# 0.1.2 (2026.04.12)

## Bugfixes / Chore
* Slack notifications now report as James Platform instead of SpCtl.
* Page titles in admin/developer pages now consistently show James Platform instead of SpCtl.



---

# 0.1.0 (2026.04.12)

## Bugfixes / Chore
* Added `copilot-setup-steps.yml` workflow to pre-install Java 25 and Gradle dependencies for the Copilot agent environment.
* Updated in-app documentation to reflect current project state.
* Fixed module overview, authentication description, and error code examples in arc42.
* Corrected SSE event list and application name in coding guidelines.



---

# 0.1.0 (2026.04.12)

## New Features
* Rebranded application from SpCtl to James Platform.
* Changed authentication from Spotify OAuth to local username/password login.
* Admin user is automatically created on first startup with a random password sent to Slack.
* Added user role support: USER, DEVELOPER, ADMIN.
* Login redirects to a role-specific dashboard (/ui/{role}/dashboard).
* Updated navigation links: GitHub, Grafana, and MongoDB Atlas point to new James Platform resources.
* Removed Spotify API documentation link from navigation.
* Replaced Spotify icon with a question mark placeholder icon.

## Bugfixes / Chore
* Removed obsolete migration and cleanup starters.
* Removed playback event viewer and aggregation features.
* Renamed cookie auth mechanism class to `CookieAuthMechanism`.
* Simplified MongoDB index initializer.



---

