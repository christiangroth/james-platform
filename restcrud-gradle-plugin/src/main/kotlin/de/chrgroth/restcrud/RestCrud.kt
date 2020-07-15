package de.chrgroth.restcrud

import org.slf4j.LoggerFactory

// TODO refactor, namings, tests
// TODO generating Ktor application makes it hard to add a custom controller etc. I maybe need a concept for that!

class Service(private val fileGenerator: FileGenerator) {
    private val logger = LoggerFactory.getLogger(Service::class.java)

    fun generateModels(configuration: Configuration) {
        logger.info("generating model...")

        if (configuration.model.isEmpty()) {
            logger.warn("No model defined, skipping!")
            return
        }

        val dataClassesSource = configuration.model.joinToString("\n") { generateModelSource(it) }

        val importsDefinition = "import org.litote.kmongo.Id"
        val modelsSource = "package ${configuration.codeGeneration.packageName}\n\n$importsDefinition\n\n$dataClassesSource"
        fileGenerator.generateFile(configuration.codeGeneration.packagePath, "Models.kt", modelsSource)
    }

    private fun generateModelSource(model: Model): String {
        logger.info("generating data class for $model")

        if (model.attributes.isEmpty()) {
            logger.error("Type ${model.name} has no defined attributes, skipping!")
            return ""
        }

        return """|data class ${model.name}(
            |    val _id: Id<${model.name}>?,
            |    ${model.attributes.joinToString(",\n|    ") { "val ${it.name}: ${it.type}" }} 
            |)
            |""".trimMargin()
    }

