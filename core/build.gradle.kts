plugins {
    id("kotlin-project")
}

dependencies {
    // TODO remove from API?
    api(libs.semverKt)

    testImplementation(testFixtures(project(":core")))
}
