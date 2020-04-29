package de.chrgroth.restcrud

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.Yaml
import java.io.File

class GradlePlugin : Plugin<Project> {

    lateinit var genSrcDir: File

    override fun apply(project: Project) {
        project.logger.info("Configuring project $project...")

        project.plugins.getPlugin("kotlin")
            ?: throw IllegalStateException("kotlin plugin must be installed!")

        // TODO not detected as sources dir in Intellij Idea
        genSrcDir = File(project.projectDir, "src/main/rest-crud")
        genSrcDir.mkdirs()

        project.logger.info("adding directory for generated sources...")
        val sourceSets = project.properties["sourceSets"] as SourceSetContainer?
        val srcDirs = sourceSets?.getByName("main")?.allSource?.srcDirs
        srcDirs?.add(genSrcDir)
        project.logger.info("source dirs: $srcDirs")

        val task = project.tasks.create("rest-crud", GenerateTask::class.java)
        project.tasks.getByName("compileKotlin").dependsOn += task
    }

    open class GenerateTask : DefaultTask() {
        private val definitionsFile = File(project.projectDir, "rest-crud.yaml")

        @TaskAction
        fun start() {
            val definition = parse()
            // TODO validate(definition)

            val configuration: Map<String, Object> =
                definition.getOrDefault("configuration", mapOf<String, Object>()) as Map<String, Object>

            generateModels(definition, configuration)
            generateControllers(definition, configuration)
        }

        // TODO typed would be better, but currently we use dynamic property names for datamodel attributes etc
        private fun parse(): Map<String, Object> {
            project.logger.info("Trying to load definitions file from ${definitionsFile.absolutePath}...")
            return if (definitionsFile.exists()) {
                Yaml().load(definitionsFile.inputStream())
            } else {
                mapOf()
            }
        }

        private fun generateModels(
            definition: Map<String, Object>,
            configuration: Map<String, Object>
        ) {
            val datamodel = definition["datamodel"]
            if (datamodel == null || datamodel !is Map<*, *>) {
                project.logger.warn("No datamodel defined, skipping!")
                return
            }

            datamodel.entries.forEach {
                val key = it.key
                val value = it.value
                if (key is String && value is Map<*, *>) {
                    generateModel(configuration, key, value)
                } else {
                    // TODO error handling or ensure validity beforehand!!
                }
            }
        }

        private fun generateModel(configuration: Map<String, Object>, name: String, definition: Map<*, *>) {
            project.logger.info("generating datamodel for $name with $definition")

            val attributes = definition["attributes"]
            if(attributes == null || attributes !is Map<*, *>) {
                project.logger.error("Type $name has no defined attributes, skipping!")
            }

            val attributesMap = (attributes as Map<String, String>)

            val dataClassSoure = """data class ${name.capitalize()} {
                ${attributesMap.map { "val ${it.key}: ${it.value}" }.joinToString(",\n")} 
            }
            """.trimIndent()
            println(dataClassSoure)
            
            /*
            val attributes = definition["attributes"]
            if(attributes == null || attributes !is Map<*, *>) {
                project.logger.error("Type $name has no defined attributes, skipping!")
            }

            val constructorParameters = (attributes as Map<String, String>).map {
                ParameterSpec.builder(it.key, Class.forName(it.value).kotlin).build()
            }

            val properties = (attributes as Map<String, String>).map {
                PropertySpec.builder(it.key, Class.forName(it.value).kotlin).build()
            }

            val typeSpec = TypeSpec.classBuilder(name.capitalize())
                .addModifiers(KModifier.DATA)
                .primaryConstructor(
                    FunSpec.constructorBuilder().addParameters(constructorParameters).build()
                )
                .addProperties(properties)
                .build()

            FileSpec.builder(configuration.getOrDefault("codeGenerationPackage", "") as String, "").addType(typeSpec).build().writeTo(System.out)
            */
        }

        private fun generateControllers(
            definition: Map<String, Object>,
            configuration: Map<String, Object>
        ) {
            val rest = definition["rest"]
            if (rest == null || rest !is Map<*, *>) {
                project.logger.warn("No rest endpoints defined, skipping!")
                return
            }

            rest.keys.forEach { type ->
                project.logger.info("generating rest endpoint for $type")
            }
        }
    }
}