plugins {
  id("kotlin-project")
  id("nu.studer.jooq") version "10.1.1"
  idea
}

// TODO manage version constants somehow
dependencies {
  implementation(project(":domain-app"))
  implementation(project(":adapter-out-postgres"))

  // TODO deduplicate
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.29.0"))

  // Database
  implementation("io.quarkus:quarkus-agroal")
  implementation("org.jooq:jooq:3.20.5")

  // Code Generator
  jooqGenerator("org.postgresql:postgresql:42.7.7")
  jooqGenerator("org.testcontainers:postgresql:1.19.0")
}

// TODO deduplicate JOOQ setup

val jooqOutputDir = "build/generated/sources/jooq/main"

jooq {
  configurations {
    create("app") {
      jooqConfiguration.apply {
        jdbc.apply {
          driver = "org.testcontainers.jdbc.ContainerDatabaseDriver"
          url = "jdbc:tc:postgresql:15:///app?TC_INITSCRIPT=file:build/jooq-init.sql"
        }
        generator.apply {
          database.apply {
            name = "org.jooq.meta.postgres.PostgresDatabase"
            inputSchema = "app_domain"
            includes = ".*"
          }
          target.apply {
            packageName = "de.chrgroth.james.platform.adapter.out.postgres.app.jooq"
            directory = jooqOutputDir
          }
        }
      }
    }
  }
}

tasks.register("generateJooqInitScript") {
  doLast {
    val migrationDir = file("src/main/resources/db/migration/app")
    val outputFile = file("build/jooq-init.sql")

    outputFile.parentFile.mkdirs()
    outputFile.writeText("")
    outputFile.appendText("CREATE SCHEMA app_domain;SET SEARCH_PATH = app_domain;\n\n")

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

val jooqTaskName = "generateAppJooq"
tasks.named(jooqTaskName) {
  dependsOn("generateJooqInitScript")
}

sourceSets {
  main {
    java {
      srcDir(jooqOutputDir)
    }
  }
}

idea {
  module {
    generatedSourceDirs.add(file(jooqOutputDir))
    isDownloadSources = true
  }
}

tasks.clean {
  delete(jooqOutputDir)
}

tasks.compileKotlin {
  dependsOn(tasks.named(jooqTaskName))
}

tasks.compileJava {
  dependsOn(tasks.named(jooqTaskName))
}
