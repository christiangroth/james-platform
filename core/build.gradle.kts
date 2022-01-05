plugins {
    id("kotlin-project")
}

dependencies {
    api("com.github.glwithu06.semver:semver:1.0.1")
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.2.0")

    implementation("com.github.everit-org.json-schema:org.everit.json.schema:1.12.2")
    testImplementation("io.mockk:mockk:1.12.2")
}
