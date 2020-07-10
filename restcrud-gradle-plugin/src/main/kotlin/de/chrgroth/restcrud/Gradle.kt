package de.chrgroth.restcrud

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

// TODO change handling of gradle project? what to add automatically and what to expect??
// TODO tests for gradle stuff

const val EXTENSION_NAME = "restcrud"
const val TASK_NAME_GENERATE = "restcrudGenerate"

open class GradleExtension(project: Project) {
    val genSrcDir = project.buildDir.resolve("gen-src/restcrud")
    val fileGenerator = FileGenerator(genSrcDir)
}

class GradlePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.info("Verifying project requirements for $project...")

        if (!project.plugins.hasPlugin("kotlin")) {
            throw IllegalStateException("Gradle kotlin plugin must be applied to project!")
        }

        project.logger.info("Restcrud extension will be available as '$EXTENSION_NAME'.")
        val extension: GradleExtension = project.extensions.create(
            EXTENSION_NAME, GradleExtension::class.java, project
        )

        project.logger.info("Creating task $TASK_NAME_GENERATE...")
        val task = project.tasks.create(TASK_NAME_GENERATE, GenerateTask::class.java)
        project.tasks.getByName("compileKotlin").dependsOn += task

        project.afterEvaluate {
            project.logger.info("Configuring kotlin main source set to include generated sources directory ${extension.genSrcDir.absolutePath}...")

            val sourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)
            val genericMainSourceSet = sourceSetContainer.getByName("main")
            val kotlinSourceSet = (genericMainSourceSet as HasConvention).convention.getPlugin(KotlinSourceSet::class.java)
            kotlinSourceSet.kotlin.srcDir(extension.genSrcDir)
        }
    }

    open class GenerateTask : DefaultTask() {

        private val extension = project.extensions.getByName(EXTENSION_NAME) as GradleExtension
        private val service = Service(extension.fileGenerator)

        private val definitionsFile = File(project.projectDir, "rest-crud.yaml")


        @TaskAction
        fun start() {
            val configuration = YamlUtils.load(definitionsFile)
            service.generateModels(configuration)
            service.generateControllers(configuration)
            service.generatePersistence(configuration)
            service.generateKtor(configuration)
        }
    }
}