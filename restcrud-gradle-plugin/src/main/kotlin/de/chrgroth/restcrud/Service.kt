package de.chrgroth.restcrud

import java.lang.IllegalArgumentException

internal interface ServiceFactory {
    fun supports(application: ApplicationFramework, persistence: PersistenceFramework): Boolean
    fun createService(configuration: Configuration, codeGenerator: CodeGenerator): Service
}

private val availableServiceFactories = listOf<ServiceFactory>(KtorKMongoServiceFactory)

private fun resolveServiceFactory(application: ApplicationFramework, persistence: PersistenceFramework) =
        availableServiceFactories.firstOrNull { it.supports(application, persistence) } ?:
                throw IllegalArgumentException("Unable to create service for $application and $persistence!")

interface Service {

    fun generate() {
        generateModels()
        generateControllers()
        generatePersistence()
        generateApplication()
    }

    fun generateModels()
    fun generateControllers()
    fun generatePersistence()
    fun generateApplication()
}

fun createService(application: ApplicationFramework, persistence: PersistenceFramework,
                  configuration: Configuration, codeGenerator: CodeGenerator) =
        resolveServiceFactory(application, persistence).createService(configuration, codeGenerator)
