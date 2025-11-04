import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("kotlin-project")
  kotlin("plugin.allopen") version "2.0.21"
  id("org.openapi.generator") version "7.11.0"
}

dependencies {
  implementation(project(":domain-app"))
  implementation(project(":domain-user"))

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  api(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.18.3"))
  api("io.quarkus:quarkus-rest-jackson")
  api("io.quarkus:quarkus-security")

  api("io.quarkiverse.qute.web:quarkus-qute-web")
  api("io.quarkus:quarkus-web-dependency-locator")
  api("org.webjars.npm:alpinejs:3.15.1")
}

openApiGenerate {
  generatorName = "kotlin-server"
  inputSpec = project.layout.projectDirectory.dir("src/main/openapi/frontend.yaml").asFile.path
  outputDir = project.layout.buildDirectory.dir("generated/openapi").get().asFile.path

  packageName = "de.chrgroth.james.platform.adapter.incoming.http"
  apiPackage = "de.chrgroth.james.platform.adapter.incoming.http.api"
  modelPackage = "de.chrgroth.james.platform.adapter.incoming.http.api.model"

  generateModelDocumentation = false
  generateApiDocumentation = false
  generateModelTests = false
  generateApiTests = false

  configOptions = mapOf(
    "library" to "jaxrs-spec",
    "useJakartaEe" to "true",
    "interfaceOnly" to "true",
    "returnResponse" to "false",
    "enumPropertyNaming" to "UPPERCASE",
  )
}

allOpen {
  annotation("jakarta.ws.rs.Path")
  annotation("jakarta.enterprise.context.ApplicationScoped")
}

project.layout.buildDirectory.dir("generated/openapi/src/main/kotlin").get().asFile.path.also {
  kotlin.sourceSets.main {
    kotlin.srcDir(it)
  }

  java.sourceSets.main {
    kotlin.srcDir(it)
  }
}

tasks {
  val openApiGenerateFrontend = register<GenerateTask>("openApiGenerateFrontend") {
    generatorName.set("javascript")
    inputSpec.set("$projectDir/src/main/openapi/frontend.yaml")
    outputDir.set("${buildDir}/generated/openapi-frontend")
    configOptions.set(
      mapOf(
        "projectName" to "james-platform-api",
        "usePromises" to "true",
        "useES6" to "true"
      )
    )

    // Output to resources so it's included in the JAR
    doLast {
      copy {
        from("${buildDir}/generated/openapi-frontend/src")
        into("${buildDir}/resources/main/META-INF/resources/js/open-api")
      }
    }
  }

  processResources {
    dependsOn(openApiGenerateFrontend)
  }

  compileKotlin {
    dependsOn(openApiGenerate)
  }
}
