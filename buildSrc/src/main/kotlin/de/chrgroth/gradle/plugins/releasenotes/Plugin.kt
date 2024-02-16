package de.chrgroth.gradle.plugins.releasenotes

import de.chrgroth.gradle.plugins.ProjectVersion
import de.chrgroth.gradle.plugins.runToString
import org.ajoberstar.grgit.Grgit
import org.gradle.api.Plugin
import org.gradle.api.Project

const val EXTENSION_NAME = "releasenotes"
const val TASK_GROUP_NAME = "releasenotes"

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
                    project.tryBumpMinorVersion()
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
                }
            }

            tasks.register(buildReleasenotes) {
                group = TASK_GROUP_NAME

                doLast {
                    extension.configurations.forEach {
                        it.init(projectDir).buildReleasenotes(project)
                    }
                }
            }.let { buildReleasenotesTask ->
                assembleOrNull?.let { assembleTask ->
                    logger.info("Task '${assembleTask.name}' found, will depend on ${buildReleasenotesTask.name}")
                    assembleTask.dependsOn(buildReleasenotesTask)
                }
            }

            // TODO inline
            registerCopyBuiltReleaseNotesToSources()
            registerDeleteReleasenotes()
        }
    }

    private fun Project.tryBumpMinorVersion() {
        val mainBranchProjectVersion = runToString(rootDir, "git", "show", "${extension.mainBranch}:gradle.properties").parseVersion()
        if(mainBranchProjectVersion == null) {
            logger.warn("Could not parse project version from gradle.properties of ${extension.mainBranch} branch. Skipping bump of minor version.")
            return
        }

        val gradlePropertiesFile = rootDir.resolve("gradle.properties")
        if(!gradlePropertiesFile.exists()) {
            logger.warn("Failed to find gradle.properties in project root. Skipping bump of minor version.")
            return
        }

        val gradlePropertiesContent = gradlePropertiesFile.readText()
        val projectVersion = gradlePropertiesContent.parseVersion()
        if(projectVersion == null) {
            logger.warn("Could not parse project version from gradle.properties of current branch. Skipping bump of minor version.")
            return
        }

        // Only bumping version if not already changed on current branch.
        if(mainBranchProjectVersion == projectVersion) {
            val newVersion = mainBranchProjectVersion.copy(minor = mainBranchProjectVersion.minor + 1, patch = 0)
            gradlePropertiesFile.writeText(gradlePropertiesContent.replaceVersion(newVersion))
            logger.info("Bumped minor version to '$newVersion'.")
        } else {
            logger.info("Skipping bump of minor version because project version already differs from ${extension.mainBranch} branch.")
        }
    }

    private fun String.parseVersion(): ProjectVersion? {
        val projectVersionString = Regex("version=(.*)").find(this)?.groupValues?.get(1)
        return if(projectVersionString == null) {
            null
        } else {
            ProjectVersion(projectVersionString, null)
        }
    }

    private fun String.replaceVersion(newVersion: ProjectVersion): String {
        return replace(Regex("version=.*"), "version=$newVersion")
    }

    private fun Project.registerCopyBuiltReleaseNotesToSources() {
        tasks.register(copyBuiltReleaseNotesToSources) {
            group = TASK_GROUP_NAME
            dependsOn(buildReleasenotes)

            doLast {
                extension.configurations.forEach {
                    project.buildDir.resolve(it.paths.output)
                        .copyTo(projectDir.resolve(it.paths.output), overwrite = true)
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
    }

    private fun Project.registerDeleteReleasenotes() {
        tasks.register(deleteReleasenotes) {
            group = TASK_GROUP_NAME
            mustRunAfter(buildReleasenotes, copyBuiltReleaseNotesToSources)

            doLast {
                extension.deleteOldFiles()
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

    private fun ReleasenotesExtension.deleteOldFiles() {
        configurations.forEach {
            it.deleteOldFiles(project)
        }
    }

    private fun ReleasenotesConfiguration.deleteOldFiles(project: Project) {
        val templateFiles = paths.resolveFilesIn(project.projectDir)
        templateFiles.nextReleasenotesFolder.apply {
            if (exists()) {
                deleteRecursively()
            }
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
        const val deleteReleasenotes = "deleteReleasenotes"
        const val copyBuiltReleaseNotesToSources = "copyBuiltReleaseNotesToSources"
    }
}

internal val Project.currentBranch
    get() = Grgit.open(mapOf("currentDir" to rootDir)).branch.current().name.substringAfterLast("/")
