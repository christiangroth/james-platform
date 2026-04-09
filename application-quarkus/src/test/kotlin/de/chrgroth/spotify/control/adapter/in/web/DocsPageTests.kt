package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class DocsPageTests {

  @Test
  fun `docs arc42 page is available and renders markdown content`() {
    given()
      .`when`()
      .get("/docs/arc42/arc42.md")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("docs-rendered"))
      .body(containsString("spotify-control"))
  }

  @Test
  fun `docs arc42 page contains raw markdown in textarea and rendering script`() {
    val body = given()
      .`when`()
      .get("/docs/arc42/arc42.md")
      .then()
      .statusCode(200)
      .extract()
      .body()
      .asString()

    assertMarkdownRenderingPipeline(body, "Introduction and Goals")
    assertThat(body).contains("spotify-control")
    assertThat(body).contains("# spotify-control")
  }

  @Test
  fun `docs adr detail page renders a specific adr`() {
    given()
      .`when`()
      .get("/docs/adr/0001-using-arc42-as-project-documentation.md")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("docs-rendered"))
  }

  @Test
  fun `docs adr detail page contains raw markdown in textarea and rendering script`() {
    val body = given()
      .`when`()
      .get("/docs/adr/0001-using-arc42-as-project-documentation.md")
      .then()
      .statusCode(200)
      .extract()
      .body()
      .asString()

    assertMarkdownRenderingPipeline(body, "Context and Problem Statement")
    assertThat(body).contains("Using arc42 as Project Documentation")
  }

  @Test
  fun `docs adr detail page returns not found for invalid filename`() {
    given()
      .`when`()
      .get("/docs/adr/not-an-md-file.txt")
      .then()
      .statusCode(404)
  }

  @Test
  fun `docs releasenotes page is available and renders markdown content`() {
    given()
      .`when`()
      .get("/docs/releasenotes/RELEASENOTES.md")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("docs-rendered"))
      .body(containsString("Bugfixes"))
  }

  @Test
  fun `docs releasenotes page contains raw markdown in textarea and rendering script`() {
    val body = given()
      .`when`()
      .get("/docs/releasenotes/RELEASENOTES.md")
      .then()
      .statusCode(200)
      .extract()
      .body()
      .asString()

    assertMarkdownRenderingPipeline(body, "# 0.9")
  }

  private fun assertMarkdownRenderingPipeline(body: String, expectedMarkdownContent: String) {
    assertThat(body).contains("""id="docs-raw"""")
    assertThat(body).contains(expectedMarkdownContent)
    assertThat(body).contains("marked.parse(")
    assertThat(body.indexOf("marked.umd.js")).isLessThan(body.indexOf("marked.parse("))
  }
}
