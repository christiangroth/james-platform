package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.ErrorCode
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.combine
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.CombinedSchema
import org.everit.json.schema.EnumSchema
import org.everit.json.schema.FormatValidator
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.StringSchema
import kotlin.reflect.KClass

enum class SchemaCompatibilityErrorCodes : ErrorCode {
    PROPERTY_REMOVED,
    NEW_REQUIRED_PROPERTY_WITHOUT_DEFAULT,
    PROPERTY_MADE_REQUIRED_WITHOUT_DEFAULT,

    ARRAY_PROPERTY_MODE_CHANGED,
    ARRAY_PROPERTY_LIST_MIN_ITEMS_INCREASED,
    ARRAY_PROPERTY_LIST_MAX_ITEMS_DECREASED,
    ARRAY_PROPERTY_LIST_ITEMS_SCHEMA_CHANGED,
    ARRAY_PROPERTY_TUPLE_ITEMS_SCHEMA_CHANGED,

    NUMBER_PROPERTY_MIN_INCREASED,
    NUMBER_PROPERTY_MAX_DECREASED,
    NUMBER_PROPERTY_MULTIPLE_OF_MORE_STRICT,

    STRING_PROPERTY_MIN_LENGTH_INCREASED,
    STRING_PROPERTY_MAX_LENGTH_DECREASED,
    STRING_PROPERTY_PATTERN_CHANGED,
    STRING_PROPERTY_FORMAT_CHANGED,

    ENUM_PROPERTY_POSSIBLE_VALUE_REMOVED;

    override val prefix = "SCHEMA_COMPATIBILITY"
    override val id = ordinal.toLong()
}

internal fun ObjectSchema.computeCompatibility(next: ObjectSchema): Maybe<Unit> {
    val currentProperties = propertySchemas.keys
    val nextProperties = next.propertySchemas.keys

    val removedProperties = currentProperties.minus(nextProperties)
    val removedPropertiesError = if (removedProperties.isNotEmpty()) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.PROPERTY_REMOVED,
            details = removedProperties.sorted().toString(),
        )
    } else null

    val newRequiredPropertiesWithoutDefault = nextProperties.minus(currentProperties).toSet()
        .filter { next.requiredProperties.contains(it) }
        .filter { next.propertySchemas[it]?.defaultValue == null }
    val newRequiredPropertiesWithoutDefaultError = if (newRequiredPropertiesWithoutDefault.isNotEmpty()) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.NEW_REQUIRED_PROPERTY_WITHOUT_DEFAULT,
            details = newRequiredPropertiesWithoutDefault.sorted().toString(),
        )
    } else null

    val keptProperties = currentProperties.intersect(nextProperties).toSet()
    val keptPropertiesMadeRequiredWithoutDefault = keptProperties
        .filter {
            val wasNotRequiredOrHadDefault =
                !requiredProperties.contains(it) || propertySchemas[it]?.defaultValue != null
            val isNowRequiredWithoutDefault =
                next.requiredProperties.contains(it) && next.propertySchemas[it]?.defaultValue == null

            wasNotRequiredOrHadDefault && isNowRequiredWithoutDefault
        }
    val keptPropertiesMadeRequiredWithoutDefaultError = if (keptPropertiesMadeRequiredWithoutDefault.isNotEmpty()) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.PROPERTY_MADE_REQUIRED_WITHOUT_DEFAULT,
            details = keptPropertiesMadeRequiredWithoutDefault.sorted().toString(),
        )
    } else null

    fun <PropertyType : Any> matchPropertySchemas(expectedSchemaType: KClass<PropertyType>) =
        keptProperties.mapNotNull {
            val currentSchema = propertySchemas.filterProperties(expectedSchemaType)[it]
            val nextSchema = next.propertySchemas.filterProperties(expectedSchemaType)[it]

            if (currentSchema != null && nextSchema != null) {
                currentSchema to nextSchema
            } else null
        }

    // BooleanSchema cannot be breaking apart from default value change handled above

    val keptArrayPropertiesResults = matchPropertySchemas(ArraySchema::class).map {
        it.first.computeCompatibility(it.second)
    }.combine()
    val keptCombinedPropertiesResults = matchPropertySchemas(CombinedSchema::class).map {
        it.first.computeCompatibility(it.second)
    }.combine()
    val keptNumberPropertiesResults = matchPropertySchemas(NumberSchema::class).map {
        it.first.computeCompatibility(it.second)
    }.combine()
    val keptStringPropertiesResults = matchPropertySchemas(StringSchema::class).map {
        it.first.computeCompatibility(it.second)
    }.combine()

    val errors = removedPropertiesError
        .combine(newRequiredPropertiesWithoutDefaultError)
        .combine(keptPropertiesMadeRequiredWithoutDefaultError)
        .combine(keptArrayPropertiesResults)
        .combine(keptCombinedPropertiesResults)
        .combine(keptNumberPropertiesResults)
        .combine(keptStringPropertiesResults)

    return errors ?: Result(Unit)
}

internal fun ArraySchema.computeCompatibility(next: ArraySchema): Errors<Unit>? {
    val modeChanged = mode != null && next.mode != null && mode != next.mode
    val modeChangedError = if (modeChanged) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_MODE_CHANGED,
            details = "$mode -> ${next.mode}"
        )
    } else null

    val modeSpecificErrors = if (mode == ArraySchemaMode.LIST && next.mode == ArraySchemaMode.LIST) {
        computeCompatibilityInListMode(next)
    } else if (mode == ArraySchemaMode.TUPLE && next.mode == ArraySchemaMode.TUPLE) {
        computeCompatibilityInTupleMode(next)
    } else null

    return modeSpecificErrors
        .combine(modeChangedError)
}