    fun generateControllers(configuration: Configuration) {
        logger.info("generating api endpoints...")

        if (configuration.endpoints().isEmpty()) {
            logger.warn("No rest endpoints defined, skipping!")
            return
        }

        val genericSource = """|const val API_PREFIX = "/api"
                |
                |data class ResponseError(val code: Int, val message: String, val details: String? = null)
                |suspend fun ApplicationCall.fail(code: HttpStatusCode, message: String, details: String? = null) =
                |    respond(code, ResponseError(code.value, message, details))
                |
                |// TODO i18n -> load correct value from entity for localized values
                |// TODO returns 404 instead of 405 if route exist but with different method
                |abstract class GenericCrudController<Type : Any, Id : Any>(private val type: KClass<Type>, pathPrefix: String) {
                |    private val itemIdPathParameterName = "itemId"
                |    private val pathAllItems = "${'$'}API_PREFIX/${'$'}pathPrefix"
                |    private val pathSingleItem = "${'$'}API_PREFIX/${'$'}pathPrefix/{${'$'}itemIdPathParameterName}"
                |
                |    open fun routes(routing: Routing) = with(routing) {
                |        get(pathAllItems) { respondToList(call) }
                |        post(pathAllItems) { respondToUpsert(call, resolveItemIdParameter(call)) }
                |
                |        get(pathSingleItem) { executeWithExistingItemId(call) { respondToGet(call, it) } }
                |        put(pathSingleItem) { executeWithExistingItemId(call) { respondToUpsert(call, it) } }
                |        delete(pathSingleItem) { executeWithExistingItemId(call) { respondToDelete(call, it) } }
                |    }
                |
                |    private suspend fun executeWithExistingItemId(call: ApplicationCall, action: suspend (Id) -> Unit) {
                |        val itemIdParameter = getItemIdParameter(call)
                |        if (itemIdParameter == null || itemIdParameter.isBlank()) {
                |            call.respond(HttpStatusCode.BadRequest, "Path id parameter must be given!")
                |        }
                |
                |        val paramItemId = resolveItemIdParameter(call)
                |        println("${'$'}{call.parameters[itemIdPathParameterName]} -> ${'$'}paramItemId")
                |        if (paramItemId != null) {
                |            action(paramItemId)
                |        } else {
                |            call.respond(HttpStatusCode.BadRequest, "Path id parameter cannot be converted!")
                |        }
                |    }
                |
                |    private fun resolveItemIdParameter(call: ApplicationCall) = convertItemIdParameter(getItemIdParameter(call))
                |    private fun convertItemIdParameter(paramValue: String?): Id? {
                |        return try {
                |            ObjectId(paramValue).toId<Type>() as Id
                |        } catch (e: IllegalArgumentException) {
                |            // TODO logging
                |            println("Failed to convert ${'$'}paramValue to Id: ${'$'}{e.message}")
                |            null
                |        }
                |    }
                |
                |    private fun getItemIdParameter(call: ApplicationCall) = call.parameters[itemIdPathParameterName]
                |
                |    private suspend fun respondToList(call: ApplicationCall) = call.respond(list(call))
                |
                |    private suspend fun respondToGet(call: ApplicationCall, paramItemId: Id?) {
                |        val item = get(call, paramItemId)
                |        if (item != null) {
                |            call.respond(item)
                |        } else {
                |            call.respond(HttpStatusCode.NotFound)
                |        }
                |    }
                |
                |    // TODO 415 Unsupported Media Type (RFC 7231) on invalid payload?
                |    private suspend fun respondToUpsert(call: ApplicationCall, paramItemId: Id?) {
                |        val payloadItem = call.receiveOrNull(type)
                |        println("payloadItem: ${'$'}payloadItem")
                |        if (payloadItem == null) {
                |            call.fail(HttpStatusCode.BadRequest, "No item payload given!")
                |        } else if (paramItemId != null && getId(payloadItem) != paramItemId) {
                |            println("getId(payloadItem): ${'$'}{getId(payloadItem)}")
                |            println("paramItemId: ${'$'}paramItemId")
                |            call.fail(HttpStatusCode.BadRequest, "Path parameter id must match payload id!!")
                |        } else {
                |            val existingItem = get(call, paramItemId)
                |            println("existingItem: ${'$'}existingItem")
                |            if (existingItem != null && getId(existingItem) != getId(payloadItem)) {
                |                call.fail(HttpStatusCode.BadRequest, "Path and body id value do not match!")
                |            } else {
                |                val updatedPayloadItem = if (getId(payloadItem) == null && paramItemId != null)
                |                    createCopyWithId(payloadItem, paramItemId)
                |                else
                |                    payloadItem
                |
                |                val persistedItem = upsert(call, updatedPayloadItem)
                |                if (persistedItem != null) {
                |                    if (existingItem != null) {
                |                        call.respond(HttpStatusCode.OK, persistedItem)
                |                    } else {
                |                        call.respond(HttpStatusCode.Created, persistedItem)
                |                    }
                |                } else {
                |                    // TODO need more error details!
                |                    call.respond(HttpStatusCode.InternalServerError, "Unable to store item!")
                |                }
                |            }
                |        }
                |    }
                |
                |    // TODO 415 Unsupported Media Type (RFC 7231) on invalid payload?
                |    private suspend fun respondToDelete(call: ApplicationCall, paramItemId: Id?) {
                |        val item = get(call, paramItemId)
                |        if (item == null) {
                |            call.respond(HttpStatusCode.NotFound)
                |            return
                |        }
                |
                |        val deleted = remove(call, item)
                |        if (deleted) {
                |            call.respond(HttpStatusCode.NoContent)
                |        } else {
                |            // TODO need more error details!
                |            call.respond(HttpStatusCode.InternalServerError, "Unable to delete item!")
                |        }
                |    }
                |
                |    abstract suspend fun list(call: ApplicationCall): List<Type>
                |    abstract suspend fun get(call: ApplicationCall, id: Id?): Type?
                |    abstract suspend fun getId(item: Type): Id?
                |    abstract suspend fun createCopyWithId(item: Type, id: Id): Type
                |    abstract suspend fun upsert(call: ApplicationCall, item: Type): Type?
                |    abstract suspend fun remove(call: ApplicationCall, item: Type): Boolean
                |}
                |""".trimMargin()

        val controllersSource = configuration.endpoints().joinToString("\n") { generateControllerSource(it) }

        // TODO validate!!
        val importDefinitions = """|import io.ktor.application.ApplicationCall
                |import io.ktor.application.call
                |import io.ktor.http.HttpStatusCode
                |import io.ktor.request.receiveOrNull
                |import io.ktor.response.respond
                |import io.ktor.routing.*
                |import kotlin.reflect.KClass
                |
                |import org.bson.types.ObjectId
                |import org.litote.kmongo.Id
                |import org.litote.kmongo.id.toId
            """.trimMargin()

        val modelsSource = "package ${configuration.codeGeneration.packageName}\n\n$importDefinitions\n\n$controllersSource\n$genericSource"
        fileGenerator.generateFile(configuration.codeGeneration.packagePath, "Controllers.kt", modelsSource)
    }

