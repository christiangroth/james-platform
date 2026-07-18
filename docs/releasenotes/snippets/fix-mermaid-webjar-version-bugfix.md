* Fixed the build: the pinned mermaid WebJar version did not exist on Maven Central, breaking every build since the Mermaid diagram rendering was added.
* Fixed a flaky test failure caused by parallel test forks racing to bind application-quarkus's fixed test port; tests now run single-forked.
