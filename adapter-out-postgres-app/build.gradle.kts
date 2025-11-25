plugins {
  id("kotlin-project")
  alias(libs.plugins.jooq)
  idea
}

dependencies {
  implementation(project(":domain-app"))
  implementation(project(":adapter-out-postgres"))

  implementation(enforcedPlatform(libs.quarkusBom))

  implementation(libs.bundles.jooq)
  jooqGenerator(libs.bundles.jooqGenerator)
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