    private fun generateControllerSource(model: Model): String {
        logger.info("generating controller object for $model")

        val type = model.name
        return """|object ${type}Controller: GenericCrudController<$type, Id<$type>>($type::class, "${model.endpoint}") {
            |    override suspend fun list(call: ApplicationCall) = MongoDB.list${type}s()
            |    override suspend fun get(call: ApplicationCall, id: Id<$type>?) = if (id != null) MongoDB.get$type(id) else null
            |    override suspend fun getId(item: $type) = item._id
            |    override suspend fun createCopyWithId(item: $type, id: Id<$type>) = item.copy(_id = id)
            |    override suspend fun upsert(call: ApplicationCall, item: $type) = MongoDB.upsert(item)
            |    override suspend fun remove(call: ApplicationCall, item: $type) = MongoDB.delete(item)
            |}
            |""".trimMargin()
    }

    fun generatePersistence(configuration: Configuration) {
        logger.info("generating persistence...")

        if (configuration.endpoints().isEmpty()) {
            logger.warn("No rest endpoints defined, skipping!")
            return
        }

        val dataClassesMethodsSource =
            configuration.endpoints().joinToString("\n") { generatePersistenceFunctionsSource(it) }

        // TODO duplicate code
        val persistenceSource = """|package ${configuration.codeGeneration.packageName}
                |
                |import com.mongodb.*
                |import com.mongodb.client.MongoCollection
                |import com.mongodb.internal.connection.ServerAddressHelper
                |import org.bson.conversions.Bson
                |import org.litote.kmongo.*
                |import kotlin.collections.toList
                |
                |// TODO paging and stuff
                |// TODO logging & error handling
                |object MongoDB {
                |
                |    // TODO get values from confog!!
                |    private val mongoClient = KMongo.createClient(
                |        ServerAddressHelper.createServerAddress("localhost", 27017),
                |        listOf(MongoCredential.createCredential("james", "admin", "semaj".toCharArray())),
                |        MongoClientOptions.builder()
                |            .applicationName("james-api")
                |            // TODO timeout does not work
                |            .connectTimeout(10000)
                |            .build()
                |    )
                |
                |$dataClassesMethodsSource
                |    private fun<T: Any> genericUpsert(collection: MongoCollection<T>, item: T): T? {
                |        try {
                |            collection.save(item)
                |            return item
                |        } catch (e: MongoWriteException) {
                |            println(e.message)
                |            e.printStackTrace()
                |        } catch (e: MongoWriteConcernException) {
                |            println(e.message)
                |            e.printStackTrace()
                |        } catch (e: MongoCommandException) {
                |            println(e.message)
                |            e.printStackTrace()
                |        } catch (e: MongoException) {
                |            println(e.message)
                |            e.printStackTrace()
                |        }
                |        return null
                |    }
                |
                |    private fun<T: Any> genericDelete(collection: MongoCollection<T>, filter: Bson): Boolean {
                |        try {
                |            var result = collection.deleteOne(filter)
                |            return result.deletedCount > 0
                |        } catch (e: MongoWriteException) {
                |            println(e.message)
                |            e.printStackTrace()
                |        } catch (e: MongoWriteConcernException) {
                |            println(e.message)
                |            e.printStackTrace()
                |        } catch (e: MongoCommandException) {
                |            println(e.message)
                |            e.printStackTrace()
                |        } catch (e: MongoException) {
                |            println(e.message)
                |            e.printStackTrace()
                |        }
                |        return false
                |    }
                |}
                |""".trimMargin()
        fileGenerator.generateFile(configuration.codeGeneration.packagePath, "Persistence.kt", persistenceSource)
    }

