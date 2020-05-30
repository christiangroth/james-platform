import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.3.72"
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("restcrud") {
            id = "de.chrgroth.gradle.restcrud"
            implementationClass = "de.chrgroth.restcrud.GradlePlugin"
        }
    }
}

dependencies {
    implementation(kotlin("gradle-plugin", version = "1.3.72"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.8.11")

//    implementation("com.squareup:kotlinpoet:1.4.4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    testImplementation(gradleTestKit())
}

tasks {

    withType<KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "1.8"
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
