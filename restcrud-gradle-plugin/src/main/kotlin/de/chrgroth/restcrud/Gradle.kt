package de.chrgroth.restcrud

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

const val EXTENSION_NAME = "restcrud"
const val TASK_NAME_GENERATE = "restcrudGenerate"

sealed class PersistenceFramework {
    object KMongo: PersistenceFramework()
}

sealed class ApplicationFramework {
    object Ktor: ApplicationFramework()
}

open class RestcrudExtension(project: Project) {
    private val project = project

    val definitionsFilename = "restcrud.yaml"
    val genSrcDir = "gen-src/restcrud"

    fun resolveGenSrcDir() = project.buildDir.resolve(genSrcDir)

    val persistenceFramework = PersistenceFramework.KMongo
    val applicationFramework = ApplicationFramework.Ktor

    val versionKtor = "1.3.0"
    val versionKMongo = "3.12.+"
    val versionLogback = "1.2.1"
    val versionJacksonKotlin = "2.10.+"
}

// TODO also generate test sources to ensure generated source are running like expected!
class RestcrudPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.info("Verifying project requirements for $project...")

        project.logger.info("Restcrud extension will be available as '$EXTENSION_NAME'.")
        val extension: RestcrudExtension = project.extensions.create(
                EXTENSION_NAME, RestcrudExtension::class.java, project
        )

        // TODO test this
        if (!project.plugins.hasPlugin("kotlin")) {
            project.plugins.apply("kotlin")
        }

        // TODO test this
        project.repositories.apply {
            mavenCentral()
            jcenter()
        }

        when(extension.applicationFramework) {
            ApplicationFramework.Ktor -> {

                // TODO test this
                if (!project.plugins.hasPlugin("application")) {
                    project.plugins.apply("application")
                }

                // TODO test this
                project.extensions.getByType(JavaApplication::class.java).apply {
                    mainClassName = "io.ktor.server.netty.EngineMain"
                }

                // TODO test this
                project.logger.info("Adding needed Ktor dependencies ...")
                project.dependencies.apply {
                    add("implementation", "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${extension.versionKtor}")
                    add("implementation", "io.ktor:ktor-server-netty:${extension.versionKtor}")
                    add("implementation", "io.ktor:ktor-server-core:${extension.versionKtor}")
                    add("implementation", "io.ktor:ktor-locations:${extension.versionKtor}")
                    add("implementation", "io.ktor:ktor-metrics:${extension.versionKtor}")
                    add("implementation", "io.ktor:ktor-server-host-common:${extension.versionKtor}")
                    add("implementation", "io.ktor:ktor-jackson:${extension.versionKtor}")

                    add("implementation", "ch.qos.logback:logback-classic:${extension.versionLogback}")
                    add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin:${extension.versionJacksonKotlin}")

                    add("testImplementation", "io.ktor:ktor-server-tests:${extension.versionKtor}")
                }
            }
        }

        when(extension.persistenceFramework) {
            PersistenceFramework.KMongo -> {

                // TODO test this
                project.logger.info("Adding needed KMongo dependencies ...")
                project.dependencies.apply {
                    add("implementation", "org.litote.kmongo:kmongo:${extension.versionKMongo}")
                }
            }
        }

        project.logger.info("Creating task $TASK_NAME_GENERATE...")
        val task = project.tasks.create(TASK_NAME_GENERATE, GenerateTask::class.java)
        project.tasks.getByName("compileKotlin").dependsOn += task

        project.afterEvaluate {
            project.logger.info("Configuring kotlin main source set to include generated sources directory ${extension.resolveGenSrcDir().absolutePath}...")

            val sourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)
            val genericMainSourceSet = sourceSetContainer.getByName("main")
            val kotlinSourceSet = (genericMainSourceSet as HasConvention).convention.getPlugin(KotlinSourceSet::class.java)
            kotlinSourceSet.kotlin.srcDir(extension.resolveGenSrcDir())
        }
    }

    open class GenerateTask : DefaultTask() {

        private val extension = project.extensions.getByName(EXTENSION_NAME) as RestcrudExtension

        init {
            group = "restcrud"
            description = "Generates all sources based on restcrud definitions."
        }

        @TaskAction
        fun start() {
            val definitionsFile = File(project.projectDir, extension.definitionsFilename)
            val configuration = YamlUtils.load(definitionsFile)

            val generationRoot = extension.resolveGenSrcDir()
            val fileGenerator = FileGenerator(generationRoot)
            val service = Service(extension.applicationFramework, extension.persistenceFramework, fileGenerator)
            
            service.generateModels(configuration)
            service.generateControllers(configuration)
            service.generatePersistence(configuration)
            service.generateKtor(configuration)
        }
    }
}