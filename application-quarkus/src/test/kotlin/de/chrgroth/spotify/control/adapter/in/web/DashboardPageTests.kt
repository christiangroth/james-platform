package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class DashboardPageTests {

  @Test
  fun `dashboard page is available and displays logout link and personalized welcome message`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="logout-link""""))
      .body(containsString("""data-testid="welcome-message""""))
      .body(containsString("Hej "))
  }

  @Test
  fun `dashboard page displays github link in dropdown`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="github-link""""))
      .body(containsString("https://github.com/christiangroth/spotify-control"))
      .body(containsString("Code"))
  }

  @Test
  fun `dashboard page displays spotify api link in dropdown`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="spotify-api-link""""))
      .body(containsString("https://developer.spotify.com/documentation/web-api"))
      .body(containsString("API"))
  }

  @Test
  fun `dashboard page displays stats section`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""id="stats-section""""))
      .body(containsString("Total Playback Events"))
      .body(containsString("Playback Events (Last 30 Days)"))
      .body(containsString("""data-testid="histogram""""))
  }

  @Test
  fun `dashboard page contains sse connection setup with reconnect interval`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("sse-utils.js"))
      .body(containsString("connectSse"))
      .body(containsString("/dashboard/events"))
  }

  @Test
  fun `dashboard page uses specific sse events with fade updates instead of full reload`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("refresh-playback-data"))
      .body(containsString("refresh-playlist-metadata"))
      .body(containsString("fadeUpdate"))
  }

  @Test
  fun `dashboard page displays recently played section`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("Recently Played"))
      .body(containsString("""id="snippet-recently-played""""))
  }

  @Test
  fun `dashboard snippet endpoint for recently played is available`() {
    given()
      .`when`()
      .get("/dashboard/snippets/recently-played")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Recently Played"))
  }

  @Test
  fun `dashboard snippet endpoint for playback data is available`() {
    given()
      .`when`()
      .get("/dashboard/snippets/playback-data")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Total Playback Events"))
  }

  @Test
  fun `dashboard snippet endpoint for playlist metadata is available`() {
    given()
      .`when`()
      .get("/dashboard/snippets/playlist-metadata")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playlists synced"))
      .body(containsString("""data-testid="playlist-settings-link""""))
      .body(containsString("/settings/playlist"))
  }

  @Test
  fun `dashboard page displays listening stats section`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("Listening Stats"))
      .body(containsString("""id="snippet-listening-stats""""))
  }

  @Test
  fun `dashboard snippet endpoint for listening stats is available`() {
    given()
      .`when`()
      .get("/dashboard/snippets/listening-stats")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Listening Stats"))
      .body(containsString("""data-testid="listened-minutes""""))
      .body(containsString("Top Tracks"))
      .body(containsString("Top Artists"))
  }

  @Test
  fun `dashboard sse handler refreshes listening stats on playback data update`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("snippet-listening-stats"))
      .body(containsString("/dashboard/snippets/listening-stats"))
  }

  @Test
  fun `dashboard page displays catalog stats section with fragment`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""id="snippet-catalog-stats""""))
      .body(containsString("Artists"))
      .body(containsString("Albums"))
      .body(containsString("Tracks"))
  }

  @Test
  fun `dashboard snippet endpoint for catalog stats is available`() {
    given()
      .`when`()
      .get("/dashboard/snippets/catalog-stats")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Artists"))
      .body(containsString("Albums"))
      .body(containsString("Tracks"))
  }

  @Test
  fun `dashboard sse handler refreshes catalog stats on catalog data update`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("refresh-catalog-data"))
      .body(containsString("snippet-catalog-stats"))
      .body(containsString("/dashboard/snippets/catalog-stats"))
  }

  @Test
  fun `dashboard page contains navbar health indicator icons`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""id="navbar-health-indicators""""))
      .body(containsString("""data-testid="navbar-outbox-icon""""))
      .body(containsString("""data-testid="navbar-playback-icon""""))
  }

  @Test
  fun `dashboard page contains navbar health indicators sse connection`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("updateNavbarOutboxStatus"))
      .body(containsString("updateNavbarPlaybackStatus"))
      .body(containsString("/health/snippets/navbar-outbox-status"))
      .body(containsString("/health/snippets/navbar-playback-status"))
      .body(containsString("navbar-outbox-popup"))
  }

  @Test
  fun `dashboard page navbar sse connection to health events refreshes widgets on open`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("connectSse('/health/events'"))
      .body(containsString("refresh-outbox-partitions"))
      .body(containsString("refresh-playback-state"))
  }

  @Test
  fun `dashboard page navbar popup shows blocked countdown for paused outbox partitions`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("updateNavbarOutboxPopupCountdown"))
      .body(containsString("startNavbarOutboxPopupCountdown"))
      .body(containsString("outboxPopupCountdownInterval"))
      .body(containsString("data-blocked-until"))
      .body(containsString("outbox-blocked-until"))
  }

  @Test
  fun `dashboard page navbar popup resume partition button is functional`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("resumeOutboxPartition"))
      .body(containsString("window.resumeOutboxPartition"))
  }
}
