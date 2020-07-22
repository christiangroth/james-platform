package de.chrgroth.restcrud

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets

class CodeGenerator(private val generationRoot: File, private val config: CodeGeneration) {
    private val logger = LoggerFactory.getLogger(CodeGenerator::class.java)

    fun generateModels(imports: List<PackageName>, models: List<Model>, additionalAttributes: List<(Model) -> Attribute>) {

        if (models.isEmpty()) {
            logger.warn("No model defined, skipping!")
            return
        }

        val dataClassesSource = models.joinToString("\n") { renderModel(it, additionalAttributes) }
        val fileContent = renderKotlinFile(imports, dataClassesSource)
        generate("Models.kt", fileContent)
    }

    private fun renderModel(model: Model, additionalAttributeProvider: List<(Model) -> Attribute>): String {
        logger.info("generating data class for $model")

        if (model.attributes.isEmpty()) {
            logger.error("Type ${model.name} has no defined attributes, skipping!")
            return ""
        }

        val additionalAttributes = additionalAttributeProvider.map { it.invoke(model) }.toList()
        return renderModel(model.name, additionalAttributes.plus(model.attributes))
    }

    private fun renderModel(name: String, attributes: List<Attribute>): String {
        val properties = attributes.joinToString(",\n") { renderModelAttribute(it) }
        return render("templates/model/dataClass.ktt", mapOf("name" to name, "properties" to properties))
    }

    private fun renderModelAttribute(attribute: Attribute): String {
        return render("templates/model/dataClassProperty.ktt", mapOf(
                "name" to attribute.name,
                "type" to attribute.type,
                "typeOptionalPostfix" to if(attribute.optional) "?" else ""
        ))
    }

    fun generateControllers(imports: List<PackageName>, endpoints: List<Model>, template: String, genericControllerInclude: String? = null) {
        logger.info("generating api endpoints...")

        if (endpoints.isEmpty()) {
            logger.warn("No rest endpoints defined, skipping!")
            return
        }

        val controllersSource = endpoints.joinToString("\n") { renderController(it, template) }
        val genericCrud = if (genericControllerInclude != null)
            render("templates/controller/$genericControllerInclude", emptyMap())
        else
            ""

        val fileContent = renderKotlinFile(imports, "$controllersSource\n$genericCrud")
        generate("Controllers.kt", fileContent)
    }

    private fun renderController(model: Model, template: String): String {
        logger.info("generating controller object for $model")
        val endpoint = model.endpoint ?: return ""

        return render(
                "templates/controller/$template",
                mapOf("type" to model.name, "endpoint" to endpoint)
        )
    }

    fun generatePersistence(imports: List<PackageName>, endpoints: List<Model>, template: String, persistenceTemplate: String) {
        logger.info("generating persistence...")

        if (endpoints.isEmpty()) {
            logger.warn("No rest endpoints defined, skipping!")
            return
        }

        val methodsSource = endpoints.joinToString("\n") { renderPersistenceMethods(it, template) }
        val persistenceSource = render("templates/persistence/$persistenceTemplate", mapOf("methodsSource" to methodsSource))

        val fileContent = renderKotlinFile(imports, persistenceSource)
        generate("Persistence.kt", fileContent)
    }

    private fun renderPersistenceMethods(model: Model, template: String): String {
        logger.info("generating persistence functions for $model")

        // TODO database name
        // TODO delete filter
        val type = model.name
        val typePlural = "${type}s"
        val typePluralCapitalized = typePlural.capitalize()
        return render("templates/persistence/$template", mapOf(
            "type" to type,
            "typePlural" to typePlural,
            "typePluralCapitalized" to typePluralCapitalized
        ))
    }

    fun generateApplication(imports: List<PackageName>, endpoints: List<Model>, template: String, applicationTemplate: String) {
        logger.info("generating Ktor base application...")

        if (endpoints.isEmpty()) {
            logger.warn("No rest endpoints defined, skipping!")
            return
        }

        val routingCalls = endpoints.joinToString("\n") {renderRoutingCalls(it, template) }
        val applicationSource = render("templates/application/$applicationTemplate", mapOf("routingCalls" to routingCalls))

        val fileContent = renderKotlinFile(imports, applicationSource)
        generate("Application.kt", fileContent)
    }

    private fun renderRoutingCalls(model: Model, template: String): String {
        return render("templates/application/$template", mapOf(
                "type" to model.name
        ))
    }

    private fun renderKotlinFile(imports: List<PackageName>, sources: String): String {
        return render("templates/kotlinFile.ktt", mapOf(
                "package" to config.packageName,
                "imports" to imports.joinToString("\n") { "import ${it.value}" },
                "sources" to sources)
        )
    }

    private fun render(source: String, variables: Map<String, String>) =
            CodeGenerator::class.java.classLoader.getResource(source)
                    .readText(StandardCharsets.UTF_8)
                    .replaceAll(variables)

    private fun String.replaceAll(variables: Map<String, String>): String {
        var result = this
        variables.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        return result
    }

    private fun generate(targetFile: String, content: String) =
            generationRoot.resolve(config.packagePath).apply {
                mkdirs()
                resolve(targetFile).apply {
                    writeText(content, StandardCharsets.UTF_8)
                }
            }
}