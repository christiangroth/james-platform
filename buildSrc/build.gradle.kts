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
  implementation(libs.detekt)
  implementation(libs.kover)

  // releasenotes plugin
  implementation(libs.grgit)
}
