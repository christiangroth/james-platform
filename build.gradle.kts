plugins {
    kotlin("jvm")
    id("net.researchgate.release") version "2.8.1"

    id("se.patrikerdes.use-latest-versions") version "0.2.16"
    id("com.github.ben-manes.versions") version "0.38.0"
}

repositories {
    mavenCentral()
    jcenter()
}
