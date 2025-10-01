plugins {
  id("kotlin-project")
  id("nu.studer.jooq") version "10.1.1"
  idea
}

// TODO manage version constants somehow
dependencies {
  implementation(project(":domain-user"))
  implementation(project(":adapter-out-postgres"))

  // TODO deduplicate
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.18.3"))

  // Database
  implementation("io.quarkus:quarkus-flyway")
  implementation("io.quarkus:quarkus-agroal")
  implementation("org.jooq:jooq:3.20.5")

  // Code Generator
  jooqGenerator("org.postgresql:postgresql:42.7.7")
  jooqGenerator("org.testcontainers:postgresql:1.19.0")
}

val jooqOutputDir = "build/generated/sources/jooq/main"

jooq {
  configurations {
    create("user") {
      jooqConfiguration.apply {
        jdbc.apply {
          driver = "org.testcontainers.jdbc.ContainerDatabaseDriver"
          url = "jdbc:tc:postgresql:15:///user?TC_INITSCRIPT=file:build/jooq-init.sql"
        }
        generator.apply {
          database.apply {
            name = "org.jooq.meta.postgres.PostgresDatabase"
            inputSchema = "user_domain"
            includes = ".*"
          }
          target.apply {
            packageName = "de.chrgroth.james.platform.adapter.out.postgres.user.jooq"
            directory = jooqOutputDir
          }
        }
      }
    }
  }
}

// Gradle Task zum Generieren des Skripts
tasks.register("generateJooqInitScript") {
  doLast {
    val migrationDir = file("src/main/resources/db/migration/user")
    val outputFile = file("build/jooq-init.sql")

    outputFile.parentFile.mkdirs()
    outputFile.writeText("")
    outputFile.appendText("CREATE SCHEMA user_domain;SET SEARCH_PATH = user_domain;\n\n")

    migrationDir.listFiles()
      ?.filter { it.extension == "sql" }
      ?.sorted()
      ?.forEach { sqlFile ->
        outputFile.appendText("-- ${sqlFile.name}\n")
        outputFile.appendText(sqlFile.readText())
        outputFile.appendText("\n")
      }

    println("Generated jOOQ init script: ${outputFile.absolutePath}")
  }
}

// Vor jOOQ-Generation das Skript erstellen
val jooqTaskName = "generateUserJooq"
tasks.named(jooqTaskName) {
  dependsOn("generateJooqInitScript")
}

// Source-Verzeichnis registrieren
sourceSets {
  main {
    java {
      srcDir(jooqOutputDir)
    }
  }
}

// IntelliJ Integration
idea {
  module {
    generatedSourceDirs.add(file(jooqOutputDir))
    isDownloadSources = true
  }
}

// Bei clean löschen
tasks.clean {
  delete(jooqOutputDir)
}

// Automatisch generieren vor dem Kompilieren
tasks.compileKotlin {
  dependsOn(tasks.named(jooqTaskName))
}

tasks.compileJava {
  dependsOn(tasks.named(jooqTaskName))
}
