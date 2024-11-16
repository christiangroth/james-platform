plugins {
    id("kotlin-project")
}

dependencies {
    api("com.github.glwithu06:semver.kt:1.0.1")

    testImplementation(testFixtures(project(":core")))
}
