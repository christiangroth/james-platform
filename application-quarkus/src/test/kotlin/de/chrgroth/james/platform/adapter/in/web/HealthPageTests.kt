package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a", roles = ["ADMIN"])
class HealthPageTests {

  @Test
  fun `health page is available and displays system health heading`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Systemstatus"))
  }

  @Test
  fun `health page displays health section with cronjobs and mongodb sub-headings`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""id="health-section""""))
      .body(containsString("Cronjobs &amp; Zustand"))
      .body(containsString("MongoDB"))
      .body(containsString("Sammlungen"))
      .body(containsString("Abfragen (letzte 24h)"))
  }

  @Test
  fun `health page displays scripting sub-heading and snippet`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("Skripting"))
      .body(containsString("Skriptausführungen"))
      .body(containsString("""id="snippet-scripting""""))
  }

  @Test
  fun `health snippet endpoint for scripting is available`() {
    given()
      .`when`()
      .get("/health/snippets/scripting")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Skriptausführungen"))
  }

  @Test
  fun `health page displays import cleanup sub-heading and snippet`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("Import-Bereinigung"))
      .body(containsString("Bereinigungs-Cronjob"))
      .body(containsString("""id="snippet-import-cleanup""""))
  }

  @Test
  fun `health snippet endpoint for import cleanup is available`() {
    given()
      .`when`()
      .get("/health/snippets/import-cleanup")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Bereinigungs-Cronjob"))
      .body(containsString("""data-testid="import-cleanup-table""""))
      .body(containsString("Noch nicht ausgeführt"))
  }

  @Test
  fun `health page contains sse connection setup with reconnect interval`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("sse-utils.js"))
      .body(containsString("connectSse"))
      .body(containsString("/health/events"))
  }

  @Test
  fun `health page uses specific sse events with fade updates instead of full reload`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("fadeUpdate"))
  }

  @Test
  fun `health page contains health link in navbar`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="health-link""""))
  }

  @Test
  fun `health page contains grafana logs link in navbar`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="grafana-logs-link""""))
      .body(containsString("https://jamesplatform.grafana.net/d/sadlil-loki-apps-dashboard/quarkus-logs"))
  }

  @Test
  fun `health page contains grafana metrics link in navbar`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="grafana-metrics-link""""))
      .body(containsString("https://jamesplatform.grafana.net/d/quarkus-james-platform/quarkus-metrics"))
  }

  @Test
  fun `health page contains mongodb atlas link in navbar`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="mongodb-atlas-link""""))
      .body(containsString("https://cloud.mongodb.com/v2/69d66ace6a6c9a904f96cdd5#/explorer"))
  }

  @Test
  fun `health snippet endpoint for mongodb collections is available`() {
    given()
      .`when`()
      .get("/health/snippets/mongodb-collections")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Sammlungen"))
      .body(containsString("""data-testid="mongodb-collections-table""""))
      .body(containsString("Größe"))
  }

  @Test
  fun `health snippet endpoint for mongodb queries is available`() {
    given()
      .`when`()
      .get("/health/snippets/mongodb-queries")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Abfragen (letzte 24h)"))
      .body(containsString("""data-testid="mongodb-queries-table""""))
  }

  @Test
  fun `health page displays cronjobs section with table`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("Cronjobs"))
      .body(containsString("""data-testid="cronjobs-table""""))
      .body(containsString("Cron"))
      .body(containsString("Nächste Ausführung"))
  }

  @Test
  fun `health page contains cronjob countdown javascript`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("formatCountdown"))
      .body(containsString("updateCronjobCountdowns"))
      .body(containsString("sortCronjobTable"))
      .body(containsString("data-next-execution"))
      .body(containsString("cronjob-countdown"))
      .body(containsString("cronjob-pulse"))
      .body(containsString("cronjob-pulse-green"))
  }

  @Test
  fun `health snippet endpoint for cronjobs is available`() {
    given()
      .`when`()
      .get("/health/snippets/cronjobs")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="cronjobs-table""""))
  }

  @Test
  fun `health page cronjob sort only reorders dom when order changes`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("hasChanged"))
      .body(containsString("newOrder.forEach"))
  }

  @Test
  fun `health page cronjob pulse animation refreshes table from server after completion`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("fadeUpdate('snippet-cronjobs', '/health/snippets/cronjobs'"))
  }

  @Test
  fun `health page cronjob sort excludes pulsing rows from reordering`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("pulsingRows"))
      .body(containsString("sortableRows"))
      .body(containsString("pulsingRows.concat(sortableRows)"))
  }

  @Test
  fun `health page mongodb collections size column shows kb after value`() {
    given()
      .`when`()
      .get("/health/snippets/mongodb-collections")
      .then()
      .statusCode(200)
      .body(containsString(" kb</td>"))
  }

  @Test
  fun `health page mongodb queries table shows combined slow and total executions column`() {
    given()
      .`when`()
      .get("/health/snippets/mongodb-queries")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="mongodb-queries-table""""))
      .body(containsString("Ausführungen"))
  }

  @Test
  fun `health page contains config link in navbar`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="config-link""""))
  }

  @Test
  fun `health page contains logs ui link in navbar`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="logs-ui-link""""))
  }

  @Test
  fun `health page navbar sse connection refreshes widgets on open`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("connectSse('/health/events'"))
  }

  @Test
  fun `health page contains breadcrumb with admin home and active health item`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="breadcrumb-home-link""""))
      .body(containsString("""href="/ui/admin/dashboard""""))
      .body(containsString("""data-testid="breadcrumb-health""""))
      .body(containsString("Systemstatus"))
  }
}
