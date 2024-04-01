plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
    implementation("org.jetbrains.kotlin:kotlin-serialization:1.9.20")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.3")
    implementation("com.xcporter:metaview:0.0.6")
    implementation("org.jetbrains.kotlinx:kover:0.6.1")

    // releasenotes plugin
    implementation("org.ajoberstar.grgit:grgit-core:4.1.0")
}
