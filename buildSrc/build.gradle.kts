plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation(libs.kotlinGradlePlugin)
  implementation(libs.kotlinGradleSerializationPlugin)
  implementation(libs.kover)
  implementation("org.ajoberstar.grgit:grgit-core:5.3.0")
}
