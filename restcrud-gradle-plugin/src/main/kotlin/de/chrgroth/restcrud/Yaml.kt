package de.chrgroth.restcrud

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

data class ConfigurationYaml(
        val packageName: String?,
        val datamodel: List<DatamodelYaml?>?
)

data class DatamodelYaml(
        val name: String?,
        val endpoint: String?,
        val attributes: List<String?>?
)

sealed class ValidationResult<T> {
    fun isSuccess() = this is Success<*>

    data class Success<T>(val result: T) : ValidationResult<T>()
    data class Failure<T>(val errors: List<String>) : ValidationResult<T>()
}

typealias ValidationResults<T> = List<ValidationResult<T>>

private fun <T> ValidationResults<T>.collectResults(): List<T> = filterIsInstance<ValidationResult.Success<T>>().map { it.result }
private fun <T> ValidationResults<T>.collectErrors() = filterIsInstance<ValidationResult.Failure<T>>().flatMap { it.errors }

object YamlUtils {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val objectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

    fun load(definitionsFile: File): Configuration {
        val unverifiedConfiguration = parse(definitionsFile)

        return when (val validationResult = convert(unverifiedConfiguration)) {
            is ValidationResult.Success -> validationResult.result
            is ValidationResult.Failure -> {
                logger.error("Validating configuration failed:")
                validationResult.errors.forEach { logger.error("- $it") }
                throw IllegalStateException("Validating configuration failed!")
            }
        }
    }

    private fun parse(definitionsFile: File): ConfigurationYaml {
        logger.info("Trying to load definitions file from ${definitionsFile.absolutePath}...")
        if (!definitionsFile.exists() || !definitionsFile.canRead()) {
            logger.error("File ${definitionsFile.absolutePath} does not exist or is not readable!")
            throw IllegalStateException("File ${definitionsFile.absolutePath} does not exist or is not readable!")
        }

        return try {
            objectMapper.readValue(definitionsFile, ConfigurationYaml::class.java)
        } catch (e: JsonProcessingException) {
            logger.error("Error parsing restcrud definitions!", e)
            throw IllegalStateException("Error parsing restcrud definitions!", e)
        } catch (e: IOException) {
            logger.error("Error reading definitions file!", e)
            throw IllegalStateException("Error reading definitions file!", e)
        }
    }

    // TODO not sure why I need the explicit type parameters (gradle build will fail, intellij still is green)
    private fun convert(input: ConfigurationYaml): ValidationResult<Configuration> {
        val codeGenerationResult = convertCodeGeneration(input)
        val codeGenerationResultErrors =
                if (codeGenerationResult.isSuccess()) emptyList()
                else (codeGenerationResult as ValidationResult.Failure).errors

        val datamodelYaml = input.datamodel
        val datamodelYamlErrors = if (datamodelYaml == null) listOf("No datamodel defined") else emptyList<String>()
        val datamodelResult = if (datamodelYaml != null) convertDatamodel(datamodelYaml) else emptyList<ValidationResult<Datamodel>>()
        val datamodelResultErrors = datamodelResult.collectErrors()

        // TODO validate duplicate datamodels

        val allErrors = codeGenerationResultErrors.plus(datamodelYamlErrors).plus(datamodelResultErrors)
        return if (allErrors.isEmpty()) {
            ValidationResult.Success(
                    Configuration(
                            (codeGenerationResult as ValidationResult.Success<CodeGeneration>).result,
                            datamodelResult.collectResults()
                    )
            )
        } else {
            ValidationResult.Failure(allErrors)
        }
    }

    private val packageNamePattern = Pattern.compile("[a-z]+([.][a-z]+)*")
    fun convertCodeGeneration(input: ConfigurationYaml): ValidationResult<CodeGeneration> {
        val packageName = input.packageName?.trim() ?: ""
        return if (packageNamePattern.validate(packageName)) {
            ValidationResult.Success(CodeGeneration(packageName))
        } else {
            ValidationResult.Failure(listOf("Code generation package name '$packageName' must match pattern: $packageNamePattern"))
        }
    }

