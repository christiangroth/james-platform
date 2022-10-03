plugins {
    id("kotlin-project")
}

dependencies {
    // TODO #31 check
    api("com.github.glwithu06.semver:semver:1.0.1")
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.2.0")

    implementation(project(":core"))
    implementation("com.github.everit-org.json-schema:org.everit.json.schema:1.12.2")
}
