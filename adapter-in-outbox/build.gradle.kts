plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
}

dependencies {
  implementation(project(":domain-api"))
  implementation(libs.quarkusOutboxDomainApi)

  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
}
