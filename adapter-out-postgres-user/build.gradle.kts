plugins {
  id("kotlin-project")
}

// TODO manage version constants somehow
dependencies {
  implementation(project(":domain-user"))

  // TODO deduplicate
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.18.3"))

  // Database
  implementation("io.quarkus:quarkus-flyway")
  implementation("io.quarkus:quarkus-jdbc-postgresql")
  implementation("io.quarkus:quarkus-hibernate-orm")
  implementation("io.quarkus:quarkus-hibernate-validator")
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
*/
