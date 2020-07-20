package de.chrgroth.restcrud

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

data class ConfigurationYaml(val packageName: String?, val model: List<ModelYaml?>?)
data class ModelYaml(val name: String?, val endpoint: String?, val attributes: List<String?>?)

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

    fun convert(input: ConfigurationYaml): ValidationResult<Configuration> {
        val codeGenerationResult = convertCodeGeneration(input)
        val codeGenerationResultErrors =
                if (codeGenerationResult.isSuccess()) emptyList()
                else (codeGenerationResult as ValidationResult.Failure).errors

        val modelYaml = input.model
        val modelYamlErrors = if (modelYaml == null || modelYaml.isEmpty()) listOf("No model defined") else emptyList()
        val modelResult = if (modelYaml != null) convertModel(modelYaml) else emptyList()
        val modelResultErrors = modelResult.collectErrors()

        // TODO validate duplicate models

        val allErrors = codeGenerationResultErrors.plus(modelYamlErrors).plus(modelResultErrors)
        return if (allErrors.isEmpty()) {
            ValidationResult.Success(
                    Configuration(
                            (codeGenerationResult as ValidationResult.Success<CodeGeneration>).result,
                            modelResult.collectResults()
                    )
            )
        } else {
            ValidationResult.Failure(allErrors)
        }
    }

    fun convertCodeGeneration(input: ConfigurationYaml): ValidationResult<CodeGeneration> {
        val packageName = input.packageName?.trim() ?: ""
        return if (packageNamePattern.validate(packageName)) {
            ValidationResult.Success(CodeGeneration(PackageName(packageName)))
        } else {
            ValidationResult.Failure(listOf("Code generation package name '$packageName' must match pattern: $packageNamePattern"))
        }
    }

    fun convertModel(input: List<ModelYaml?>) =
            input.map {
                if (it == null) {
                    ValidationResult.Failure(listOf("Found null/empty model"))
                } else {
                    convertModel(it)
                }
            }

    fun convertModel(input: ModelYaml): ValidationResult<Model> {
        val name = input.name?.trim() ?: ""
        val nameErrors = if (!modelNamePattern.validate(name)) {
            listOf("Model name '$name' must match pattern: $modelNamePattern")
        } else emptyList()

        val processedEndpoint = ModelEndpoint.cleanup(input.endpoint)
        val endpointErrors = if (!modelEndpointPattern.validate(processedEndpoint)) {
            listOf("Model endpoint '${input.endpoint}' (processed to '$processedEndpoint') must match pattern: $modelEndpointPattern")
        } else emptyList()

        val attributesResult = if (input.attributes != null) convertAttributes(input.name
                ?: "", input.attributes) else emptyList()
        val attributeErrors = if (input.attributes == null || input.attributes.isEmpty()) listOf("${input.name}: No attributes defined") else attributesResult.collectErrors()
        val attributes = attributesResult.collectResults()

        // TODO validate duplicate attributes

        val allErrors = nameErrors.plus(endpointErrors).plus(attributeErrors)
        return if (allErrors.isEmpty()) {
            ValidationResult.Success(Model(ModelName(name), ModelEndpoint(processedEndpoint), attributes))
        } else {
            ValidationResult.Failure(allErrors)
        }
    }

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
        if (parts.size != 2) {
            return ValidationResult.Failure(listOf("$typeName: Attribute '$attribute' does not match pattern: name type[?]"))
        }

        val name = parts[0].trim()
        if (!modelAttributeNameAndTypePattern.validate(name)) {
            return ValidationResult.Failure(listOf("$typeName: Attribute name '$name' must match pattern: $modelAttributeNameAndTypePattern"))
        }

        // TODO validate only one of kotlin basic types or other models only
        val type = parts[1].trim()
        val optional = type.endsWith('?')
        val cleanedType = type.trimEnd('?')
        if (!modelNamePattern.validate(cleanedType)) {
            return ValidationResult.Failure(listOf("$typeName: Attribute type '$cleanedType' must match pattern: $modelNamePattern"))
        }

        return ValidationResult.Success(Attribute(AttributeName(name), AttributeType(cleanedType), optional))
    }

    private fun Regex.validate(input: String?) = input == null || input.matches(this)
}
