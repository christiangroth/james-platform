package de.chrgroth.restcrud

// TODO typealiases??

data class Configuration(
    val packageName: String,
    val datamodel: List<Datamodel>
) {
    // TODO test functions
    fun packagePath() = packageName.replace('.', '/')
    fun endpoints() = datamodel.filter { it.hasEndpoint() }
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
