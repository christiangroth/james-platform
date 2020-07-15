package de.chrgroth.restcrud

import de.chrgroth.restcrud.ApplicationFramework.*
import de.chrgroth.restcrud.PersistenceFramework.*
import java.util.regex.Pattern

data class Configuration(
    val codeGeneration: CodeGeneration,
    val model: List<Model>
) {
    fun endpoints() = model.filter { it.hasEndpoint() }
}

sealed class PersistenceFramework {
    object KMongo: PersistenceFramework()
}

sealed class ApplicationFramework {
    object Ktor: ApplicationFramework()
}

val packageNamePattern = Pattern.compile("[a-z]+([.][a-z]+)*").toRegex()
data class PackageName(val value: String) {
    init {
        value.matches(packageNamePattern)
    }
}

data class CodeGeneration(
    private val packageNameWrapper: PackageName,
    val persistenceFramework: PersistenceFramework = KMongo,
    val applicationFramework: ApplicationFramework = Ktor
) {
    val packageName: String
        get() = packageNameWrapper.value
    val packagePath: String
        get() = packageName.replace('.', '/')
}

val modelNamePattern = Pattern.compile("[A-Z][a-zA-Z]*").toRegex()
data class ModelName(val value: String) {
    init {
        value.matches(modelNamePattern)
    }
}

val modelEndpointPattern = Pattern.compile("[/][a-zA-Z]+([/][a-zA-Z]+)*").toRegex()
data class ModelEndpoint(val value: String?) {
    init {
        value?.matches(modelEndpointPattern)
    }

    companion object {
        fun cleanup(input: String?) = input?.trim()?.addLeadingSlash()?.deduplicateSlashes()?.removeTrailingSlash()

        private fun String.addLeadingSlash() = prependIndent("/")
        private fun String.deduplicateSlashes() = replace(Regex("[/]+"), "/")
        private fun String.removeTrailingSlash() = trimEnd('/')
    }
}

data class Model(
    private val nameWrapper: ModelName,
    private val endpointWrapper: ModelEndpoint,
    val attributes: List<Attribute>
) {
    val name: String
        get() = nameWrapper.value
    val endpoint: String?
        get() = endpointWrapper.value
    fun hasEndpoint() = endpoint != null
}

val modelAttributeNameAndTypePattern = Pattern.compile("[a-zA-Z]+").toRegex()
data class AttributeName(val value: String) {
    init {
        value.matches(modelAttributeNameAndTypePattern)
    }
}

data class AttributeType(val value: String) {
    init {
        value.matches(modelNamePattern)
    }
}

data class Attribute(
    val nameWrapper: AttributeName,
    val typeWrapper: AttributeType,
    val key: Boolean,
    val optional: Boolean
) {
    val name: String
        get() = nameWrapper.value
    val type: String
        get() = typeWrapper.value
}
