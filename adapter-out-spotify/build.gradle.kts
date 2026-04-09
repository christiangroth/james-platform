plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
}

dependencies {
  api(project(":domain-api"))

  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-micrometer")
  implementation(libs.kotlinxSerializationJson)
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
}
