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

