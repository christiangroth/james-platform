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

