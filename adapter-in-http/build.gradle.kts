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
  api("org.webjars.npm:marked:12.0.2")
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
  val copyReleaseNotes = register<Copy>("copyReleaseNotes") {
    from(project.rootDir) {
      include("RELEASENOTES.md")
    }

    into(layout.buildDirectory.dir("resources/main/META-INF/resources"))
  }

  processResources {
    dependsOn(copyReleaseNotes)
  }

  compileKotlin {
    dependsOn(openApiGenerate)
  }
}
