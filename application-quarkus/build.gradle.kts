plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
  alias(libs.plugins.quarkus)
}

dependencies {
  implementation(project(":adapter-in-outbox"))
  implementation(project(":adapter-in-scheduler"))
  implementation(project(":adapter-in-starter"))
  implementation(project(":adapter-in-web"))
  implementation(project(":adapter-out-config"))
  implementation(project(":adapter-out-mongodb"))
  implementation(project(":adapter-out-outbox"))
  implementation(project(":adapter-out-scheduler"))
  implementation(project(":adapter-out-slack"))
  implementation(project(":adapter-out-spotify"))
  implementation(project(":domain-impl"))

  implementation(libs.quarkusOutboxAdapterInScheduler)
  implementation(libs.quarkusOutboxAdapterOutExecutor)
  implementation(libs.quarkusOutboxAdapterOutPersistenceMongodb)
  implementation(libs.quarkusOutboxDomainImpl)
  implementation(libs.quarkusStartersDomainImpl)
  implementation(libs.quarkusStartersAdapterOutPersistenceMongodb)

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  api(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-kotlin")
  implementation("io.quarkus:quarkus-smallrye-health")
  implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
  implementation("io.quarkus:quarkus-logging-json")
  implementation("io.quarkus:quarkus-container-image-docker")

  testImplementation("io.quarkus:quarkus-junit5")
  testImplementation("io.quarkus:quarkus-junit5-mockito")
  testImplementation("io.quarkus:quarkus-test-security")
  testImplementation("io.rest-assured:rest-assured")
}

tasks.withType<Test> {
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

allOpen {
  annotation("jakarta.ws.rs.Path")
  annotation("jakarta.enterprise.context.ApplicationScoped")
  annotation("io.quarkus.test.junit.QuarkusTest")
}
