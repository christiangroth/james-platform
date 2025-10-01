plugins {
  id("kotlin-project")
}

// TODO manage version constants somehow
dependencies {
  implementation(project(":domain-user"))

  // TODO deduplicate
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.18.3"))

  // Database
  implementation("io.quarkus:quarkus-flyway")
  implementation("io.quarkus:quarkus-jdbc-postgresql")
  implementation("io.quarkus:quarkus-hibernate-orm")
  implementation("io.quarkus:quarkus-hibernate-validator")
}
