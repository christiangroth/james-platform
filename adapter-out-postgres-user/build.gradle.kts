plugins {
  id("kotlin-project")
  id("app.cash.sqldelight") version "2.1.0"
}

dependencies {
  implementation(project(":domain-user"))

  // TODO deduplicate
  api(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.18.3"))
  api("io.quarkus:quarkus-jdbc-postgresql")
  api("io.quarkus:quarkus-vertx")

  api("app.cash.sqldelight:jdbc-driver:2.1.0")
  api("app.cash.sqldelight:postgresql-dialect:2.1.0")
}

sqldelight {
  databases {
    create("UserDatabase") {
      packageName = "de.chrgroth.james.platform.adapter.out.postgres.user"
      verifyMigrations.set(true)
      deriveSchemaFromMigrations.set(true)
      migrationOutputFileFormat = "sql"
      dialect("app.cash.sqldelight:postgresql-dialect:2.1.0")
    }
  }
}
