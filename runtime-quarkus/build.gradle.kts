plugins {
    id("kotlin-project")
    id("io.quarkus") version "3.7.3"
}

dependencies {

    // Quarkus
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.7.3"))
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-resteasy")
    implementation("io.quarkus:quarkus-arc")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")

    // YAML parsing
    implementation("com.charleskorn.kaml:kaml:0.57.0")

    // james-platform modules
    implementation(project(":core"))
    implementation(project(":core-app"))
    implementation(project(":core-data"))
    implementation(project(":core-typesystem"))
    implementation(project(":core-user"))
    implementation(project(":core-workspace"))
}
