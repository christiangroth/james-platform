package de.chrgroth.gradle.plugins.releasenotes

import de.chrgroth.gradle.plugins.createWithText
import de.chrgroth.gradle.plugins.prepend
import de.chrgroth.gradle.plugins.readOrNull
import de.chrgroth.gradle.plugins.replaceAll
import org.gradle.api.Project
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

enum class ReleasenoteSnippetType(val filenamePostfix: String, val nextVersionReplacementVariableName: String) {
    FEATURE("-feature", "features"),
    BUGFIX("-bugfix", "bugfixes"),
    HIGHLIGHT("-highlight", "highlights"),
    UPDATENOTICE("-updateNotice", "updateNotices");
}

class ReleaseNotesProcessor(
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
) {

    private val bugfixTemplate: File
        get() = templatesFolder.resolve(BUGFIX_TEMPLATE_FILE)

    private val bugfixTemplateContent: String
        get() = bugfixTemplate.readOrNull()
            ?: "* {gitbranch}: Answer to the ultimate question of life, the universe, and everything."

    private val featureTemplate: File
        get() = templatesFolder.resolve(FEATURE_TEMPLATE_FILE)

    private val featureTemplateContent: String
        get() = featureTemplate.readOrNull()
            ?: "* {gitbranch}: Answer to the ultimate question of life, the universe, and everything."

    private val highlightTemplate: File
        get() = templatesFolder.resolve(HIGHLIGHT_TEMPLATE_FILE)

    private val highlightTemplateContent: String
        get() = highlightTemplate.readOrNull() ?: "Good news everyone: {gitbranch} is here."

    private val updateNoticwTemplate: File
        get() = templatesFolder.resolve(UPDATE_NOTICE_TEMPLATE_FILE)

    private val updateNoticeTemplateContent: String
        get() = updateNoticwTemplate.readOrNull() ?: "Caution, {gitbranch} may eventually break something!"

    private val nextVersionTemplate: File
        get() = templatesFolder.resolve(NEXT_VERSION_TEMPLATE_FILE)

    private val nextVersionTemplateContent: String
        get() = nextVersionTemplate.readOrNull() ?: """
            |# {version} - {date}
            |{highlights}{updateNotices}{features}{bugfixes}
        """.trimIndent()

    fun createFolderStructure() {
        outputFile.apply {
            parentFile.mkdirs()
            createNewFile()
        }

        snippetsFolder.mkdirs()
    }

    fun createTemplatesFiles() {
        bugfixTemplate.createWithText(bugfixTemplateContent)
        featureTemplate.createWithText(featureTemplateContent)
        highlightTemplate.createWithText(highlightTemplateContent)
        updateNoticwTemplate.createWithText(updateNoticeTemplateContent)
        nextVersionTemplate.createWithText(nextVersionTemplateContent)
    }

    fun createBugfix(branch: String) =
        createSnippet(bugfixTemplateContent, branch, ReleasenoteSnippetType.BUGFIX)

    fun createFeature(branch: String) =
        createSnippet(featureTemplateContent, branch, ReleasenoteSnippetType.FEATURE)

    fun createHighlight(branch: String) =
        createSnippet(highlightTemplateContent, branch, ReleasenoteSnippetType.HIGHLIGHT)

    fun createUpdateNotice(branch: String) =
        createSnippet(updateNoticeTemplateContent, branch, ReleasenoteSnippetType.UPDATENOTICE)

    private fun createSnippet(text: String, branch: String, snippet: ReleasenoteSnippetType) {
        val branch = branch.substringAfterLast("/")
        snippetsFolder
            .resolve(branch + snippet.filenamePostfix + outputFile.extension)
            .createWithText(text.replace("{gitbranch}", branch))
    }

    fun buildReleasenotes(project: Project) {
        val bugfixes = renderSnippets(ReleasenoteSnippetType.BUGFIX, bugfixesHeader, bugfixesFooter)
        val features = renderSnippets(ReleasenoteSnippetType.FEATURE, featuresHeader, featuresFooter)
        val highlights = renderSnippets(ReleasenoteSnippetType.HIGHLIGHT, highlightsHeader, highlightsFooter)
        val updateNotices =
            renderSnippets(ReleasenoteSnippetType.UPDATENOTICE, updateNoticesHeader, updateNoticesFooter)

        val renderedSnippetsAsVariables = mapOf(
            ReleasenoteSnippetType.BUGFIX to features,
            ReleasenoteSnippetType.FEATURE to bugfixes,
            ReleasenoteSnippetType.HIGHLIGHT to highlights,
            ReleasenoteSnippetType.UPDATENOTICE to updateNotices,
        )

        if (project.enforceReleasenotes() && renderedSnippetsAsVariables.values.all { it.isBlank() }) {
            logger.error("No release note snippets found, failing build!")
            logger.info("You may opt-out of enforcing release notes by configuring the plugin extension in your build.")
            throw IllegalStateException("Stopping build due to missing release note snippets!")
        }

        // TODO fix this file resolving
        val releaseNotesFileInBuild = project.buildDir.resolve(outputFile)
        // templateFiles.releasenotes.parentFile.copyRecursively(releaseNotesFileInBuild.parentFile, overwrite = true)

        val nextVersionText = nextVersionTemplateContent.replaceAll(renderedSnippetsAsVariables)
            .replace("{version}", project.version.toString())
            .replace("{date}", SimpleDateFormat(dateFormat).format(Date()))

        releaseNotesFileInBuild.prepend(nextVersionText)
    }

    private fun renderSnippets(snippetType: ReleasenoteSnippetType, header: String, footer: String): String =
        snippetsFolder.listFilesOrdered { it.detectReleasenoteSnippetType() == snippetType }.let { snippets ->
            if (snippets.isEmpty()) {
                return ""
            }

            val contents = snippets.joinToString("") {
                it.readText().trim() + "\n\n"
            }

            "$header\n$contents$footer"
        }

    private fun File.detectReleasenoteSnippetType() = when {
        name.endsWith(ReleasenoteSnippetType.FEATURE.filenamePostfix + outputFile.extension) -> ReleasenoteSnippetType.FEATURE
        name.endsWith(ReleasenoteSnippetType.BUGFIX.filenamePostfix + outputFile.extension) -> ReleasenoteSnippetType.BUGFIX
        name.endsWith(ReleasenoteSnippetType.HIGHLIGHT.filenamePostfix + outputFile.extension) -> ReleasenoteSnippetType.HIGHLIGHT
        name.endsWith(ReleasenoteSnippetType.UPDATENOTICE.filenamePostfix + outputFile.extension) -> ReleasenoteSnippetType.UPDATENOTICE
        else -> null
    }

    private fun Project.enforceReleasenotes() = !currentBranch.contains(
        (extensions.findByType(ReleasenotesExtension::class.java)
            ?: throw IllegalStateException("Please add a releasenotes extension to your project!")).mainBranch
    )
}