package de.chrgroth.james.app.jsonschema

import arrow.core.ValidatedNel
import de.chrgroth.james.DomainError
import de.chrgroth.james.app.AppDomainErrorCodes
import de.chrgroth.james.createValidation
import de.chrgroth.james.reduceWithFirstValue
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.ObjectSchema

internal fun ObjectSchema.validateBooleanProperties(): ValidatedNel<DomainError, Unit> =
    filterProperties(BooleanSchema::class)
        .mapNotNull { it.value.validateDefinition(propertyName = it.key) }
        .reduceWithFirstValue(valueProviderIfEmpty = { })

// see: https://json-schema.org/understanding-json-schema/reference/boolean.html
internal fun BooleanSchema.validateDefinition(propertyName: String): ValidatedNel<DomainError, Unit> {

    val commonAnnotationsValidation = validateCommonAnnotations(propertyName)

    val unprocessedPropertiesValidation = createValidation(
        errorCondition = unprocessedProperties.isNotEmpty(),
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
        errorMessage = "$propertyName: $unprocessedProperties"
        ) {}

    return listOf(
        commonAnnotationsValidation,
        unprocessedPropertiesValidation
    ).reduceWithFirstValue()
}