    private val datamodelNamePattern = Pattern.compile("[A-Z][a-zA-Z]*")
    private val datamodelEndpointPattern = Pattern.compile("[/][a-zA-Z]+([/][a-zA-Z]+)*")
    fun convertDatamodel(input: List<DatamodelYaml?>) =
            input.map {
                if (it == null) {
                    ValidationResult.Failure(listOf("Found null/empty datamodel"))
                } else {
                    convertDatamodel(it)
                }
            }

    // TODO not sure why I need the explicit type parameters (gradle build will fail, intellij still is green)
    private fun convertDatamodel(input: DatamodelYaml): ValidationResult<Datamodel> {
        val name = input.name?.trim() ?: ""
        val nameErrors = if (!datamodelNamePattern.validate(name)) {
            listOf("Datamodel name '$name' must match pattern: $datamodelNamePattern")
        } else emptyList<String>()

        val processedEndpoint = input.endpoint?.trim()?.addLeadingSlash()?.deduplicateSlashes()?.removeTrailingSlash()
        val endpointErrors = if (!datamodelEndpointPattern.validate(processedEndpoint)) {
            listOf("Datamodel endpoint '${input.endpoint}' (processed to '$processedEndpoint') must match pattern: $datamodelEndpointPattern")
        } else emptyList<String>()

        val attributesResult = if (input.attributes != null) convertAttributes(input.name
                ?: "", input.attributes) else emptyList<ValidationResult<Attribute>>()
        val attributeErrors = if (input.attributes == null) listOf("${input.name}: No attributes defined") else attributesResult.collectErrors()
        val attributes = attributesResult.collectResults()

        // TODO validate duplicate attributes

        val allErrors = nameErrors.plus(endpointErrors).plus(attributeErrors)
        return if (allErrors.isEmpty()) {
            ValidationResult.Success(Datamodel(name, processedEndpoint, attributes))
        } else {
            ValidationResult.Failure(allErrors)
        }
    }

    private fun String.addLeadingSlash() = prependIndent("/")
    private fun String.deduplicateSlashes() = replace(Regex("[/]+"), "/")
    private fun String.removeTrailingSlash() = trimEnd('/')

    private const val ATTRIBUTE_KEY = "key"
    private val datamodelAttributeNameAndTypePattern = Pattern.compile("[a-zA-Z]+")
    fun convertAttributes(typeName: String, input: List<String?>) =
            input.map {
                if (it == null) {
                    ValidationResult.Failure(listOf("$typeName: Found null/empty attribute"))
                } else {
                    convertAttribute(typeName, it)
                }
            }

    private fun convertAttribute(typeName: String, input: String): ValidationResult<Attribute> {
        val attribute = input.trim()
        val parts = attribute.split(" ")
        if (parts.size !in 2..3) {
            return ValidationResult.Failure(listOf("$typeName: Attribute '$attribute' does not match pattern: [$ATTRIBUTE_KEY] name type[?]"))
        }

        val threeParts = parts.size == 3
        val key = threeParts && ATTRIBUTE_KEY == parts[0]
        if (threeParts && !key) {
            return ValidationResult.Failure(listOf("$typeName: When attribute consists of three parts, first part must be '$ATTRIBUTE_KEY': $attribute"))
        }

        val nameAndTypeOffset = if (threeParts) 1 else 0
        val name = parts[0 + nameAndTypeOffset].trim()
        if (!datamodelAttributeNameAndTypePattern.validate(name)) {
            return ValidationResult.Failure(listOf("$typeName: Attribute name '$name' must match pattern: $datamodelAttributeNameAndTypePattern"))
        }

        // TODO validate only one of kotlin basic types or other datamodels only??!?
        val type = parts[1 + nameAndTypeOffset].trim()
        val optional = type.endsWith('?')
        val cleanedType = type.trimEnd('?')
        if (!datamodelNamePattern.validate(cleanedType)) {
            return ValidationResult.Failure(listOf("$typeName: Attribute type '$cleanedType' must match pattern: $datamodelNamePattern"))
        }

        // TODO validate combination of key and type

        return if (key && optional) {
            ValidationResult.Failure(listOf("$typeName: Key attribute '$name' must not be optional"))
        } else {
            ValidationResult.Success(Attribute(name, cleanedType, key, optional))
        }
    }

    private fun Pattern.validate(input: String?) = input == null || matcher(input).matches()
}
