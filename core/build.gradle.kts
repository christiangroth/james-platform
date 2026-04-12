plugins {
  id("kotlin-project")
}

dependencies {
  testImplementation(testFixtures(project(":core")))
}
