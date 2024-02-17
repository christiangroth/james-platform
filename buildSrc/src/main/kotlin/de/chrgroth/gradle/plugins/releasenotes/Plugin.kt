package de.chrgroth.gradle.plugins.releasenotes

import de.chrgroth.gradle.plugins.releasenotes.ProjectVersion.Companion.toProjectVersion
import org.ajoberstar.grgit.Grgit
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

const val EXTENSION_NAME = "releasenotes"
const val TASK_GROUP_NAME = "releasenotes"

// TODO add clean task and remove build files
class ReleasenotesPlugin : Plugin<Project> {
    private lateinit var extension: ReleasenotesExtension

    override fun apply(project: Project) {
        extension = ReleasenotesExtension(project).apply {
            project.extensions.add(EXTENSION_NAME, this)
        }

        project.run {

            tasks.register(init) {
                group = TASK_GROUP_NAME

                doLast {
                    extension.configurations.forEach {
                        it.init(projectDir).createFolderStructure()
                    }
                }
            }

            val assembleOrNull = tasks.findByName("assemble")
            assembleOrNull?.apply {
                logger.info("Task with path 'assemble' found, will depend on $init")
                dependsOn(init)
            }

            tasks.register(createTemplates) {
                group = TASK_GROUP_NAME

                doLast {
                    extension.configurations.forEach {
                        it.init(projectDir).createTemplatesFiles()
                    }
                }
            }

            tasks.register(createFeature) {
                group = TASK_GROUP_NAME
                dependsOn(init)

                doLast {
                    extension.configurations.forEach {
                        it.init(projectDir).createFeature(currentBranch)
                    }

                    project.changeProjectVersion { mainBranchProjectVersion, currentProjectVersion ->
                        if (mainBranchProjectVersion != currentProjectVersion) {
                            logger.info("Skipping version bump because version already differs from ${extension.mainBranch} branch: $mainBranchProjectVersion vs $currentProjectVersion")
                            null
                        } else {
                            currentProjectVersion.copy(minor = currentProjectVersion.minor + 1, patch = 0)
                        }
                    }
                }
            }

            tasks.register(createBugfix) {
                group = TASK_GROUP_NAME
                dependsOn(init)

                doLast {
                    extension.configurations.forEach {
                        it.init(projectDir).createBugfix(currentBranch)
                    }
                }
            }

            tasks.register(createHighlight) {
                group = TASK_GROUP_NAME
                dependsOn(init)

                doLast {
                    extension.configurations.forEach {
                        it.init(projectDir).createHighlight(currentBranch)
                    }
                }
            }

            tasks.register(createUpdateNotice) {
                group = TASK_GROUP_NAME
                dependsOn(init)

                doLast {
                    extension.configurations.forEach {
                        it.init(projectDir).createUpdateNotice(currentBranch)
                    }

                    project.changeProjectVersion { mainBranchProjectVersion, currentProjectVersion ->
                        if (mainBranchProjectVersion.major != currentProjectVersion.major) {
                            logger.info("Skipping version bump because version major part already differs from ${extension.mainBranch} branch: $mainBranchProjectVersion vs $currentProjectVersion")
                            null
                        } else {
                            currentProjectVersion.copy(major = currentProjectVersion.major + 1, minor = 0, patch = 0)
                        }
                    }
                }
            }

            tasks.register(buildReleasenotes) {
                group = TASK_GROUP_NAME

                doLast {
                    extension.configurations.forEach {

                        val projectVersion = project.version.toString().toProjectVersion(null)
                        if (projectVersion == null) {
                            logger.error("Unable to parse project version, skipping releasenotes build!")
                        } else {
                            it.init(projectDir).buildReleasenotes(
                                enforceOnNonMainBranch = extension.enforceOnNonMainBranch,
                                mainBranch = extension.mainBranch,
                                branch = currentBranch,
                                buildDir = buildDir,
                                versionReplacement = projectVersion.toString(),
                            )
                        }
                    }
                }
            }.let { buildReleasenotesTask ->
                assembleOrNull?.let { assembleTask ->
                    logger.info("Task '${assembleTask.name}' found, will depend on ${buildReleasenotesTask.name}")
                    assembleTask.dependsOn(buildReleasenotesTask)
                }
            }

            tasks.register(copyBuiltReleaseNotesToSources) {
                group = TASK_GROUP_NAME
                dependsOn(buildReleasenotes)

                doLast {
                    extension.configurations.forEach {
                        it.init(projectDir).copyBuiltReleaseNotesToSources(project.buildDir)
                    }
                }
            }.let { copyBuiltReleaseNotesToSourcesTask ->
                afterEvaluate {
                    tasks.findByPath(afterReleaseBuildTaskName)?.let { afterReleaseBuildTask ->
                        logger.info("Task '${afterReleaseBuildTask.path}' found, will depend on ${copyBuiltReleaseNotesToSourcesTask.name}")
                        afterReleaseBuildTask.dependsOn(copyBuiltReleaseNotesToSourcesTask)
                    }
                }
            }

            tasks.register(deleteSnippets) {
                group = TASK_GROUP_NAME
                mustRunAfter(buildReleasenotes, copyBuiltReleaseNotesToSources)

                doLast {
                    extension.configurations.forEach {
                        it.init(projectDir).deleteSnippets()
                    }
                }
            }.let { deleteReleasenotesTask ->
                afterEvaluate {
                    tasks.findByPath(afterReleaseBuildTaskName)?.let { afterReleaseBuildTask ->
                        logger.info("Task '${afterReleaseBuildTask.path}' found, will depend on ${deleteReleasenotesTask.name}")
                        afterReleaseBuildTask.dependsOn(deleteReleasenotesTask)
                    }
                }
            }
        }
    }

