import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
    jcenter()
    maven {
        url = URI("https://jitpack.io")
    }
}

dependencies {
    api("com.github.glwithu06.semver:semver:1.0.1")
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.2.0")

    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.github.everit-org.json-schema:org.everit.json.schema:1.12.2")

    testImplementation("org.assertj:assertj-core:3.17.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")
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
