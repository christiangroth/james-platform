import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    kotlin("jvm")
    `java-library`
    `java-test-fixtures`

    jacoco
    id("io.gitlab.arturbosch.detekt")
    id("com.xcporter.metaview")
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

    implementation("com.sksamuel.tribune:tribune-core:1.2.4")

    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testImplementation("io.mockk:mockk:1.12.2")
    testImplementation("com.sksamuel.tribune:tribune-core:1.2.4")

    testFixturesImplementation("org.assertj:assertj-core:3.23.1")
    testFixturesImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testFixturesImplementation("io.mockk:mockk:1.12.2")
    testFixturesImplementation("com.sksamuel.tribune:tribune-core:1.2.4")
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

jacoco {
    toolVersion = "0.8.7"
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
        finalizedBy(tasks.getByName("generateUmlDiagrams"))
    }

    test {
        finalizedBy(jacocoTestReport)
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    jacocoTestCoverageVerification {
        violationRules {
            rule {
                limit {
                    minimum = "0.90".toBigDecimal()
                }
            }
        }
    }

    check {
        dependsOn(jacocoTestCoverageVerification)
    }
}
