plugins {
  id("kotlin-project")
  kotlin("plugin.allopen") version "2.0.21"
  id("io.quarkus") version "3.18.3"
}

dependencies {
  implementation(project(":adapter-in-http"))
  implementation(project(":adapter-out-postgres"))
  implementation(project(":adapter-out-postgres-app"))
  implementation(project(":adapter-out-postgres-user"))
  implementation(project(":domain-app"))
  implementation(project(":domain-user"))

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.18.3"))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-kotlin")
  implementation("io.quarkus:quarkus-smallrye-health")
  implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
  implementation("io.quarkus:quarkus-container-image-docker")

  testImplementation("io.quarkus:quarkus-junit5")
  testImplementation("io.quarkus:quarkus-junit5-mockito")
  testImplementation("io.quarkus:quarkus-jdbc-postgresql")
  testImplementation("io.quarkus:quarkus-test-security")
  testImplementation("io.rest-assured:rest-assured")

  testImplementation(testFixtures(project(":core")))
}

tasks.withType<Test> {
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

allOpen {
  annotation("jakarta.ws.rs.Path")
  annotation("jakarta.enterprise.context.ApplicationScoped")
  annotation("jakarta.persistence.Entity")
  annotation("io.quarkus.test.junit.QuarkusTest")
}
