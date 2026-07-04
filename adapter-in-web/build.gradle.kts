import java.io.File

plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
}

kotlin {
  // Qute message bundle interfaces (see AppMessages) resolve `{paramName}` placeholders via reflection on method
  // parameter names, which the JVM only retains when compiled with this flag.
  compilerOptions {
    javaParameters = true
  }
}

dependencies {
  implementation(project(":domain-api"))

  api(enforcedPlatform(libs.quarkusBom))
  api("io.quarkus:quarkus-rest-jackson")
  api("io.quarkus:quarkus-rest-qute")
  api("io.quarkus:quarkus-security")
  api("io.quarkus:quarkus-web-dependency-locator")

  implementation(libs.bootstrap)
  implementation(libs.bootstrapIcons)
  implementation(libs.marked)
}

// Bundle qualifiers to pseudo-localize, matching the `messages/<qualifier>_de.properties` files (see AppMessages, DeveloperMessages, UserMessages, AdminMessages, MonitoringMessages).
val pseudoLocaleBundleQualifiers = listOf("msg", "developer", "user", "admin", "monitoring")
val pseudoLocaleOutputDir = layout.buildDirectory.dir("generated/resources/pseudoLocale")

sourceSets {
  main {
    resources {
      srcDir(pseudoLocaleOutputDir)
    }
  }
}

tasks {
  // Generates the "Underscore" test locale (`xx`) from the German properties: every letter/digit becomes `_`,
  // `{param}` placeholders are preserved untouched, and all whitespace/punctuation/special characters are kept as-is.
  // Regenerated on every build so it can never drift out of sync with the German source of truth.
  val generatePseudoLocaleMessages by registering {
    val sourceDir = layout.projectDirectory.dir("src/main/resources/messages")
    val outputDir = pseudoLocaleOutputDir.map { it.dir("messages") }
    inputs.dir(sourceDir)
    outputs.dir(outputDir)

    doLast {
      val outDir = outputDir.get().asFile
      outDir.mkdirs()
      pseudoLocaleBundleQualifiers.forEach { qualifier ->
        val deFile = sourceDir.file("${qualifier}_de.properties").asFile
        if (deFile.exists()) {
          File(outDir, "${qualifier}_xx.properties").bufferedWriter(Charsets.UTF_8).use { writer ->
            deFile.forEachLine(Charsets.UTF_8) { line ->
              val trimmed = line.trim()
              val separatorIndex = line.indexOf('=')
              val pseudoLocalizedLine = if (trimmed.isEmpty() || trimmed.startsWith("#") || separatorIndex < 0) {
                line
              } else {
                line.substring(0, separatorIndex + 1) + pseudoLocalize(line.substring(separatorIndex + 1))
              }
              writer.write(pseudoLocalizedLine)
              writer.newLine()
            }
          }
        }
      }
    }
  }

  val syncDocsMd by registering(Sync::class) {
    mustRunAfter(rootProject.tasks.named("releasenotesCopyToSources"))
    from(rootProject.layout.projectDirectory.dir("docs/arc42")) {
      include("arc42.md")
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
    dependsOn(syncDocsMd, generatePseudoLocaleMessages)
  }
}

allOpen {
  annotation("jakarta.ws.rs.Path")
  annotation("jakarta.enterprise.context.ApplicationScoped")
}

// Replaces every letter/digit in a German message value with `_` for the "Underscore" pseudo-locale, keeping the
// content of `{param}` placeholders, whitespace, punctuation and other special characters untouched.
fun pseudoLocalize(value: String): String {
  val result = StringBuilder(value.length)
  var placeholderDepth = 0
  for (c in value) {
    when {
      c == '{' -> {
        placeholderDepth++
        result.append(c)
      }
      c == '}' -> {
        placeholderDepth = maxOf(0, placeholderDepth - 1)
        result.append(c)
      }
      placeholderDepth > 0 -> result.append(c)
      Character.isLetterOrDigit(c) -> result.append('_')
      else -> result.append(c)
    }
  }
  return result.toString()
}

