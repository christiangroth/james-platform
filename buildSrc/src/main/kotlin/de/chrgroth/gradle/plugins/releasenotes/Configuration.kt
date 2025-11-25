package de.chrgroth.gradle.plugins.releasenotes

import org.gradle.api.Project
import java.io.File

// TODO would be great to have a single source of truth and multiple renderers
// TODO render to sourcesDir directly to always have the latest version available (i.e. for quarkus dev mode)

data class ReleasenotesConfiguration(
  val name: String,
  val outputPath: String,
  val snippetsPath: String,
  val templatesPath: String,
  val bugfixesHeader: String,
  val bugfixesFooter: String,
  val featuresHeader: String,
  val featuresFooter: String,
  val highlightsHeader: String,
  val highlightsFooter: String,
  val updateNoticesHeader: String,
  val updateNoticesFooter: String,
  val dateFormat: String,
  val preserveWhitespace: Boolean,
) {
  fun init(projectDir: File, buildDir: File): ReleaseNotesProcessor = ReleaseNotesProcessor(
    name = name,
    outputFile = projectDir.resolve(outputPath),
    snippetsFolder = projectDir.resolve(snippetsPath),
    templatesFolder = projectDir.resolve(templatesPath),
    bugfixesHeader = bugfixesHeader,
    bugfixesFooter = bugfixesFooter,
    featuresHeader = featuresHeader,
    featuresFooter = featuresFooter,
    highlightsHeader = highlightsHeader,
    highlightsFooter = highlightsFooter,
    updateNoticesHeader = updateNoticesHeader,
    updateNoticesFooter = updateNoticesFooter,
    dateFormat = dateFormat,
    preserveWhitespace = preserveWhitespace,
    buildDir = buildDir,
  )
}

class ReleasenotesExtension(val project: Project) {
  var mainBranch = "main"
  var skipReleaseNotesOnBranchPrefixes: List<String> = emptyList()
  internal val configurations: MutableList<ReleasenotesConfiguration> = mutableListOf()

  fun configure(provider: () -> ReleasenotesConfiguration) {
    configurations.add(provider())
  }
}
