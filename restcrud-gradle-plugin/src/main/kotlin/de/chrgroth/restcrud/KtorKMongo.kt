package de.chrgroth.restcrud

internal object KtorKMongoServiceFactory : ServiceFactory {
    override fun supports(application: ApplicationFramework, persistence: PersistenceFramework) =
            application == ApplicationFramework.Ktor && persistence == PersistenceFramework.KMongo

    override fun createService(configuration: Configuration, codeGenerator: CodeGenerator) =
            KtorKMongoService(configuration, codeGenerator)
}

internal class KtorKMongoService(private val configuration: Configuration, private val codeGenerator: CodeGenerator) : Service {

    private val kMongoIdProvider: (Model) -> Pair<String, String> = { "_id" to "Id<${it.name}>?" }

    override fun generateModels() {
        codeGenerator.generateModels(
                listOf(
                        // TODO use compile time class to get name?!
                        PackageName("org.litote.kmongo.Id")
                ),
                configuration.model,
                listOf(kMongoIdProvider)
        )
    }

    override fun generateControllers() {
        codeGenerator.generateControllers(
                listOf(
                        // TODO use compile time class to get name?!
                        PackageName("io.ktor.application.ApplicationCall"),
                        PackageName("io.ktor.application.call"),
                        PackageName("io.ktor.http.HttpStatusCode"),
                        PackageName("io.ktor.request.receiveOrNull"),
                        PackageName("io.ktor.response.respond"),
                        PackageName("io.ktor.routing.*"),
                        PackageName("kotlin.reflect.KClass"),

                        PackageName("org.bson.types.ObjectId"),
                        PackageName("org.litote.kmongo.Id"),
                        PackageName("org.litote.kmongo.id.toId")
                ),
                configuration.endpoints(),
                "ktorController.kt",
                "ktorGenericCrud.kt"
        )
    }

    override fun generatePersistence() {
        codeGenerator.generatePersistence(
                listOf(
                        // TODO use compile time class to get name?!
                        PackageName("com.mongodb.*"),
                        PackageName("com.mongodb.client.MongoCollection"),
                        PackageName("com.mongodb.internal.connection.ServerAddressHelper"),
                        PackageName("org.bson.conversions.Bson"),
                        PackageName("org.litote.kmongo.*"),
                        PackageName("kotlin.collections.toList")
                ),
                configuration.endpoints(),
                "kMongoMethods.kt",
                "kMongo.kt"
        )
    }

    override fun generateApplication() {
        codeGenerator.generateApplication(
                listOf(
                        // TODO use compile time class to get name?!
                        PackageName("com.fasterxml.jackson.core.JsonProcessingException"),
                        PackageName("com.fasterxml.jackson.databind.SerializationFeature"),
                        PackageName("io.ktor.application.Application"),
                        PackageName("io.ktor.application.call"),
                        PackageName("io.ktor.application.install"),
                        PackageName("io.ktor.application.log"),
                        PackageName("io.ktor.features.*"),
                        PackageName("io.ktor.http.HttpStatusCode"),
                        PackageName("io.ktor.jackson.jackson"),
                        PackageName("io.ktor.locations.Locations"),
                        PackageName("io.ktor.request.path"),
                        PackageName("io.ktor.routing.routing"),
                        PackageName("org.litote.kmongo.id.jackson.IdJacksonModule"),
                        PackageName("org.slf4j.event.Level")
                ),
                configuration.endpoints(),
                "ktorRoutingCall.kt",
                "ktor.kt"
        )
    }
}