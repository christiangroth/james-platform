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

