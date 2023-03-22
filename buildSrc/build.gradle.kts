plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    jcenter()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.17.1")
    implementation("com.xcporter:metaview:0.0.6")
    implementation("org.jetbrains.kotlinx:kover:0.6.1")
}
