plugins {
    id("kotlin-project")
}

dependencies {
    implementation(project(":core"))
    // TODO #32 remove dependency
    implementation(project(":core-app"))

    testImplementation(testFixtures(project(":core")))
}