    private val Project.currentBranch
        get() = Grgit.open(mapOf("currentDir" to rootDir)).branch.current().name.substringAfterLast("/")

    private fun Project.changeProjectVersion(newVersionProvider: (ProjectVersion, ProjectVersion) -> ProjectVersion?) {
        val mainBranchProjectVersion =
            runToString(rootDir, "git", "show", "${extension.mainBranch}:gradle.properties").parseVersion()
        if (mainBranchProjectVersion == null) {
            logger.warn("Could not parse project version from gradle.properties of ${extension.mainBranch} branch. Skipping bump of minor version.")
            return
        }

        val gradlePropertiesFile = rootDir.resolve("gradle.properties")
        if (!gradlePropertiesFile.exists()) {
            logger.warn("Failed to find gradle.properties in project root. Skipping bump of minor version.")
            return
        }

        val gradlePropertiesContent = gradlePropertiesFile.readText()
        val projectVersion = gradlePropertiesContent.parseVersion()
        if (projectVersion == null) {
            logger.warn("Could not parse project version from gradle.properties of current branch. Skipping bump of minor version.")
            return
        }

        // Only bumping version if not already changed on current branch.
        val newVersion = newVersionProvider(mainBranchProjectVersion, projectVersion)
        if(newVersion != null) {
            gradlePropertiesFile.writeText(
                gradlePropertiesContent.replace(
                    regex = Regex("version=.*"),
                    replacement = "version=$newVersion"
                )
            )
            logger.info("Bumped project version to: $newVersion")
        }
    }

    private fun Project.runToString(workingDirectory: File, vararg cmd: String): String {
        return try {
            val proc = ProcessBuilder(cmd.toList())
                .directory(workingDirectory)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
            proc.waitFor(5, TimeUnit.SECONDS)

            val errorOutput = String(proc.errorStream.use { it.readAllBytes() })
            if(errorOutput.isNotEmpty()) {
                System.err.println(errorOutput)
            }

            String(proc.inputStream.use { it.readAllBytes() })
        } catch (e: java.io.IOException) {
            logger.error("Unable to execute command $cmd in ${workingDirectory.absolutePath}", e)
            ""
        }
    }

    private fun String.parseVersion(): ProjectVersion? {
        val projectVersionString = Regex("version=(.*)").find(this)?.groupValues?.get(1)
        return if (projectVersionString == null) {
            null
        } else {
            ProjectVersion(projectVersionString, null)
        }
    }

    companion object {
        private const val afterReleaseBuildTaskName = ":afterReleaseBuild"

        const val init = "init"
        const val createTemplates = "createTemplates"

        const val createFeature = "createFeature"
        const val createBugfix = "createBugfix"
        const val createHighlight = "createHighlight"
        const val createUpdateNotice = "createUpdateNotice"

        const val buildReleasenotes = "buildReleasenotes"
        const val copyBuiltReleaseNotesToSources = "copyBuiltReleaseNotesToSources"
        const val deleteSnippets = "deleteSnippets"
    }
}

data class ProjectVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val addition: String,
    val ticketId: String?
) {

    operator fun compareTo(other: ProjectVersion): Int {
        val majorComparison = major.compareTo(other.major)
        val minorComparison = minor.compareTo(other.minor)
        val patchComparison = patch.compareTo(other.patch)

        return when {
            majorComparison != 0 -> majorComparison
            minorComparison != 0 -> minorComparison
            patchComparison != 0 -> patchComparison
            else -> 0
        }
    }

    override fun toString() = "$major.$minor.$patch${ticketId?.let { "-$it" } ?: ""}$addition"

    companion object {

        private val logger: Logger = LoggerFactory.getLogger("Releasenotes")
        private val versionExtractor: Pattern = Pattern.compile("""([0-9]+)(?:.([0-9]+))?(?:.([0-9]+))?([0-9.a-zA-Z-+_]*)""")

        fun String.toProjectVersion(ticketId: String? = null) = invoke(this, ticketId)

        operator fun invoke(projectVersion: String, ticketId: String? = null): ProjectVersion? {
            val matcher = versionExtractor.matcher(projectVersion).apply { find() }
            return try {
                ProjectVersion(
                    major = matcher.group(1).toInt(),
                    minor = matcher.group(2).toInt(),
                    patch = matcher.group(3).toInt(),
                    addition = if (matcher.groupCount() > 3) matcher.group(4) else "",
                    ticketId = ticketId
                )
            } catch (e: Exception) {
                logger.error("Unable to parse ProjectVersion from $projectVersion")
                null
            }
        }
    }
}
