plugins {
  id("kotlin-project")
}

dependencies {
  api(libs.quarkusOutboxDomainApi)

  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