    private fun generatePersistenceFunctionsSource(model: Model): String {
        logger.info("generating KMongo functions for $model")

        // TODO database name
        // TODO delete filter
        val typeName = model.name
        val typePlural = """${typeName}s"""
        return """|    private val $typePlural = mongoClient.getDatabase("james-api").getCollection<$typeName>("$typePlural")
                |    fun list${typePlural.capitalize()}() = $typePlural.find().toList()
                |    fun get$typeName(id: Id<$typeName>) = $typePlural.findOneById(id)
                |    fun upsert(item: $typeName) = genericUpsert($typePlural, item)
                |    fun delete(item: $typeName) = genericDelete($typePlural, $typeName::_id eq item._id)
                |    """.trimMargin()
    }

    fun generateKtor(configuration: Configuration) {
        logger.info("generating Ktor base application...")

        if (configuration.endpoints().isEmpty()) {
            logger.warn("No rest endpoints defined, skipping!")
            return
        }

        val routingCalls = configuration.endpoints().joinToString("\n") {
            "        ${it.name}Controller.routes(this)"
        }

        val ktorSource = """|package ${configuration.codeGeneration.packageName}
                |
                |import com.fasterxml.jackson.core.JsonProcessingException
                |import com.fasterxml.jackson.databind.SerializationFeature
                |import io.ktor.application.Application
                |import io.ktor.application.call
                |import io.ktor.application.install
                |import io.ktor.application.log
                |import io.ktor.features.*
                |import io.ktor.http.HttpStatusCode
                |import io.ktor.jackson.jackson
                |import io.ktor.locations.Locations
                |import io.ktor.request.path
                |import io.ktor.routing.routing
                |import org.litote.kmongo.id.jackson.IdJacksonModule
                |import org.slf4j.event.Level
                |
                |fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)
                |
                |// TODO add metrics
                |// TODO add AUTH somehow!
                |
                |@kotlin.jvm.JvmOverloads
                |fun Application.module(testing: Boolean = false) {
                |
                |    // Allows type-safe routing using @Location
                |    // https://ktor.io/servers/features/locations.html
                |    install(Locations) { }
                |
                |    // Logs application calls
                |    // https://ktor.io/servers/features/call-logging.html
                |    install(CallLogging) {
                |        level = if (testing) Level.INFO else Level.DEBUG
                |        filter { call -> call.request.path().startsWith("/") }
                |    }
                |
                |    // Enable payload compression
                |    // https://ktor.io/servers/features/compression.html
                |    install(Compression) {
                |        gzip {
                |            priority = 1.0
                |        }
                |        deflate {
                |            priority = 10.0
                |            minimumSize(512) // condition
                |        }
                |    }
                |
                |    // Jackson configuration
                |    // https://ktor.io/servers/features/content-negotiation.html
                |    install(ContentNegotiation) {
                |        jackson {
                |
                |            // handler for semver
                |            registerModule(IdJacksonModule())
                |
                |            // TODO enable validation somehow?
                |            // TODO maybe configure date format??
                |
                |            if (testing) {
                |                enable(SerializationFeature.INDENT_OUTPUT)
                |            }
                |        }
                |    }
                |
                |    routing {
                |        trace { application.log.debug(it.buildText()) }
                |
                |        // apply routes
                |$routingCalls
                |        
                |        // exception handler
                |        install(StatusPages) {
                |            exception<JsonProcessingException> { cause ->
                |                call.fail(
                |                    code = HttpStatusCode.BadRequest,
                |                    message = "Unable to parse payload: ${'$'}{cause.javaClass.name}",
                |                    details = cause.message
                |                )
                |            }
                |            exception<Throwable> { cause ->
                |                call.fail(
                |                    code = HttpStatusCode.InternalServerError,
                |                    message = "Caught unexpected error: ${'$'}{cause.javaClass.name}",
                |                    details = cause.message
                |                )
                |            }
                |        }
                |    }
                |}
                |""".trimMargin()

        val folderPath = configuration.codeGeneration.packagePath
        fileGenerator.generateFile(folderPath, "Ktor.kt", ktorSource)
    }
}