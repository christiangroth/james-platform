package de.chrgroth.restcrud

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException
import java.util.regex.Pattern

data class ConfigurationYaml(
    val packageName: String?,
    val datamodel: List<DatamodelYaml?>?
)

data class DatamodelYaml(
    val name: String?,
    // TODO need base api path??
    val endpoint: String?,
    val attributes: List<String?>?
)

object YamlUtils {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // TODO add warnings?
    data class ValidationResult<T>(val result: T, val errors: List<String>)

    // TODO avoid usage of exceptions, return null!
    fun load(definitionsFile: File): Configuration {
        val unverifiedConfiguration = parse(definitionsFile)
        val validationResult = convert(unverifiedConfiguration)
        if(validationResult.errors.isNotEmpty()) {
            logger.error("Validating configuration failed:")
            validationResult.errors.forEach { logger.error("- $it") }
            throw IllegalArgumentException("Configuration is not valid, please see the errors in the log above!")
        }
        return validationResult.result
    }

    // TODO avoid usage of exceptions, return null!
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

    private fun convert(input: ConfigurationYaml): ValidationResult<Configuration> {
        val codeGenerationResult = convertCodeGeneration(input)
        val datamodelResult = convertDatamodel(input)
        // TODO validate duplicate datamodels
        val allErrors = codeGenerationResult.errors.plus(datamodelResult.flatMap { it.errors })
        return ValidationResult(Configuration(codeGenerationResult.result, datamodelResult.map { it.result }), allErrors)
    }

    private val packageNamePattern = Pattern.compile("[a-z]+([.][a-z]+)*")
    fun convertCodeGeneration(input: ConfigurationYaml): ValidationResult<CodeGeneration> {
        val packageName = input.packageName?.trim() ?: ""
        val errors = packageNamePattern.validate(packageName,
            "Code generation package name '$packageName' must match pattern: $packageNamePattern")
        return ValidationResult(CodeGeneration(packageName), errors)
    }

    private val datamodelNamePattern = Pattern.compile("[A-Z][a-zA-Z]*")
    private val datamodelEndpointPattern = Pattern.compile("[/][a-zA-Z]+([/][a-zA-Z]+)*")
    fun convertDatamodel(input: ConfigurationYaml): List<ValidationResult<Datamodel>> {
        if(input.datamodel == null) {
            return listOf(
                ValidationResult(
                    Datamodel(name = "", endpoint = null, attributes = emptyList()),
                    listOf("No datamodel defined")
                )
            )
        }

        return input.datamodel.map {
            if(it == null) {
                ValidationResult(
                    Datamodel(name = "", endpoint = null, attributes = emptyList()),
                    listOf("Found null/empty datamodel")
                )
            } else {
                val name = it.name?.trim() ?: ""
                val nameErrors = datamodelNamePattern.validate(name,
                    "Datamodel name '$name' must match pattern: $datamodelNamePattern")

                val endpoint = it.endpoint?.trim()
                val processedEndpoint = endpoint?.addLeadingSlash()?.deduplicateSlashes()?.removeTrailingSlash()
                val endpointErrors = datamodelEndpointPattern.validate(processedEndpoint,
                    "Datamodel endpoint '$endpoint' (processed to '$processedEndpoint') must match pattern: $datamodelEndpointPattern")

                val attributesResult = convertAttributes(it)
                // TODO validate duplicate attributes
                val allErrors = nameErrors.plus(endpointErrors).plus(attributesResult.flatMap { result -> result.errors })
                ValidationResult(Datamodel(name, processedEndpoint, attributesResult.map { attribute -> attribute.result }), allErrors)
            }
        }
    }

    private fun String.addLeadingSlash() = prependIndent("/")
    private fun String.deduplicateSlashes() = replace(Regex("[/]+"), "/")
    private fun String.removeTrailingSlash() = trimEnd('/')

    private const val ATTRIBUTE_KEY = "key"
    private val datamodelAttributeNameAndTypePattern = Pattern.compile("[a-zA-Z]+")
    fun convertAttributes(input: DatamodelYaml): List<ValidationResult<Attribute>> {
        if(input.attributes == null) {
            return listOf(
                ValidationResult(
                    Attribute(name = "", type = "", key = false, optional = false),
                    listOf("${input.name}: No attributes defined")
                )
            )
        }

        return input.attributes.map {
            if(it == null) {
                ValidationResult(
                    Attribute(name = "", type = "", key = false, optional = false),
                    listOf("${input.name}: Found null/empty attribute")
                )
            } else {
                val attribute = it.trim()
                val parts = attribute.split(" ")
                if(parts.size !in 2..3) {
                    return@map ValidationResult(
                        Attribute(name = "", type = "", key = false, optional = false),
                        listOf("${input.name}: Attribute '$attribute' does not match pattern: [$ATTRIBUTE_KEY] name type[?]")
                    )
                }

                val threeParts = parts.size == 3
                val key = threeParts && ATTRIBUTE_KEY == parts[0]
                if(threeParts && !key) {
                    return@map ValidationResult(
                        Attribute(name = "", type = "", key = false, optional = false),
                        listOf("${input.name}: When attribute consists of three parts, first part must be '$ATTRIBUTE_KEY': $attribute")
                    )
                }

                val nameAndTypeOffset = if(threeParts) 1 else 0
                val name = parts[0 + nameAndTypeOffset].trim()
                val nameErrors = datamodelAttributeNameAndTypePattern.validate(name,
                    "${input.name}: Attribute name '$name' must match pattern: $datamodelAttributeNameAndTypePattern")

                // TODO validate only one of kotlin basic types or other datamodels only??!?
                val type = parts[1 + nameAndTypeOffset].trim()
                val optional = type.endsWith('?')
                val cleanedType = type.trimEnd('?')
                val typeErrors = datamodelNamePattern.validate(cleanedType,
                    "${input.name}: Attribute type '$cleanedType' must match pattern: $datamodelNamePattern")

                // TODO validate combination of key and type

                val keyOptionalCombinationErrors = if(key && optional) {
                    listOf("${input.name}: Key attribute '$name' must not be optional")
                } else {
                    emptyList()
                }

                ValidationResult(
                    Attribute(name, cleanedType, key, optional),
                    nameErrors.plus(typeErrors).plus(keyOptionalCombinationErrors)
                )
            }
        }
    }

    private fun Pattern.validate(input: String?, error: String) =
        when {
            input == null -> emptyList()
            matcher(input).matches() -> emptyList()
            else -> listOf(error)
        }

    // TODO private fun to combine multiple error lists / strings
}
