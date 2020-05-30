package de.chrgroth.restcrud

import de.chrgroth.restcrud.ApplicationFramework.*
import de.chrgroth.restcrud.PersistenceFramework.*

// TODO typealiases??

data class Configuration(
    val codeGeneration: CodeGeneration,
    val datamodel: List<Datamodel>
) {
    // TODO test functions
    fun endpoints() = datamodel.filter { it.hasEndpoint() }
}

sealed class PersistenceFramework {
    object KMongo: PersistenceFramework()
}

sealed class ApplicationFramework {
    object Ktor: ApplicationFramework()
}

data class CodeGeneration(
    val packageName: String,
    val persistenceFramework: PersistenceFramework = KMongo,
    val applicationFramework: ApplicationFramework = Ktor
) {
    // TODO test functions
    fun packagePath() = packageName.replace('.', '/')
}

data class Datamodel(
    val name: String,
    // TODO slashes/url handling
    val endpoint: String?,
    val attributes: List<Attribute>
) {
    // TODO test vals/functions
    val typeName = name.capitalize()
    fun hasEndpoint() = endpoint != null
}

data class Attribute(
    val name: String,
    val type: String,
    val key: Boolean,
    val optional: Boolean
)
