plugins {
  id("kotlin-project")
}

dependencies {
  api(project(":core"))

  // TODO deduplicate
  api(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.29.0"))
  api("io.quarkus:quarkus-arc")
  api("io.quarkus:quarkus-vertx")
}
