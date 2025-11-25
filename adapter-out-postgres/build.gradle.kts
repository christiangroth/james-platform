plugins {
  id("kotlin-project")
}

dependencies {
  api(enforcedPlatform(libs.quarkusBom))
  api("io.quarkus:quarkus-jdbc-postgresql")
  api("io.quarkus:quarkus-flyway")
}
