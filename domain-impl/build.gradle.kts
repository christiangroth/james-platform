plugins {
  id("kotlin-project")
}

dependencies {
  api(project(":domain-api"))

  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-micrometer")
  implementation("io.quarkus:quarkus-vertx")
}
