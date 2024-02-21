import io.gitlab.arturbosch.detekt.Detekt
import kotlinx.kover.api.CounterType
import kotlinx.kover.api.VerificationTarget
import kotlinx.kover.api.VerificationValueType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    kotlin("jvm")
    `java-library`
    `java-test-fixtures`

    id("io.gitlab.arturbosch.detekt")
    id("com.xcporter.metaview")
    id("org.jetbrains.kotlinx.kover")
}

repositories {
    mavenCentral()
    jcenter()
    maven {
        url = URI("https://jitpack.io")
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-Beta")
    implementation("io.github.microutils:kotlin-logging:1.8.3")

    api("com.sksamuel.tribune:tribune-core:1.2.4")

    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testImplementation("io.mockk:mockk:1.13.9")

    testFixturesImplementation("org.assertj:assertj-core:3.23.1")
    testFixturesImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testFixturesImplementation("io.mockk:mockk:1.13.9")
}

java {
    withSourcesJar()
    withJavadocJar()

    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("${rootProject.projectDir}/detekt-config.yaml"))
}

generateUml {
    classTree {
        target = file(projectDir.path + "/src/main/kotlin")
        outputDir = file(project.buildDir.path + "/docs")
    }
    functionTree {
        target = file(projectDir.path + "/src/main/kotlin")
        outputDir = file(project.buildDir.path + "/docs")
    }
}

tasks {
    withType<Detekt> {
        this.jvmTarget = "17"
    }

    // see https://kotlinlang.org/docs/gradle.html#compiler-options
    withType<KotlinCompile>().configureEach {
        kotlinOptions.apiVersion = "1.9"
        kotlinOptions.languageVersion = "1.9"

        kotlinOptions.jvmTarget = "17"

        kotlinOptions.allWarningsAsErrors = false
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.time.ExperimentalTime"
    }

    compileKotlin {
        // TODO #9 reactivate
        // finalizedBy(tasks.getByName("generateUmlDiagrams"))
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
