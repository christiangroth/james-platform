package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.combine
import org.everit.json.schema.CombinedSchema
import org.everit.json.schema.EnumSchema
import org.everit.json.schema.ObjectSchema

internal fun ObjectSchema.validateCombinedProperties() =
    filterProperties(CombinedSchema::class)
        .mapNotNull { it.value.validateDefinition(propertyName = it.key) }.combine()

// see: https://json-schema.org/understanding-json-schema/reference/combining.html
// see: https://json-schema.org/understanding-json-schema/reference/conditionals.html
internal fun CombinedSchema.validateDefinition(propertyName: String): Errors<CombinedSchema>? {

    val commonAnnotationsErrors = validateCommonAnnotations(propertyName)

    val criterionError = if (criterion != CombinedSchema.ALL_CRITERION) {
        Error<CombinedSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_COMBINED_PROPERTY_UNSUPPORTED_CRITERION,
            details = propertyName,
        )
    } else null

    val exactTwoSubschemas = subschemas != null && subschemas.size == 2
    val enumSchema = subschemas.filterIsInstance<EnumSchema>().firstOrNull()
    val typeSchema = subschemas.firstOrNull { it !is EnumSchema }
    val enumSupportingJsonSchema = typeSchema?.resolveEnumSupportingJsonSchema()
    val subSchemasForEnumUsecaseError = if (!exactTwoSubschemas || enumSchema == null || enumSupportingJsonSchema == null) {
        Error<CombinedSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_COMBINED_PROPERTY_ONLY_SUPPORTS_ENUM_USECASE_FOR_STRING_AND_NUMBER,
            details = "$propertyName: ${subschemas.map { it.javaClass.simpleName }.sorted() }",
        )
    } else null

    val enumAndTypeValidationErrors = if(enumSchema != null && typeSchema != null && enumSupportingJsonSchema != null) {
        val enumNotSupportedError = if (!enumSupportingJsonSchema.enumDefinitionSupported(typeSchema)) {
            Error<CombinedSchema>(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ENUM_PROPERTY_NOT_SUPPORTED,
                details = propertyName,
            )
        } else null

        val enumValuesMissingError = if(enumSchema.possibleValues == null || enumSchema.possibleValues.isEmpty()) {
            Error<CombinedSchema>(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISSING,
                details = propertyName,
            )
        } else null

        val enumValuesMismatchingTypeError = if(!enumSupportingJsonSchema.enumValuesTypeMatches(typeSchema, enumSchema)) {
            Error<CombinedSchema>(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISMATCHING_TYPE,
                details = propertyName,
            )
        } else null

        val delegatedTypeSchemaValidationErrors =
            enumSupportingJsonSchema.delegateTypeSchemaValidation(typeSchema, propertyName)

        @Suppress("UNCHECKED_CAST")
        enumNotSupportedError
            .combine(enumValuesMissingError)
            .combine(enumValuesMismatchingTypeError)
            .combine(delegatedTypeSchemaValidationErrors as Errors<CombinedSchema>?)
    } else null

    val unprocessedPropertiesError = if (unprocessedProperties.isNotEmpty()) {
        Error<CombinedSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
            details = "$propertyName: $unprocessedProperties",
        )
    } else null

    return commonAnnotationsErrors
        .combine(criterionError)
        .combine(subSchemasForEnumUsecaseError)
        .combine(enumAndTypeValidationErrors)
        .combine(unprocessedPropertiesError)
}
