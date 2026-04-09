plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
}

dependencies {
  implementation(project(":domain-api"))

  api(enforcedPlatform(libs.quarkusBom))
  api("io.quarkus:quarkus-rest-jackson")
  api("io.quarkus:quarkus-rest-qute")
  api("io.quarkus:quarkus-security")
  api("io.quarkus:quarkus-web-dependency-locator")

  implementation(libs.bootstrap)
  implementation(libs.marked)
}

tasks {
  val syncDocsMd by registering(Sync::class) {
    mustRunAfter(rootProject.tasks.named("releasenotesCopyToSources"))
    from(rootProject.layout.projectDirectory.dir("docs/arc42")) {
      include("arc42.md", "outbox.md", "starters.md")
      into("arc42")
    }
    from(rootProject.layout.projectDirectory.dir("docs/adr")) {
      include("*.md")
      exclude("0000-template.md")
      into("adr")
    }
    from(rootProject.layout.projectDirectory.dir("docs/releasenotes")) {
      include("RELEASENOTES.md")
      into("releasenotes")
    }
    from(rootProject.layout.projectDirectory.dir("docs/coding-guidelines")) {
      include("*.md")
      into("coding-guidelines")
    }
    into(layout.projectDirectory.dir("src/main/resources/docs"))
  }

  named("processResources") {
    dependsOn(syncDocsMd)
  }
}

allOpen {
  annotation("jakarta.ws.rs.Path")
  annotation("jakarta.enterprise.context.ApplicationScoped")
}