internal fun ArraySchema.computeCompatibilityInListMode(next: ArraySchema): Errors<Unit>? {
    val minIncreased = minItemsNullSafe < next.minItemsNullSafe
    val minIncreasedError = if (minIncreased) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_LIST_MIN_ITEMS_INCREASED,
            details = "$minItemsNullSafe -> ${next.minItemsNullSafe}"
        )
    } else null

    val maxDecreased = maxItemsNullSafe > next.maxItemsNullSafe
    val maxDecreasedError = if (maxDecreased) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_LIST_MAX_ITEMS_DECREASED,
            details = "$maxItemsNullSafe -> ${next.maxItemsNullSafe}"
        )
    } else null

    val allItemsSchemaChanged = allItemSchema != null && next.allItemSchema != null &&
            allItemSchema != next.allItemSchema
    val allItemsSchemaChangedError = if (allItemsSchemaChanged) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_LIST_ITEMS_SCHEMA_CHANGED,
            details = "${allItemSchema.javaClass.simpleName} -> ${next.allItemSchema.javaClass.simpleName}"
        )
    } else null

    return minIncreasedError
        .combine(maxDecreasedError)
        .combine(allItemsSchemaChangedError)
}

internal fun ArraySchema.computeCompatibilityInTupleMode(next: ArraySchema): Errors<Unit>? {
    val itemsSchemaChanged = itemSchemas != next.itemSchemas
    val itemsSchemaChangedError = if (itemsSchemaChanged) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_TUPLE_ITEMS_SCHEMA_CHANGED,
            details = "${itemSchemas.map { it.javaClass.simpleName }} -> ${next.itemSchemas.map { it.javaClass.simpleName }}"
        )
    } else null

    return itemsSchemaChangedError.combine(null)
}

internal fun CombinedSchema.computeCompatibility(next: CombinedSchema): Errors<Unit>? {
    val enumSchema = enumSchemaOrNull
    val nextEnumSchema = next.enumSchemaOrNull
    val enumErrors = if (enumSchema != null && nextEnumSchema != null) {
        enumSchema.computeCompatibility(nextEnumSchema)
    } else null

    val typeSchema = typeSchemaOrNull
    val nextTypeSchema = next.typeSchemaOrNull
    val enumSupportingJsonSchema = typeSchema?.resolveEnumSupportingJsonSchema()
    val delegateErrors = if (enumSupportingJsonSchema != null && nextTypeSchema != null) {
        enumSupportingJsonSchema.delegateCompatibilityCheck(typeSchema, nextTypeSchema)
    } else null

    return enumErrors
        .combine(delegateErrors)
}

internal fun EnumSchema.computeCompatibility(next: EnumSchema): Errors<Unit>? {
    val removedPossibleValues = possibleValues.filter { !next.possibleValues.contains(it) }
    val removedPossibleValuesError = if (removedPossibleValues.isNotEmpty()) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.ENUM_PROPERTY_POSSIBLE_VALUE_REMOVED,
            details = removedPossibleValues.sortedBy { it.toString() }.toSet().toString()
        )
    } else null

    return removedPossibleValuesError.combine(null)
}

internal fun NumberSchema.computeCompatibility(next: NumberSchema): Errors<Unit>? {
    val minIncreased = combinedMinimum.toLong() < next.combinedMinimum.toLong()
    val minIncreasedError = if (minIncreased) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MIN_INCREASED,
            details = "$combinedMinimum -> ${next.combinedMinimum}"
        )
    } else null

    val maxDecreased = combinedMaximum.toLong() > next.combinedMaximum.toLong()
    val maxDecreasedError = if (maxDecreased) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MAX_DECREASED,
            details = "$combinedMaximum -> ${next.combinedMaximum}"
        )
    } else null

    val multipleOfIntroduced = multipleOf == null && next.multipleOf != null
    val multipleOfChangedAndMoreStrict = multipleOf != null && next.multipleOf != null && multipleOf.toLong().rem(next.multipleOf.toLong()) != 0.toLong()
    val multipleOfMoreStrict = multipleOfIntroduced || multipleOfChangedAndMoreStrict
    val multipleOfMoreStrictError = if (multipleOfMoreStrict) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MULTIPLE_OF_MORE_STRICT,
            details = "$multipleOf -> ${next.multipleOf}"
        )
    } else null

    return minIncreasedError
        .combine(maxDecreasedError)
        .combine(multipleOfMoreStrictError)
}

internal fun StringSchema.computeCompatibility(next: StringSchema): Errors<Unit>? {
    val minIncreased = minLengthNullSafe < next.minLengthNullSafe
    val minIncreasedError = if (minIncreased) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_MIN_LENGTH_INCREASED,
            details = "$minLengthNullSafe -> ${next.minLengthNullSafe}"
        )
    } else null

    val maxDecreased = maxLengthNullSafe > next.maxLengthNullSafe
    val maxDecreasedError = if (maxDecreased) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_MAX_LENGTH_DECREASED,
            details = "$maxLengthNullSafe -> ${next.maxLengthNullSafe}"
        )
    } else null

    val patternChanged = next.pattern != null && pattern != next.pattern
    val patternChangedError = if (patternChanged) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_PATTERN_CHANGED,
            details = "$pattern -> ${next.pattern}"
        )
    } else null

    val formatChanged = next.formatValidator != null && next.formatValidator != FormatValidator.NONE &&
            formatValidator != next.formatValidator
    val formatChangedError = if (formatChanged) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_FORMAT_CHANGED,
            details = "${formatValidator.formatName()} -> ${next.formatValidator.formatName()}"
        )
    } else null

    return minIncreasedError
        .combine(maxDecreasedError)
        .combine(patternChangedError)
        .combine(formatChangedError)
}
