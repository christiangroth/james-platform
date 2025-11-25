import io.gitlab.arturbosch.detekt.Detekt
import kotlinx.kover.api.CounterType
import kotlinx.kover.api.VerificationTarget
import kotlinx.kover.api.VerificationValueType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9

plugins {
  kotlin("jvm")

  `java-library`
  `java-test-fixtures`

  id("io.gitlab.arturbosch.detekt")
  id("org.jetbrains.kotlinx.kover")
}

repositories {
  mavenCentral()
  maven {
    this.name = "Jitpack.io"
    url = uri("https://jitpack.io")
  }
}

// Access the version catalog
val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
  implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(libs.findLibrary("kotlinxCoroutines").get())
  implementation(libs.findLibrary("kotlinLogging").get())

  api(libs.findLibrary("arrow").get())

  testImplementation(libs.findLibrary("assertJ").get())
  testImplementation(libs.findLibrary("junit").get())
  testImplementation(libs.findLibrary("mockk").get())

  testFixturesImplementation(libs.findLibrary("assertJ").get())
  testFixturesImplementation(libs.findLibrary("junit").get())
  testFixturesImplementation(libs.findLibrary("mockk").get())
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(files("${rootProject.projectDir}/detekt-config.yaml"))
}

tasks {

  withType<Detekt> {
    this.jvmTarget = "17"
  }

  kotlin {
    compilerOptions.apiVersion = KOTLIN_1_9
    compilerOptions.languageVersion = KOTLIN_1_9
    compilerOptions.jvmTarget = JVM_17
    compilerOptions.allWarningsAsErrors = false // TODO enable again after code is reduced
    compilerOptions.optIn = listOf("kotlin.time.ExperimentalTime")
  }

  test {
    useJUnitPlatform()
    testLogging {
      events("passed", "skipped", "failed")
    }
  }

  kover {
    htmlReport {
      onCheck.set(true)
    }

    verify {
      onCheck.set(true)

      rule {
        name = "Cover coverage bounds"
        isEnabled = true

        target = VerificationTarget.ALL
        bound {
          minValue = 0
          valueType = VerificationValueType.COVERED_PERCENTAGE
          counter = CounterType.INSTRUCTION
        }
      }
    }
  }
}
