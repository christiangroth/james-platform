plugins {
    id("kotlin-project")
    id("io.quarkus") version "3.7.3"
}

dependencies {

    // Quarkus
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.7.3"))
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-resteasy")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.quarkus:quarkus-arc")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")

    // james-platform moduels
    implementation(project(":core"))
    implementation(project(":core-app"))
    implementation(project(":core-data"))
    implementation(project(":core-typesystem"))
    implementation(project(":core-user"))
    implementation(project(":core-workspace"))
}
