package de.chrgroth.gradle.plugins.releasenotes

import org.gradle.kotlin.dsl.support.listFilesOrdered
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

private val logger: Logger = LoggerFactory.getLogger("Releasenotes")

private const val BUGFIX_TEMPLATE_FILE = "bugfix"
private const val FEATURE_TEMPLATE_FILE = "feature"
private const val HIGHLIGHT_TEMPLATE_FILE = "highlight"
private const val UPDATE_NOTICE_TEMPLATE_FILE = "update-notice"
private const val NEXT_VERSION_TEMPLATE_FILE = "next-version"

private const val OUTPUT_FOLDER = "releasenotes"

enum class ReleasenoteSnippetType(val filenamePostfix: String, val nextVersionReplacementVariableName: String) {
  FEATURE("-feature", "features"),
  BUGFIX("-bugfix", "bugfixes"),
  HIGHLIGHT("-highlight", "highlights"),
  UPDATENOTICE("-updateNotice", "updateNotices");
}

class ReleaseNotesProcessor(
  private val name: String,
  private val outputFile: File,
  private val snippetsFolder: File,
  private val templatesFolder: File,
  private val bugfixesHeader: String,
  private val bugfixesFooter: String,
  private val featuresHeader: String,
  private val featuresFooter: String,
  private val highlightsHeader: String,
  private val highlightsFooter: String,
  private val updateNoticesHeader: String,
  private val updateNoticesFooter: String,
  private val dateFormat: String,
  private val preserveWhitespace: Boolean,
  private val buildDir: File,
) {

  private val bugfixTemplate: File
    get() = templatesFolder.resolve(BUGFIX_TEMPLATE_FILE + "." + outputFile.extension)

  private val bugfixTemplateContent: String
    get() = bugfixTemplate.readOrNull()
      ?: "* {gitbranch}: Answer to the ultimate question of life, the universe, and everything."

  private val featureTemplate: File
    get() = templatesFolder.resolve(FEATURE_TEMPLATE_FILE + "." + outputFile.extension)

  private val featureTemplateContent: String
    get() = featureTemplate.readOrNull()
      ?: "* {gitbranch}: Answer to the ultimate question of life, the universe, and everything."

  private val highlightTemplate: File
    get() = templatesFolder.resolve(HIGHLIGHT_TEMPLATE_FILE + "." + outputFile.extension)

  private val highlightTemplateContent: String
    get() = highlightTemplate.readOrNull() ?: "Good news everyone: {gitbranch} is here."

  private val updateNoticwTemplate: File
    get() = templatesFolder.resolve(UPDATE_NOTICE_TEMPLATE_FILE + "." + outputFile.extension)

  private val updateNoticeTemplateContent: String
    get() = updateNoticwTemplate.readOrNull() ?: "Caution, {gitbranch} may eventually break something!"

  private val nextVersionTemplate: File
    get() = templatesFolder.resolve(NEXT_VERSION_TEMPLATE_FILE + "." + outputFile.extension)

  private val nextVersionTemplateContent: String
    get() = nextVersionTemplate.readOrNull() ?: """
            # {version} - {date}
            {highlights}{updateNotices}{features}{bugfixes}
        """.trimIndent()

  private fun File.readOrNull() = if (canRead()) {
    readText()
  } else {
    null
  }

  fun createFolderStructure() {
    outputFile.apply {
      parentFile.mkdirs()
      createNewFile()
    }

    snippetsFolder.mkdirs()
  }

  fun cleanupGeneratedFiles() {
    val targetFolder = resolveTargetFolder()
    if (targetFolder.exists()) {
      targetFolder.deleteRecursively()
    }

    val outputFolder = resolveOutputFolder()
    if (outputFolder.exists() && outputFolder.listFiles().isEmpty()) {
      outputFolder.deleteRecursively()
    }
  }

  fun createTemplatesFiles() {
    bugfixTemplate.createWithText(bugfixTemplateContent)
    featureTemplate.createWithText(featureTemplateContent)
    highlightTemplate.createWithText(highlightTemplateContent)
    updateNoticwTemplate.createWithText(updateNoticeTemplateContent)
    nextVersionTemplate.createWithText(nextVersionTemplateContent)
  }

  fun createBugfix(currentBranch: String) =
    createSnippet(bugfixTemplateContent, currentBranch, ReleasenoteSnippetType.BUGFIX)

  fun createFeature(currentBranch: String) =
    createSnippet(featureTemplateContent, currentBranch, ReleasenoteSnippetType.FEATURE)

  fun createHighlight(currentBranch: String) =
    createSnippet(highlightTemplateContent, currentBranch, ReleasenoteSnippetType.HIGHLIGHT)

  fun createUpdateNotice(currentBranch: String) =
    createSnippet(updateNoticeTemplateContent, currentBranch, ReleasenoteSnippetType.UPDATENOTICE)

  private fun createSnippet(text: String, currentBranch: String, snippet: ReleasenoteSnippetType) {
    logger.info("Creating $snippet for $name")
    val branchPrefix = currentBranch.substringAfterLast("/")
    snippetsFolder
      .resolve(branchPrefix + snippet.filenamePostfix + "." + outputFile.extension)
      .createWithText(text.replace("{gitbranch}", branchPrefix))
  }

  private fun File.createWithText(text: String) {
    parentFile.mkdirs()
    createNewFile()
    writeText(text)
  }

  fun buildReleasenotes(
    skipReleaseNotesOnBranchPrefixes: List<String>,
    branchName: String,
    versionReplacement: String,
  ) {

    val renderedSnippets = mapOf(
      ReleasenoteSnippetType.BUGFIX to renderSnippets(
        ReleasenoteSnippetType.BUGFIX,
        bugfixesHeader,
        bugfixesFooter
      ),
      ReleasenoteSnippetType.FEATURE to renderSnippets(
        ReleasenoteSnippetType.FEATURE,
        featuresHeader,
        featuresFooter
      ),
      ReleasenoteSnippetType.HIGHLIGHT to renderSnippets(
        ReleasenoteSnippetType.HIGHLIGHT,
        highlightsHeader,
        highlightsFooter
      ),
      ReleasenoteSnippetType.UPDATENOTICE to renderSnippets(
        ReleasenoteSnippetType.UPDATENOTICE,
        updateNoticesHeader,
        updateNoticesFooter
      ),
    )

    val noContent = renderedSnippets.values.all { it.isBlank() }
    if (noContent) {
      val skipReleaseNotesAllowed = skipReleaseNotesOnBranchPrefixes.any { branchName.startsWith(it) }
      if (!skipReleaseNotesAllowed) {
        logger.error("Missing release notes snippets, failing build!")
        logger.info("You may opt-out of enforcing release notes by configuring the plugin extension in your build.")
        logger.info("Current branch: $branchName")
        check(false) { "Stopping build due to missing release note snippets!" }
      } else {
        logger.info("Ignoring missing release notes snippets.")
      }

      return
    }

    val targetFile = resolveTargetFile()
    if (!targetFile.exists()) {
      targetFile.mkdirs()
      targetFile.createNewFile()
    }

    if (outputFile.exists()) {
      outputFile.copyTo(targetFile, overwrite = true)
    }

    val nextVersionText = nextVersionTemplateContent.replaceAll(renderedSnippets)
      .replace("{version}", versionReplacement)
      .replace("{date}", SimpleDateFormat(dateFormat).format(Date()))

    targetFile.prepend(nextVersionText)
  }

  private fun renderSnippets(snippetType: ReleasenoteSnippetType, header: String, footer: String): String =
    findSnippetFiles(snippetType).let { snippets ->
      if (snippets.isEmpty()) {
        return ""
      }

      val contents = snippets.joinToString("") { snippetFile ->
        snippetFile.readText().let { if (preserveWhitespace) it else it.trim() }
      }

      "$header\n$contents\n$footer"
    }

  private fun findSnippetFiles(snippetType: ReleasenoteSnippetType) =
    snippetsFolder.listFilesOrdered { it.detectReleasenoteSnippetType() == snippetType }

  private fun File.detectReleasenoteSnippetType() = when {
    name.endsWith(ReleasenoteSnippetType.FEATURE.filenamePostfix + "." + outputFile.extension) -> ReleasenoteSnippetType.FEATURE
    name.endsWith(ReleasenoteSnippetType.BUGFIX.filenamePostfix + "." + outputFile.extension) -> ReleasenoteSnippetType.BUGFIX
    name.endsWith(ReleasenoteSnippetType.HIGHLIGHT.filenamePostfix + "." + outputFile.extension) -> ReleasenoteSnippetType.HIGHLIGHT
    name.endsWith(ReleasenoteSnippetType.UPDATENOTICE.filenamePostfix + "." + outputFile.extension) -> ReleasenoteSnippetType.UPDATENOTICE
    else -> null
  }

  private fun String.replaceAll(placeholders: Map<ReleasenoteSnippetType, String>): String =
    placeholders.entries.fold(this) { result, snippets ->
      result.replace("{${snippets.key.nextVersionReplacementVariableName}}", snippets.value)
    }

  private fun File.prepend(nextVersionText: String) {
    writeText(nextVersionText + readText())
  }

  fun copyBuiltReleaseNotesToSources() {
    val targetFile = resolveTargetFile()
    if (!targetFile.exists()) {
      logger.error("Unable to copy built releasenotes back to sources, no target file found: ${targetFile.absolutePath}!")
      return
    }

    targetFile.copyTo(outputFile, overwrite = true)
  }

  private fun resolveTargetFile() = resolveTargetFolder().resolve(outputFile.absolutePath.substringAfterLast("/"))

  private fun resolveTargetFolder() = resolveOutputFolder().resolve(name)

  private fun resolveOutputFolder() = buildDir.resolve(OUTPUT_FOLDER)

  fun deleteSnippets() {
    snippetsFolder.deleteRecursively()
  }
}
