plugins {
  id("kotlin-project")
}

dependencies {
  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
