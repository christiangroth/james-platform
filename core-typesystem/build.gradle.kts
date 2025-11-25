plugins {
  id("kotlin-project")
}

dependencies {
  implementation(project(":core"))
  implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

  testImplementation(testFixtures(project(":core")))
}
