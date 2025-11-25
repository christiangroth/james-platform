plugins {
  id("kotlin-project")
}

dependencies {
  implementation(project(":core"))
  implementation(project(":core-typesystem"))
  implementation("com.github.everit-org.json-schema:org.everit.json.schema:1.12.2")

  testImplementation(testFixtures(project(":core")))
}
