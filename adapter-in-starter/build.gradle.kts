plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
}

dependencies {
  implementation(project(":domain-api"))
  implementation(libs.quarkusStartersDomainApi)

  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-mongodb-client")
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
}
