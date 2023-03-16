package de.chrgroth.james.app.jsonschema

import arrow.core.Validated
import arrow.core.ValidatedNel
import de.chrgroth.james.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.createValidation
import de.chrgroth.james.reduceWithFirstValue
import org.everit.json.schema.CombinedSchema
import org.everit.json.schema.EnumSchema
import org.everit.json.schema.ObjectSchema

internal fun ObjectSchema.validateCombinedProperties(): ValidatedNel<Error, Unit> =
    filterProperties(CombinedSchema::class)
        .mapNotNull { it.value.validateDefinition(propertyName = it.key) }
        .reduceWithFirstValue()

internal val CombinedSchema.enumSchemaOrNull get() = subschemas.filterIsInstance<EnumSchema>().firstOrNull()
internal val CombinedSchema.typeSchemaOrNull get() = subschemas.firstOrNull { it !is EnumSchema }

// see: https://json-schema.org/understanding-json-schema/reference/combining.html
// see: https://json-schema.org/understanding-json-schema/reference/conditionals.html
internal fun CombinedSchema.validateDefinition(propertyName: String): ValidatedNel<Error, Unit> {

    val commonAnnotationsValidation = validateCommonAnnotations(propertyName)

    val criterionValidation = createValidation(
        errorCondition = criterion != CombinedSchema.ALL_CRITERION,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_COMBINED_PROPERTY_UNSUPPORTED_CRITERION,
        errorDetails = propertyName,
    ) {}

    val enumSchema = enumSchemaOrNull
    val typeSchema = typeSchemaOrNull
    val exactTwoSubschemas = subschemas != null && subschemas.size == 2
    val enumSupportingJsonSchema = typeSchema?.resolveEnumSupportingJsonSchema()
    val subSchemasForEnumUsecaseValidation = createValidation(
        errorCondition = !exactTwoSubschemas || enumSchema == null || enumSupportingJsonSchema == null,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_COMBINED_PROPERTY_ONLY_SUPPORTS_ENUM_USECASE_FOR_STRING_AND_NUMBER,
        errorDetails = "$propertyName: ${subschemas.map { it.javaClass.simpleName }.sorted()}"
    ) {}

    val enumAndTypeValidationValidation: ValidatedNel<Error, Unit> = if (enumSchema != null && typeSchema != null && enumSupportingJsonSchema != null) {

        val enumNotSupportedValidation = createValidation(
            errorCondition = !enumSupportingJsonSchema.enumDefinitionSupported(typeSchema),
            errorCode = AppErrorCodes.DATATYPE_SCHEMA_ENUM_PROPERTY_NOT_SUPPORTED,
            errorDetails = propertyName,
        ) {}

        val enumAndTypeValidationValidation = createValidation(
            errorCondition = enumSchema != null && typeSchema != null && enumSupportingJsonSchema != null,
            errorCode = AppErrorCodes.DATATYPE_SCHEMA_ENUM_PROPERTY_NOT_SUPPORTED,
            errorDetails = propertyName
        ) {}

        val enumValuesMissingValidation = createValidation(
            errorCondition = enumSchema.possibleValues == null || enumSchema.possibleValues.isEmpty(),
            errorCode = AppErrorCodes.DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISSING,
            errorDetails = propertyName,
        ) {}

        val enumValuesMismatchingTypeValidation = createValidation(
            errorCondition = !enumSupportingJsonSchema.enumValuesTypeMatches(typeSchema, enumSchema),
            errorCode = AppErrorCodes.DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISMATCHING_TYPE,
            errorDetails = propertyName
        ) {}

        val delegatedTypeSchemaValidationValidation =
            enumSupportingJsonSchema.delegateTypeSchemaValidation(typeSchema, propertyName)

        listOf(
            enumNotSupportedValidation,
            enumValuesMissingValidation,
            enumValuesMismatchingTypeValidation,
            delegatedTypeSchemaValidationValidation
        ).reduceWithFirstValue()
    } else {
        Validated.validNel(Unit)
    }

    val unprocessedPropertiesValidation = createValidation(
        errorCondition = unprocessedProperties.isNotEmpty(),
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
        errorDetails = "$propertyName: $unprocessedProperties",
    ) {}

    return listOf(
        commonAnnotationsValidation,
        criterionValidation,
        subSchemasForEnumUsecaseValidation,
        enumAndTypeValidationValidation,
        unprocessedPropertiesValidation
    ).reduceWithFirstValue()
}
