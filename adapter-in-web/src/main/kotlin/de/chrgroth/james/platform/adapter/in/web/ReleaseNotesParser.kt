package de.chrgroth.james.platform.adapter.`in`.web

private val VERSION_HEADING_REGEX = Regex("""^# (\S+) \((.+)\)$""")
private const val BREAKING_CHANGES_HEADING = "## Breaking Changes"
private const val NEW_FEATURES_HEADING = "## New Features"
private const val BUGFIXES_HEADING = "## Bugfixes / Chore"
private const val BULLET_PREFIX = "* "

/**
 * One released version, parsed out of `docs/releasenotes/RELEASENOTES.md`.
 *
 * [breakingChanges], [newFeatures] and [bugfixes] hold the bullet lines of the matching section with the leading
 * `* ` marker stripped; a version usually only has one or two of the three sections populated, the rest stay empty.
 * The `*Markdown` properties re-assemble a section's bullets into a markdown list so the release notes page can hand
 * them to `marked.js` for rendering (preserving inline formatting such as `` `code` `` or links), the same way the
 * generic docs viewer (`DocsFileResource`/`docs.html`) already renders whole markdown files client-side.
 */
data class ReleaseNotesEntry(
  val version: String,
  val date: String,
  val minorVersion: String,
  val breakingChanges: List<String> = emptyList(),
  val newFeatures: List<String> = emptyList(),
  val bugfixes: List<String> = emptyList(),
) {
  val breakingChangesMarkdown: String get() = toMarkdownList(breakingChanges)
  val newFeaturesMarkdown: String get() = toMarkdownList(newFeatures)
  val bugfixesMarkdown: String get() = toMarkdownList(bugfixes)

  private fun toMarkdownList(items: List<String>): String = items.joinToString("\n") { "$BULLET_PREFIX$it" }
}

/** All released versions sharing the same minor version (e.g. `0.73`), newest first. */
data class ReleaseNotesMinorVersionGroup(
  val minorVersion: String,
  val entries: List<ReleaseNotesEntry>,
)

/**
 * Parses the generated `docs/releasenotes/RELEASENOTES.md` file into version entries grouped by minor version, so
 * the release notes UI page can render it as a collapsible accordion instead of one long raw markdown dump.
 *
 * The file is produced by the `de.chrgroth.gradle.release-notes` Gradle plugin (see the `releasenotes { ... }` block
 * in the root `build.gradle.kts`) from per-branch snippets, and follows this exact layout per released version:
 * ```
 * # 0.73.2 (2026.07.25)
 *
 * ## Breaking Changes
 * * ...
 *
 * ## New Features
 * * ...
 *
 * ## Bugfixes / Chore
 * * ...
 *
 * ---
 * ```
 * Any of the three sections may be missing for a given version (in practice, most versions only ever carry one of
 * them, and `## Breaking Changes` has never been emitted so far since the project never had a breaking change).
 * Versions are returned in the order they are encountered (the source file is already newest-first), grouped by
 * minor version while preserving that order.
 */
object ReleaseNotesParser {

  fun parse(markdown: String): List<ReleaseNotesMinorVersionGroup> {
    val entries = mutableListOf<ReleaseNotesEntry>()
    val lines = markdown.lines()

    var index = 0
    while (index < lines.size) {
      val headingMatch = VERSION_HEADING_REGEX.matchEntire(lines[index])
      if (headingMatch == null) {
        index++
        continue
      }

      val version = headingMatch.groupValues[1]
      val date = headingMatch.groupValues[2]
      index++

      var currentSection: String? = null
      val breakingChanges = mutableListOf<String>()
      val newFeatures = mutableListOf<String>()
      val bugfixes = mutableListOf<String>()

      while (index < lines.size && VERSION_HEADING_REGEX.matchEntire(lines[index]) == null) {
        val line = lines[index]
        when {
          line == BREAKING_CHANGES_HEADING -> currentSection = BREAKING_CHANGES_HEADING
          line == NEW_FEATURES_HEADING -> currentSection = NEW_FEATURES_HEADING
          line == BUGFIXES_HEADING -> currentSection = BUGFIXES_HEADING
          line.startsWith(BULLET_PREFIX) -> when (currentSection) {
            BREAKING_CHANGES_HEADING -> breakingChanges += line.removePrefix(BULLET_PREFIX).trim()
            NEW_FEATURES_HEADING -> newFeatures += line.removePrefix(BULLET_PREFIX).trim()
            BUGFIXES_HEADING -> bugfixes += line.removePrefix(BULLET_PREFIX).trim()
            else -> Unit
          }
          else -> Unit
        }
        index++
      }

      entries += ReleaseNotesEntry(
        version = version,
        date = date,
        minorVersion = minorVersionOf(version),
        breakingChanges = breakingChanges,
        newFeatures = newFeatures,
        bugfixes = bugfixes,
      )
    }

    return entries
      .groupBy { it.minorVersion }
      .map { (minorVersion, groupedEntries) -> ReleaseNotesMinorVersionGroup(minorVersion, groupedEntries) }
  }

  private fun minorVersionOf(version: String): String {
    val segments = version.split(".")
    return if (segments.size >= 2) "${segments[0]}.${segments[1]}" else version
  }
}
