plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    implementation("org.yaml:snakeyaml:1.21")
    implementation("com.squareup:kotlinpoet:1.5.0")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}
