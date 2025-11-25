plugins {
    id("kotlin-project")
    alias(libs.plugins.allopen)
    alias(libs.plugins.openapiGenerator)
}

dependencies {
    implementation(project(":domain-app"))
    implementation(project(":domain-user"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    api(enforcedPlatform(libs.quarkusBom))
    api("io.quarkus:quarkus-rest-jackson")
    api("io.quarkus:quarkus-security")
    api("io.quarkiverse.qute.web:quarkus-qute-web")
    api("io.quarkus:quarkus-web-dependency-locator")

    api(libs.webjarAlpinejs)
    api(libs.webjarMarked)
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
