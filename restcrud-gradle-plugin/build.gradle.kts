plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.3.72"
}

repositories {
    mavenCentral()
//    mavenLocal()
//    jcenter()
//    maven { url 'https://kotlin.bintray.com/ktor' }
}

gradlePlugin {
    plugins {
        create("restcrud") {
            id = "de.chrgroth.gradle.restcrud"
            implementationClass = "de.chrgroth.restcrud.RestCrudPlugin"
        }
    }
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))
    implementation("org.yaml:snakeyaml:1.26")

//    implementation("com.squareup:kotlinpoet:1.4.4")
//    implementation(kotlin("gradle-plugin", version = "1.3.61"))

    testImplementation(gradleTestKit())
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}
