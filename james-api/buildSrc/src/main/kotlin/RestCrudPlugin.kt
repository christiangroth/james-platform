package de.chrgroth.restcrud

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.charset.StandardCharsets

const val EXTENSION_NAME = "restCrud"

open class RestCrudExtension(project: Project) {

    // TODO change to separate source folder
    // TODO why is it detected as sources dir in Intellij??
    val genSrcDir = File(project.projectDir, "src/main/kotlin")

    init {
        genSrcDir.mkdirs()
    }

    fun generateFile(folderPath: String, filename: String, content: String) {

        val folder = File(genSrcDir, folderPath)
        folder.mkdirs()

        val file = File(folder, filename)
        file.writeText(content, StandardCharsets.UTF_8)
    }
}

class GradlePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.info("Configuring project $project...")

        project.plugins.getPlugin("kotlin") ?: project.plugins.apply("kotlin")

        val extension: RestCrudExtension = project.extensions.create(
            EXTENSION_NAME,
            RestCrudExtension::class.java, project
        )

        project.logger.info("adding directory for generated sources...")
        val sourceSets = project.properties["sourceSets"] as SourceSetContainer?
        val srcDirs = sourceSets?.getByName("main")?.allSource?.srcDirs
        srcDirs?.add(extension.genSrcDir)
        project.logger.info("source dirs: $srcDirs")

        val task = project.tasks.create("rest-crud", GenerateTask::class.java)
        project.tasks.getByName("compileKotlin").dependsOn += task
    }

    open class GenerateTask : DefaultTask() {

        private val extension = project.extensions.getByName(EXTENSION_NAME) as RestCrudExtension
        private val definitionsFile = File(project.projectDir, "rest-crud.yaml")

        @TaskAction
        fun start() {
            val definition = parse()
            // TODO validate(definition)

            val configuration: Map<String, Object> =
                definition.getOrDefault("configuration", mapOf<String, Object>()) as Map<String, Object>

            generateModels(definition, configuration)
            generateControllers(definition, configuration)
            generatePersistence(definition, configuration)
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

        private fun generateModels(definition: Map<String, Object>, configuration: Map<String, Object>) {
            val datamodel = definition["datamodel"]
            if (datamodel == null || datamodel !is Map<*, *>) {
                project.logger.warn("No datamodel defined, skipping!")
                return
            }

            val dataClassesSource = datamodel.entries.map {
                val key = it.key
                val value = it.value
                if (key is String && value is Map<*, *>) {
                    generateModelSource(key, value)
                } else {
                    // TODO error handling or ensure validity beforehand!!
                    ""
                }
            }.joinToString("\n")

            // TODO validate!!
            val packageDefinition = configuration["codeGenerationPackage"] as String

            // TODO duplicate code
            val folderPath = packageDefinition.replace('.', '/')
            val modelsSource = "package $packageDefinition\n\n$dataClassesSource"
            extension.generateFile(folderPath, "Models.kt", modelsSource)
        }

        private fun generateModelSource(name: String, definition: Map<*, *>): String {
            project.logger.info("generating datamodel for $name with $definition")

            val attributes = definition["attributes"]
            if (attributes == null || attributes !is Map<*, *>) {
                project.logger.error("Type $name has no defined attributes, skipping!")
            }

            val attributesMap = (attributes as Map<String, String>)

            return """|data class ${name.capitalize()}(
            |    ${attributesMap.map { "val ${it.key}: ${it.value}" }.joinToString(",\n|    ")} 
            |)
            |""".trimMargin()
        }

        private fun generateControllers(definition: Map<String, Object>, configuration: Map<String, Object>) {
            val rest = definition["rest"]
            if (rest == null || rest !is Map<*, *>) {
                project.logger.warn("No rest endpoints defined, skipping!")
                return
            }

            val genericSource = """const val API_PREFIX = "/api"
                
                data class ResponseError(val code: Int, val message: String, val details: String? = null)
                suspend fun ApplicationCall.fail(code: HttpStatusCode, message: String, details: String? = null) =
                    respond(code, ResponseError(code.value, message, details))

                // TODO i18n -> load correct value from entity for localized values
                // TODO returns 404 instead of 405 if route exist but with different method
                abstract class GenericCrudController<Type : Any, Id : Any>(private val type: KClass<Type>, pathPrefix: String) {
                    private val itemIdPathParameterName = "itemId"
                    private val pathAllItems = "${'$'}API_PREFIX/${'$'}pathPrefix"
                    private val pathSingleItem = "${'$'}API_PREFIX/${'$'}pathPrefix/{${'$'}itemIdPathParameterName}"

                    open fun routes(routing: Routing) = with(routing) {
                        get(pathAllItems) { respondToList(call) }
                        post(pathAllItems) { respondToUpsert(call, resolveItemIdParameter(call)) }

                        get(pathSingleItem) { executeWithExistingItemId(call) { respondToGet(call, it) } }
                        put(pathSingleItem) { executeWithExistingItemId(call) { respondToUpsert(call, it) } }
                        delete(pathSingleItem) { executeWithExistingItemId(call) { respondToDelete(call, it) } }
                    }

                    private suspend fun executeWithExistingItemId(call: ApplicationCall, action: suspend (Id) -> Unit) {
                        val itemIdParameter = getItemIdParameter(call)
                        if (itemIdParameter == null || itemIdParameter.isBlank()) {
                            call.respond(HttpStatusCode.BadRequest, "Path id parameter must be given!")
                        }

                        val paramItemId = resolveItemIdParameter(call)
                        println("${'$'}{call.parameters[itemIdPathParameterName]} -> ${'$'}paramItemId")
                        if (paramItemId != null) {
                            action(paramItemId)
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Path id parameter cannot be converted!")
                        }
                    }

                    private fun resolveItemIdParameter(call: ApplicationCall) = convertItemIdParameter(getItemIdParameter(call))
                    private fun getItemIdParameter(call: ApplicationCall) = call.parameters[itemIdPathParameterName]

                    private suspend fun respondToList(call: ApplicationCall) = call.respond(list(call))

                    private suspend fun respondToGet(call: ApplicationCall, paramItemId: Id?) {
                        val item = get(call, paramItemId)
                        if (item != null) {
                            call.respond(item)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }

                    // TODO 415 Unsupported Media Type (RFC 7231) on invalid payload?
                    private suspend fun respondToUpsert(call: ApplicationCall, paramItemId: Id?) {
                        val payloadItem = call.receiveOrNull(type)
                        println("payloadItem: ${'$'}payloadItem")
                        if (payloadItem == null) {
                            call.fail(HttpStatusCode.BadRequest, "No item payload given!")
                        } else if (paramItemId != null && getId(payloadItem) != paramItemId) {
                            println("getId(payloadItem): ${'$'}{getId(payloadItem)}")
                            println("paramItemId: ${'$'}paramItemId")
                            call.fail(HttpStatusCode.BadRequest, "Path parameter id must match payload id!!")
                        } else {
                            val existingItem = get(call, paramItemId)
                            println("existingItem: ${'$'}existingItem")
                            if (existingItem != null && getId(existingItem) != getId(payloadItem)) {
                                call.fail(HttpStatusCode.BadRequest, "Path and body id value do not match!")
                            } else {
                                val updatedPayloadItem = if (getId(payloadItem) == null && paramItemId != null)
                                    createCopyWithId(payloadItem, paramItemId)
                                else
                                    payloadItem

                                val persistedItem = upsert(call, updatedPayloadItem)
                                if (persistedItem != null) {
                                    if (existingItem != null) {
                                        call.respond(HttpStatusCode.OK, persistedItem)
                                    } else {
                                        call.respond(HttpStatusCode.Created, persistedItem)
                                    }
                                } else {
                                    // TODO need more error details!
                                    call.respond(HttpStatusCode.InternalServerError, "Unable to store item!")
                                }
                            }
                        }
                    }

                    // TODO 415 Unsupported Media Type (RFC 7231) on invalid payload?
                    private suspend fun respondToDelete(call: ApplicationCall, paramItemId: Id?) {
                        val item = get(call, paramItemId)
                        if (item == null) {
                            call.respond(HttpStatusCode.NotFound)
                            return
                        }

                        val deleted = remove(call, item)
                        if (deleted) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            // TODO need more error details!
                            call.respond(HttpStatusCode.InternalServerError, "Unable to delete item!")
                        }
                    }

                    abstract fun convertItemIdParameter(paramValue: String?): Id?

                    abstract suspend fun list(call: ApplicationCall): List<Type>
                    abstract suspend fun get(call: ApplicationCall, id: Id?): Type?
                    abstract suspend fun getId(item: Type): Id?
                    abstract suspend fun createCopyWithId(item: Type, id: Id): Type
                    abstract suspend fun upsert(call: ApplicationCall, item: Type): Type?
                    abstract suspend fun remove(call: ApplicationCall, item: Type): Boolean
                }
            """.trimIndent() // TODO trimming!!

            val controllersSource = rest.entries.map {
                val key = it.key
                val value = it.value
                if (key is String && value is String) {
                    generateControllerSource(key, value)
                } else {
                    // TODO error handling or ensure validity beforehand!!
                    ""
                }
            }.joinToString("\n")

            // TODO validate!!
            val packageDefinition = configuration["codeGenerationPackage"] as String
            val importDefinitions = """
                import io.ktor.application.ApplicationCall
                import io.ktor.application.call
                import io.ktor.http.HttpStatusCode
                import io.ktor.request.receiveOrNull
                import io.ktor.response.respond
                import io.ktor.routing.*
                import kotlin.reflect.KClass
                
                import org.bson.types.ObjectId
                import org.litote.kmongo.Id
                import org.litote.kmongo.id.toId
                import java.util.*
            """.trimIndent()

            // TODO duplicate code
            val folderPath = packageDefinition.replace('.', '/')
            val modelsSource = "package $packageDefinition\n\n$importDefinitions\n\n$controllersSource\n$genericSource"
            extension.generateFile(folderPath, "Controllers.kt", modelsSource)
        }

        private fun generateControllerSource(typeName: String, path: String): String {
            project.logger.info("generating controller for $typeName with path $path")

            val type = typeName.capitalize()
            return """|object ${type}Controller: GenericCrudController<$type, Id<$type>>($type::class, "$path") {
            |
            |    override fun convertItemIdParameter(paramValue: String?) = paramValue?.toAppId()
            |
            |    override suspend fun list(call: ApplicationCall) = MongoDB.listApps()
            |    override suspend fun get(call: ApplicationCall, id: Id<App>?) = if (id != null) MongoDB.get(id) else null
            |    override suspend fun getId(item: App) = item._id
            |    override suspend fun createCopyWithId(item: App, id: Id<App>) = item.copy(_id = id)
            |    override suspend fun upsert(call: ApplicationCall, item: App) = MongoDB.upsert(item)
            |    override suspend fun remove(call: ApplicationCall, item: App) = MongoDB.delete(item)
            |}
            |""".trimMargin()
        }

        private fun generatePersistence(definition: Map<String, Object>, configuration: Map<String, Object>) {
            val datamodel = definition["datamodel"]
            if (datamodel == null || datamodel !is Map<*, *>) {
                project.logger.warn("No datamodel defined, skipping!")
                return
            }

            // TODO validate!!
            val packageDefinition = configuration["codeGenerationPackage"] as String

            val dataClassesMethodsSource = datamodel.entries.map {
                val key = it.key
                val value = it.value
                if (key is String && value is Map<*, *>) {
                    generatePersistenceFunctionsSource(key)
                } else {
                    // TODO error handling or ensure validity beforehand!!
                    ""
                }
            }.joinToString("\n")

            // TODO duplicate code
            val folderPath = packageDefinition.replace('.', '/')
            val persistenceSource = """
                package $packageDefinition
                
                import com.mongodb.*
                import com.mongodb.client.MongoCollection
                import com.mongodb.internal.connection.ServerAddressHelper
                import org.bson.conversions.Bson
                import org.litote.kmongo.*
                import kotlin.collections.toList

                // TODO paging and stuff
                // TODO logging & error handling
                object MongoDB {

                    // TODO get values from confog!!
                    private val mongoClient = KMongo.createClient(
                        ServerAddressHelper.createServerAddress("localhost", 27017),
                        listOf(MongoCredential.createCredential("james", "admin", "semaj".toCharArray())),
                        MongoClientOptions.builder()
                            .applicationName("james-api")
                            .connectTimeout(10000)
                            .build()
                    )

                    $dataClassesMethodsSource
                    
                    private fun<T: Any> genericUpsert(collection: MongoCollection<T>, item: T): T? {
                        try {
                            collection.save(item)
                            return item
                        } catch (e: MongoWriteException) {
                            println(e.message)
                            e.printStackTrace()
                        } catch (e: MongoWriteConcernException) {
                            println(e.message)
                            e.printStackTrace()
                        } catch (e: MongoCommandException) {
                            println(e.message)
                            e.printStackTrace()
                        } catch (e: MongoException) {
                            println(e.message)
                            e.printStackTrace()
                        }
                        return null
                    }

                    private fun<T: Any> genericDelete(collection: MongoCollection<T>, filter: Bson): Boolean {
                        try {
                            var result = collection.deleteOne(filter)
                            return result.deletedCount > 0
                        } catch (e: MongoWriteException) {
                            println(e.message)
                            e.printStackTrace()
                        } catch (e: MongoWriteConcernException) {
                            println(e.message)
                            e.printStackTrace()
                        } catch (e: MongoCommandException) {
                            println(e.message)
                            e.printStackTrace()
                        } catch (e: MongoException) {
                            println(e.message)
                            e.printStackTrace()
                        }
                        return false
                    }
                }
            """.trimIndent()
            extension.generateFile(folderPath, "Persistence.kt", persistenceSource)
        }

        private fun generatePersistenceFunctionsSource(typeName: String): String {
            project.logger.info("generating controller for $typeName with path $path")

            // TODO database name
            // TODO delete filter
            val typeCap = typeName.capitalize()
            val typePlural = """${typeName}s"""
            return """|private val $typePlural = mongoClient.getDatabase("james-api").getCollection<$typeCap>("$typePlural")
                |fun list${typePlural.capitalize()}() = $typePlural.find().toList()
                |fun get$typeCap(id: Id<$typeCap>) = apps.findOneById(id)
                |fun upsert(item: $typeCap) = genericUpsert($typePlural, item)
                |fun delete(item: $typeCap) = genericDelete($typePlural, $typeCap::_id eq item._id)
                |""".trimMargin()
        }
    }
}