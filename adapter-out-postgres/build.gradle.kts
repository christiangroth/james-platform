plugins {
  id("kotlin-project")
}

// TODO manage version constants somehow
dependencies {

  // TODO deduplicate
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.18.3"))

  // Database
  api("io.quarkus:quarkus-jdbc-postgresql")
}
