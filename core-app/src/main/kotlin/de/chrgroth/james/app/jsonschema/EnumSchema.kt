package de.chrgroth.james.app.jsonschema

import arrow.core.Validated
import arrow.core.ValidatedNel
import de.chrgroth.james.Error
import org.everit.json.schema.EnumSchema
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.Schema
import org.everit.json.schema.StringSchema
import java.math.BigDecimal

internal val EnumSchema.possibleValuesNullSafe: Set<Any>
    get() = possibleValues?.filterNotNull()?.toSet() ?: emptySet()

fun Schema.resolveEnumSupportingJsonSchema() = when (this) {
    is StringSchema -> StringEnumSchema
    is NumberSchema -> NumberEnumSchema
    else -> null
}

sealed class EnumSupportingJsonSchema {
    abstract fun enumDefinitionSupported(typeSchema: Schema): Boolean
    abstract fun enumValuesTypeMatches(typeSchema: Schema, enumSchema: EnumSchema): Boolean
    abstract fun delegateTypeSchemaValidation(typeSchema: Schema, propertyName: String): ValidatedNel<Error, Unit>
    abstract fun delegateCompatibilityCheck(typeSchema: Schema, nextTypeSchema: Schema): ValidatedNel<Error, Unit>
}

object StringEnumSchema : EnumSupportingJsonSchema() {
    override fun enumDefinitionSupported(typeSchema: Schema) = typeSchema is StringSchema

    override fun enumValuesTypeMatches(typeSchema: Schema, enumSchema: EnumSchema) =
        enumSchema.possibleValuesNullSafe.all { it is String }

    override fun delegateTypeSchemaValidation(typeSchema: Schema, propertyName: String): ValidatedNel<Error, Unit> =
        if (typeSchema is StringSchema) {
            typeSchema.validateDefinition(propertyName)
        } else {
            Validated.validNel(Unit)
        }

    override fun delegateCompatibilityCheck(typeSchema: Schema, nextTypeSchema: Schema): ValidatedNel<Error, Unit> =
        if (typeSchema is StringSchema && nextTypeSchema is StringSchema) {
            typeSchema.computeCompatibility(nextTypeSchema)
        }  else {
            Validated.validNel(Unit)
        }
}

object NumberEnumSchema : EnumSupportingJsonSchema() {
    override fun enumDefinitionSupported(typeSchema: Schema) = typeSchema is NumberSchema

    override fun enumValuesTypeMatches(typeSchema: Schema, enumSchema: EnumSchema) =
        typeSchema is NumberSchema && enumSchema.possibleValuesNullSafe.all {
            if (typeSchema.requiresInteger()) {
                it is Int
            } else {
                it is Int || it is BigDecimal
            }
        }

    override fun delegateTypeSchemaValidation(typeSchema: Schema, propertyName: String): ValidatedNel<Error, Unit> =
        if (typeSchema is NumberSchema) {
            typeSchema.validateDefinition(propertyName)
        } else {
            Validated.validNel(Unit)
        }

    override fun delegateCompatibilityCheck(typeSchema: Schema, nextTypeSchema: Schema): ValidatedNel<Error, Unit> =
        if (typeSchema is NumberSchema && nextTypeSchema is NumberSchema) {
            typeSchema.computeCompatibility(nextTypeSchema)
        } else {
            Validated.validNel(Unit)
        }
}
