import io.gitlab.arturbosch.detekt.Detekt
import kotlinx.kover.api.CounterType
import kotlinx.kover.api.KoverVerifyConfig
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

    api("com.sksamuel.tribune:tribune-core:1.2.4")

    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testImplementation("io.mockk:mockk:1.12.2")

    testFixturesImplementation("org.assertj:assertj-core:3.23.1")
    testFixturesImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testFixturesImplementation("io.mockk:mockk:1.12.2")
}

java {
    withSourcesJar()
    withJavadocJar()
}

detekt {
    buildUponDefaultConfig = true
    config = files("${rootProject.projectDir}/detekt-config.yaml")
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
        this.jvmTarget = "11"
    }

    // see https://kotlinlang.org/docs/gradle.html#compiler-options
    withType<KotlinCompile>().configureEach {
        kotlinOptions.apiVersion = "1.5"
        kotlinOptions.languageVersion = "1.5"
        kotlinOptions.jvmTarget = "11"

        kotlinOptions.allWarningsAsErrors = true
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.time.ExperimentalTime"
    }

    compileKotlin {
        // TODO reactivate
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
                    minValue = 90
                    valueType = VerificationValueType.COVERED_PERCENTAGE
                    counter = CounterType.INSTRUCTION
                }
            }
        }
    }
}
