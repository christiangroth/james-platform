plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.10")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.17.1")
}
