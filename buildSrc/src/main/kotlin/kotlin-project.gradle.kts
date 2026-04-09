import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")

  `java-library`
  `java-test-fixtures`

  id("dev.detekt")
  id("org.jetbrains.kotlinx.kover")
}

// Access the version catalog
val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
  implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(libs.findLibrary("kotlinLogging").get())
  implementation(libs.findLibrary("kotlinxCoroutinesCore").get())
  implementation(libs.findLibrary("kotlinxDatetime").get())
  api(libs.findLibrary("arrowCore").get())

  testImplementation(libs.findLibrary("assertJ").get())
  testImplementation(libs.findLibrary("junit").get())
  testImplementation(libs.findLibrary("mockk").get())
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  testFixturesImplementation(libs.findLibrary("assertJ").get())
  testFixturesImplementation(libs.findLibrary("junit").get())
  testFixturesImplementation(libs.findLibrary("mockk").get())
}

java {
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
  jvmToolchain(25)
}

kover {
  reports {
    total {
      html {
        onCheck.set(true)
      }

      verify {
        onCheck.set(true)

        rule {
          minBound(0)
        }
      }
    }
  }
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(files("${rootProject.projectDir}/detekt-config.yaml"))
}

tasks {

  withType<Detekt> {
    this.jvmTarget.set(JVM_25.target)
  }

  kotlin {
    compilerOptions.apiVersion = KOTLIN_2_3
    compilerOptions.languageVersion = KOTLIN_2_3
    compilerOptions.jvmTarget = JVM_25
    compilerOptions.allWarningsAsErrors = true
    compilerOptions.optIn = listOf("kotlin.time.ExperimentalTime")
  }

  test {
    useJUnitPlatform()
    testLogging {
      events("passed", "skipped", "failed")
    }
  }
}
