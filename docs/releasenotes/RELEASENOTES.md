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

