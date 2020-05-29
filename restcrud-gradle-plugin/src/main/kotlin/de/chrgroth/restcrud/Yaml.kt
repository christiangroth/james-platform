package de.chrgroth.restcrud

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.io.File

data class RestCrud(
    val packageName: String,
    val datamodel: List<RestCrudDatamodel>
) {
    fun packagePath() = packageName.replace('.', '/')
    fun endpoints() = datamodel.filter { it.hasEndpoint() }
}

data class RestCrudDatamodel(
    val name: String,
    val key: List<String>,
    val endpoint: String?,
    val attributes: List<RestCrudDatamodelAttribute>
) {
    val typeName = name.capitalize()
    fun hasEndpoint() = endpoint != null
}

typealias RestCrudDatamodelAttribute = String

val RestCrudDatamodelAttribute.name
    get() = this.split(" ")[0]

val RestCrudDatamodelAttribute.type
    get() = this.split(" ")[1]

fun RestCrudDatamodelAttribute.isOptional() = type.endsWith('?')

object YamlUtils {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // TODO service.validate(definition)
    fun parse(definitionsFile: File): RestCrud? {
        logger.info("Trying to load definitions file from ${definitionsFile.absolutePath}...")
        return if (definitionsFile.exists()) {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())
            mapper.readValue(definitionsFile, RestCrud::class.java)
        } else {
            null
        }
    }
}
