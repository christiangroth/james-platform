package de.chrgroth.restcrud

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException

// TODO create data classes for raw parsing and for validated model??

data class ConfigurationYaml(
    // TODO typealias
    val packageName: String,
    val datamodel: List<DatamodelYaml>
) {

    // TODO test functions
    fun packagePath() = packageName.replace('.', '/')
    fun endpoints() = datamodel.filter { it.hasEndpoint() }
}

data class DatamodelYaml(
    // TODO typealias
    val name: String,
    // TODO typealias
    val key: List<String>,
    // TODO typealias, slash handling
    val endpoint: String?,
    val attributes: List<String>
) {

    // TODO test vals/functions
    val typeName = name.capitalize()
    fun hasEndpoint() = endpoint != null
}

object YamlUtils {
    private val logger = LoggerFactory.getLogger(this::class.java)

    data class ValidationResult<T>(val result: T, val errors: List<String>)

    fun load(definitionsFile: File): Configuration {
        val unverifiedConfiguration = parse(definitionsFile)
        val validationResult = validate(unverifiedConfiguration)
        if(validationResult.errors.isNotEmpty()) {
            validationResult.errors.forEach { logger.error(it) }
            throw IllegalArgumentException("Configuration is not valid, please see the errors in the log above!")
        }
        return validationResult.result
    }

    private fun parse(definitionsFile: File): ConfigurationYaml {
        logger.info("Trying to load definitions file from ${definitionsFile.absolutePath}...")
        return if (definitionsFile.exists() && definitionsFile.canRead()) {
            try {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())
            mapper.readValue(definitionsFile, ConfigurationYaml::class.java)
            } catch(e: JsonProcessingException) {
                throw IllegalStateException("Error parsing restcrud definitions!", e)
            } catch(e: IOException) {
                throw IllegalStateException("Error reading definitions file!", e)
            }
        } else {
            throw IllegalStateException("File ${definitionsFile.absolutePath} does not exist or is not readable!")
        }
    }

    private fun validate(input: ConfigurationYaml): ValidationResult<Configuration> {
        // TODO collect all errors somehow
        val codeGeneration = convertCodeGenerationConfiguration(input)
        val datamodel = convertDatamodel(input)
        return ValidationResult(Configuration(codeGeneration, datamodel), emptyList())
    }

    private fun convertCodeGenerationConfiguration(input: ConfigurationYaml): CodeGeneration {
        // TODO validate package name
        val packageName = input.packageName
        return CodeGeneration(packageName)
    }

    private fun convertDatamodel(input: ConfigurationYaml) = input.datamodel.map {
        if(it == null) {
            // TODO validation error
            Datamodel(name = "", endpoint = null, attributes = emptyList())
        } else {
            // TODO validate name
            val name = it.name
            // TODO validate endpoint
            val endpoint = it.endpoint
            val attributes = convertAttributes(it)
            Datamodel(name, endpoint, attributes)
        }
    }

    private fun convertAttributes(input: DatamodelYaml) = input.attributes.map {
        if(it == null) {
            // TODO validation error
            Attribute(name = "", type = "", key = false, optional = false)
        } else {
            // TODO validate parts
            val parts = it.split(" ")
            // TODO validate name
            val name = parts[0]
            // TODO validate type
            val type = parts[1]
            // TODO validate combination of key and type
            // TODO validate combination of key and optional
            val key = false
            val optional = type.endsWith('?')
            Attribute(name, type, key, optional)
        }
    }
}
