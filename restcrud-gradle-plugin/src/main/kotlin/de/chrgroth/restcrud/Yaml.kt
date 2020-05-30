package de.chrgroth.restcrud

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

// TODO create data classes for raw parsing and for validated model??

data class RestCrud(
    // TODO typealias
    val packageName: String,
    val datamodel: List<RestCrudDatamodel>
) {

    // TODO test functions
    fun packagePath() = packageName.replace('.', '/')
    fun endpoints() = datamodel.filter { it.hasEndpoint() }
}

data class RestCrudDatamodel(
    // TODO typealias
    val name: String,
    // TODO typealias
    val key: List<String>,
    // TODO typealias, slash handling
    val endpoint: String?,
    val attributes: List<RestCrudDatamodelAttribute>
) {

    // TODO test vals/functions
    val typeName = name.capitalize()
    fun hasEndpoint() = endpoint != null
}

// TODO test vals/functions
typealias RestCrudDatamodelAttribute = String
val RestCrudDatamodelAttribute.name
    get() = this.split(" ")[0]
val RestCrudDatamodelAttribute.type
    get() = this.split(" ")[1]
fun RestCrudDatamodelAttribute.isOptional() = type.endsWith('?')

object YamlUtils {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // TODO service.validate(definition)

    fun parse(definitionsFile: File): RestCrud {
        logger.info("Trying to load definitions file from ${definitionsFile.absolutePath}...")
        return if (definitionsFile.exists() && definitionsFile.canRead()) {
            try {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())
            mapper.readValue(definitionsFile, RestCrud::class.java)
            } catch(e: JsonProcessingException) {
                throw IllegalStateException("Error parsing restcrud definitions!", e)
            } catch(e: IOException) {
                throw IllegalStateException("Error reading definitions file!", e)
            }
        } else {
            throw IllegalStateException("File ${definitionsFile.absolutePath} does not exist or is not readable!")
        }
    }
}
