plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
  implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.3")
  implementation("org.jetbrains.kotlinx:kover:0.6.1")

  // releasenotes plugin
  implementation("org.ajoberstar.grgit:grgit-core:5.3.3")
}
