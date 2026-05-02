* Fixed deployment stability: Grafana Alloy container no longer crashes on startup due to invalid River syntax (hyphens in component labels are now underscores).
* Added health check for the Quarkus service in the Docker Swarm stack so deployments reliably wait for the application to be ready.
