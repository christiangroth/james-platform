plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}
