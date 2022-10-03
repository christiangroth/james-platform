plugins {
    id("kotlin-project")
}

dependencies {
    api("com.github.glwithu06.semver:semver:1.0.1")

    implementation(project(":core"))
    // TODO #32 remove dependency
    implementation(project(":core-user"))
    implementation("com.github.everit-org.json-schema:org.everit.json.schema:1.12.2")

    testImplementation(testFixtures(project(":core")))
}
