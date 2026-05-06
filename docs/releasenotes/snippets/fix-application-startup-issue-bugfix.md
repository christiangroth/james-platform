* Fixed application not starting in Docker container.
* Fixed Kotlin scripting engine not found at runtime due to classloader isolation in Quarkus.
* Disabled Quarkus dev services in production (were incorrectly enabled globally).
* Removed unused HTTP_AUTH_ENCRYPTION_KEY from deployment configuration.
