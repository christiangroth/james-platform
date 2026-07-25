package de.chrgroth.james.platform.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReleaseNotesParserTests {

  @Test
  fun `parses a single version with one section`() {
    val markdown = """
      # 0.73.2 (2026.07.25)

      ## Bugfixes / Chore
      * Documented the Data Import (ETL) feature and its security design in the architecture docs.



      ---
    """.trimIndent()

    val groups = ReleaseNotesParser.parse(markdown)

    assertThat(groups).hasSize(1)
    assertThat(groups[0].minorVersion).isEqualTo("0.73")
    assertThat(groups[0].entries).hasSize(1)
    val entry = groups[0].entries[0]
    assertThat(entry.version).isEqualTo("0.73.2")
    assertThat(entry.date).isEqualTo("2026.07.25")
    assertThat(entry.breakingChanges).isEmpty()
    assertThat(entry.newFeatures).isEmpty()
    assertThat(entry.bugfixes).containsExactly("Documented the Data Import (ETL) feature and its security design in the architecture docs.")
  }

  @Test
  fun `parses breaking changes, new features and bugfixes in a single version`() {
    val markdown = """
      # 1.2.0 (2026.01.01)

      ## Breaking Changes
      * Removed the legacy `/api/v1` endpoint.

      ## New Features
      * Added a new `FOO` role.
      * Added a settings page.

      ## Bugfixes / Chore
      * Fixed a crash on startup.

      ---
    """.trimIndent()

    val groups = ReleaseNotesParser.parse(markdown)

    assertThat(groups).hasSize(1)
    val entry = groups[0].entries.single()
    assertThat(entry.breakingChanges).containsExactly("Removed the legacy `/api/v1` endpoint.")
    assertThat(entry.newFeatures).containsExactly("Added a new `FOO` role.", "Added a settings page.")
    assertThat(entry.bugfixes).containsExactly("Fixed a crash on startup.")
  }

  @Test
  fun `groups multiple versions by minor version while preserving encounter order`() {
    val markdown = """
      # 0.73.2 (2026.07.25)

      ## Bugfixes / Chore
      * Fix two.

      ---

      # 0.73.1 (2026.07.24)

      ## Bugfixes / Chore
      * Fix one.

      ---

      # 0.72.0 (2026.07.20)

      ## New Features
      * Old feature.

      ---
    """.trimIndent()

    val groups = ReleaseNotesParser.parse(markdown)

    assertThat(groups.map { it.minorVersion }).containsExactly("0.73", "0.72")
    assertThat(groups[0].entries.map { it.version }).containsExactly("0.73.2", "0.73.1")
    assertThat(groups[1].entries.map { it.version }).containsExactly("0.72.0")
  }

  @Test
  fun `entries without a matching section stay empty`() {
    val markdown = """
      # 0.1.0 (2026.01.01)

      Some highlight text with no section heading at all.

      ---
    """.trimIndent()

    val groups = ReleaseNotesParser.parse(markdown)

    val entry = groups.single().entries.single()
    assertThat(entry.breakingChanges).isEmpty()
    assertThat(entry.newFeatures).isEmpty()
    assertThat(entry.bugfixes).isEmpty()
  }

  @Test
  fun `falls back to the full version string when it has fewer than two segments`() {
    val markdown = """
      # 1 (2026.01.01)

      ## New Features
      * Initial release.

      ---
    """.trimIndent()

    val groups = ReleaseNotesParser.parse(markdown)

    assertThat(groups.single().minorVersion).isEqualTo("1")
  }

  @Test
  fun `returns no groups for blank input`() {
    assertThat(ReleaseNotesParser.parse("")).isEmpty()
    assertThat(ReleaseNotesParser.parse("no headings here at all")).isEmpty()
  }

  @Test
  fun `markdown getters re-assemble bullet lists with the leading marker restored`() {
    val markdown = """
      # 2.0.0 (2026.02.02)

      ## New Features
      * First feature.
      * Second feature.

      ---
    """.trimIndent()

    val entry = ReleaseNotesParser.parse(markdown).single().entries.single()

    assertThat(entry.newFeaturesMarkdown).isEqualTo("* First feature.\n* Second feature.")
    assertThat(entry.breakingChangesMarkdown).isEmpty()
  }
}
