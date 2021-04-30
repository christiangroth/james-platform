plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.20"
    `java-library`
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("com.github.glwithu06.semver:semver:1.0.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
