plugins {
    id("kotlin-project")
}

dependencies {
    api("com.github.glwithu06.semver:semver:1.0.1")

    testImplementation(testFixtures(project(":core")))
}
