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
      .body(containsString("System Health"))
  }

  @Test
  fun `health page displays health section with communication, state, cronjobs and mongodb sub-headings`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""id="health-section""""))
      .body(containsString("Communication"))
      .body(containsString("Cronjobs &amp; State"))
      .body(containsString("MongoDB"))
      .body(containsString("Outbox Partitions"))
      .body(containsString("Collections"))
      .body(containsString("Queries (Last 24h)"))
  }

  @Test
  fun `health page outbox table contains blocked column`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="outbox-table""""))
      .body(containsString("Blocked"))
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
      .body(containsString("refresh-outbox-partitions"))
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
  fun `health snippet endpoint for outbox partitions is available`() {
    given()
      .`when`()
      .get("/health/snippets/outbox-partitions")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Outbox Partitions"))
  }

  @Test
  fun `health page contains outbox blocked-until countdown javascript`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("formatBlockedUntil"))
      .body(containsString("updateOutboxBlockedUntilCountdowns"))
      .body(containsString("startOutboxBlockedUntilInterval"))
      .body(containsString("outboxBlockedUntilInterval"))
      .body(containsString("data-blocked-until"))
      .body(containsString("outbox-blocked-until"))
  }

  @Test
  fun `health page outbox blocked-until uses dd-MM-yyyy HH-mm format for dates beyond 24h`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("TWENTY_FOUR_HOURS_MS"))
    given()
      .`when`()
      .get("/sse-utils.js")
      .then()
      .statusCode(200)
      .body(containsString("day + '.' + month + '.' + year + ' ' + hours + ':' + minutes"))
  }

  @Test
  fun `health page outbox blocked-until shows only countdown when less than 24h away`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("formatCountdown(remaining)"))
      .body(not(containsString("formatBlockedUntil(blockedUntil) + ' ('")))
  }

  @Test
  fun `health page outbox blocked-until interval is started and managed with 500ms`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("setInterval(updateOutboxBlockedUntilCountdowns, 500)"))
      .body(containsString("clearInterval(outboxBlockedUntilInterval)"))
  }

  @Test
  fun `health page outbox interval is cleared before sse snippet replacement`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("refresh-outbox-partitions"))
      .body(containsString("startOutboxBlockedUntilInterval"))
      .body(containsString("clearInterval"))
  }

  @Test
  fun `health snippet endpoint for mongodb collections is available`() {
    given()
      .`when`()
      .get("/health/snippets/mongodb-collections")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Collections"))
      .body(containsString("""data-testid="mongodb-collections-table""""))
      .body(containsString("Size"))
  }

  @Test
  fun `health snippet endpoint for mongodb queries is available`() {
    given()
      .`when`()
      .get("/health/snippets/mongodb-queries")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Queries (Last 24h)"))
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
      .body(containsString("Next"))
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
  fun `health page outbox table uses icon before partition name instead of status column`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="outbox-table""""))
      .body(containsString("fill=\"#1db954\""))
      .body(containsString("vertical-align:middle"))
  }

  @Test
  fun `health page cronjob table uses icon before job name instead of status column`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="cronjobs-table""""))
      .body(containsString("fill=\"#1db954\""))
      .body(containsString("vertical-align:middle"))
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
      .body(containsString("Executions"))
  }

  @Test
  fun `health page outbox document count is shown statically`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(not(containsString("toggleOutboxDetail")))
      .body(containsString("outbox-doc-count"))
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
  fun `health page outbox detail row is expanded by default`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(not(containsString("toggleOutboxDetail")))
      .body(not(containsString("display:none;border-color")))
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
}
