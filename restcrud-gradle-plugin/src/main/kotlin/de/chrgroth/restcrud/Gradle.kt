package de.chrgroth.restcrud

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.yaml.snakeyaml.Yaml
import java.io.File

const val EXTENSION_NAME = "restcrud"

const val TASK_NAME_GENERATE = "restcrudGenerate"

open class RestCrudExtension(project: Project) {

    val genSrcDir = project.buildDir.resolve("gen-src/restcrud")
    val fileGenerator = FileGenerator(genSrcDir)
}

class RestCrudPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.info("Verifying project requirements for $project...")

        if (!project.plugins.hasPlugin("kotlin")) {
            throw IllegalStateException("Gradle kotlin plugin must be applied to project!")
        }

        project.logger.info("Restcrud extension will be available as '$EXTENSION_NAME'.")
        val extension: RestCrudExtension = project.extensions.create(
            EXTENSION_NAME, RestCrudExtension::class.java, project
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

        private val extension = project.extensions.getByName(EXTENSION_NAME) as RestCrudExtension
        private val service = RestCrudService(project.logger, extension.fileGenerator)

        private val definitionsFile = File(project.projectDir, "rest-crud.yaml")


        @TaskAction
        fun start() {
            val definition = service.parse(definitionsFile)
            // TODO service.validate(definition)

            // TODO move to service
            val configuration: Map<String, Object> =
                definition.getOrDefault("configuration", mapOf<String, Object>()) as Map<String, Object>

            service.generateModels(definition, configuration)
            service.generateControllers(definition, configuration)
            service.generatePersistence(definition, configuration)
            service.generateKtor(definition, configuration)
        }
    }
}