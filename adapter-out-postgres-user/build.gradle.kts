plugins {
  id("kotlin-project")
  id("org.jooq.jooq-codegen-gradle") version "3.20.7"
}

// TODO manage version constants somehow
dependencies {
  implementation(project(":domain-user"))

  // TODO deduplicate
  api(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.18.3"))

  // Database
  api("io.quarkus:quarkus-jdbc-postgresql")
  api("io.quarkus:quarkus-flyway")
  // TODO api("io.quarkus:quarkus-jooq")

  // jOOQ
  implementation("org.jooq:jooq:3.18.7")
  // TODO jooqGenerator("org.postgresql:postgresql:42.7.3")

  // Testing
  testImplementation("io.quarkus:quarkus-junit5")
  testImplementation("io.quarkus:quarkus-test-h2")
}

/*
flyway {
  url = "jdbc:postgresql://localhost:5432/mydb"
  user = "dbuser"
  password = "dbpass"
  schemas = arrayOf("user_domain")
  locations = arrayOf("classpath:db/migration")
  table = "flyway_schema_history_user" // separate History-Tabelle
}

jooq {
  configuration {
    create("main") {

      jooqConfiguration.apply {
        jdbc.apply {
          driver = "org.postgresql.Driver"
          url = "jdbc:postgresql://localhost:5432/mydb"
          user = "dbuser"
          password = "dbpass"
        }

        generator.apply {
          name = "org.jooq.codegen.KotlinGenerator"
          database.apply {
            name = "org.jooq.meta.postgres.PostgresDatabase"
            includes = "user_.*" // nur User-Tabellen
            excludes = ""
            inputSchema = "user_schema"

            // Custom types for enums
            forcedTypes {
              forcedType {
                userType = "de.chrgroth.james.platform.domain.user.PasswordStatus"
                includeExpression = ".*\\.password_status"
                includeTypes = "password_status"
                converter = "de.chrgroth.james.platform.adapter.out.postgres.user.PasswordStatusConverter"
              }
              forcedType {
                userType = "de.chrgroth.james.platform.domain.user.UserRole"
                includeExpression = ".*\\.user_role"
                includeTypes = "user_role"
                converter = "de.chrgroth.james.platform.adapter.out.postgres.user.UserRoleConverter"
              }
              forcedType {
                userType = "de.chrgroth.james.platform.domain.user.UserStatus"
                includeExpression = ".*\\.user_status"
                includeTypes = "user_status"
                converter = "de.chrgroth.james.platform.adapter.out.postgres.user.UserStatusConverter"
              }
            }
          }

          target.apply {
            packageName = "de.chrgroth.james.platform.adapter.out.postgres.user.generated"
            directory = "build/generated/jooq"
          }
        }
      }
    }
  }
}
*/

tasks.named("compileKotlin") {
  dependsOn("generateJooq")
}

sourceSets.main {
  java.srcDirs("build/generated/jooq")
}

tasks.named("clean") {
  delete("build/generated")
}
