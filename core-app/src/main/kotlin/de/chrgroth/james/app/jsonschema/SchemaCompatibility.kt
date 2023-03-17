package de.chrgroth.james.app.jsonschema

import arrow.core.Validated
import arrow.core.ValidatedNel
import de.chrgroth.james.DomainError
import de.chrgroth.james.DomainErrorCode
import de.chrgroth.james.createValidation
import de.chrgroth.james.reduceWithFirstValue
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.CombinedSchema
import org.everit.json.schema.EnumSchema
import org.everit.json.schema.FormatValidator
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.StringSchema
import kotlin.reflect.KClass

enum class SchemaCompatibilityDomainErrorCodes : DomainErrorCode {
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

internal fun ObjectSchema.computeCompatibility(next: ObjectSchema): ValidatedNel<DomainError, Unit> {
    val currentProperties = propertySchemas.keys
    val nextProperties = next.propertySchemas.keys

    val removedProperties = currentProperties.minus(nextProperties)
    val removedPropertiesValidation = createValidation(
        errorCondition = removedProperties.isNotEmpty(),
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.PROPERTY_REMOVED,
        errorDetails = removedProperties.sorted().toString(),
    ) {}

    val newRequiredPropertiesWithoutDefault = nextProperties.minus(currentProperties).toSet()
        .filter { next.requiredProperties.contains(it) }
        .filter { next.propertySchemas[it]?.defaultValue == null }
    val newRequiredPropertiesWithoutDefaultValidation = createValidation(
        errorCondition = newRequiredPropertiesWithoutDefault.isNotEmpty(),
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.NEW_REQUIRED_PROPERTY_WITHOUT_DEFAULT,
        errorDetails = newRequiredPropertiesWithoutDefault.sorted().toString(),
    ) {}

    val keptProperties = currentProperties.intersect(nextProperties).toSet()
    val keptPropertiesMadeRequiredWithoutDefault = keptProperties
        .filter {
            val wasNotRequiredOrHadDefault =
                !requiredProperties.contains(it) || propertySchemas[it]?.defaultValue != null
            val isNowRequiredWithoutDefault =
                next.requiredProperties.contains(it) && next.propertySchemas[it]?.defaultValue == null

            wasNotRequiredOrHadDefault && isNowRequiredWithoutDefault
        }
    val keptPropertiesMadeRequiredWithoutDefaultValidation = createValidation(
        errorCondition = keptPropertiesMadeRequiredWithoutDefault.isNotEmpty(),
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.PROPERTY_MADE_REQUIRED_WITHOUT_DEFAULT,
        errorDetails = keptPropertiesMadeRequiredWithoutDefault.sorted().toString(),
    ) {}

    fun <PropertyType : Any> matchPropertySchemas(expectedSchemaType: KClass<PropertyType>) =
        keptProperties.mapNotNull {
            val currentSchema = propertySchemas.filterProperties(expectedSchemaType)[it]
            val nextSchema = next.propertySchemas.filterProperties(expectedSchemaType)[it]

            if (currentSchema != null && nextSchema != null) {
                currentSchema to nextSchema
            } else null
        }

    // BooleanSchema cannot be breaking apart from default value change handled above

    val keptArrayPropertiesValidation = matchPropertySchemas(ArraySchema::class).map {
        it.first.computeCompatibility(it.second)
    }.reduceWithFirstValue()
    val keptCombinedPropertiesValidation = matchPropertySchemas(CombinedSchema::class).map {
        it.first.computeCompatibility(it.second)
    }.reduceWithFirstValue()
    val keptNumberPropertiesValidation = matchPropertySchemas(NumberSchema::class).map {
        it.first.computeCompatibility(it.second)
    }.reduceWithFirstValue()
    val keptStringPropertiesValidation = matchPropertySchemas(StringSchema::class).map {
        it.first.computeCompatibility(it.second)
    }.reduceWithFirstValue()

    return listOf(
        removedPropertiesValidation,
        newRequiredPropertiesWithoutDefaultValidation,
        keptPropertiesMadeRequiredWithoutDefaultValidation,
        keptArrayPropertiesValidation,
        keptCombinedPropertiesValidation,
        keptNumberPropertiesValidation,
        keptStringPropertiesValidation
    ).reduceWithFirstValue()
}

internal fun ArraySchema.computeCompatibility(next: ArraySchema): ValidatedNel<DomainError, Unit> {
    val modeChanged = mode != null && next.mode != null && mode != next.mode
    val modeChangedValidation = createValidation(
        errorCondition = modeChanged,
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.ARRAY_PROPERTY_MODE_CHANGED,
        errorDetails = "$mode -> ${next.mode}"
    ) {}

    val modeSpecificValidation = if (mode == ArraySchemaMode.LIST && next.mode == ArraySchemaMode.LIST) {
        computeCompatibilityInListMode(next)
    } else if (mode == ArraySchemaMode.TUPLE && next.mode == ArraySchemaMode.TUPLE) {
        computeCompatibilityInTupleMode(next)
    } else {
        Validated.validNel(Unit)
    }

    return listOf(modeSpecificValidation, modeChangedValidation).reduceWithFirstValue()
}

internal fun ArraySchema.computeCompatibilityInListMode(next: ArraySchema): ValidatedNel<DomainError, Unit> {
    val minIncreased = minItemsNullSafe < next.minItemsNullSafe
    val minIncreasedValidation = createValidation(
        errorCondition = minIncreased,
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.ARRAY_PROPERTY_LIST_MIN_ITEMS_INCREASED,
        errorDetails = "$minItemsNullSafe -> ${next.minItemsNullSafe}"
    ) {}

    val maxDecreased = maxItemsNullSafe > next.maxItemsNullSafe
    val maxDecreasedValidation = createValidation(
        errorCondition = maxDecreased,
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.ARRAY_PROPERTY_LIST_MAX_ITEMS_DECREASED,
        errorDetails = "$maxItemsNullSafe -> ${next.maxItemsNullSafe}"
    ) {}

    val allItemsSchemaChanged = allItemSchema != null && next.allItemSchema != null &&
            allItemSchema != next.allItemSchema
    val allItemsSchemaChangedValidation = createValidation(
        errorCondition = allItemsSchemaChanged,
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.ARRAY_PROPERTY_LIST_ITEMS_SCHEMA_CHANGED,
        errorDetails = "${allItemSchema.javaClass.simpleName} -> ${next.allItemSchema.javaClass.simpleName}"
    ) {}

    return listOf(minIncreasedValidation, maxDecreasedValidation, allItemsSchemaChangedValidation).reduceWithFirstValue()
}

internal fun ArraySchema.computeCompatibilityInTupleMode(next: ArraySchema): ValidatedNel<DomainError, Unit> {
    val itemsSchemaChanged = itemSchemas != next.itemSchemas
    val itemsSchemaChangedValidation = createValidation(
        itemsSchemaChanged,
        SchemaCompatibilityDomainErrorCodes.ARRAY_PROPERTY_TUPLE_ITEMS_SCHEMA_CHANGED,
        "${itemSchemas.map { it.javaClass.simpleName }} -> ${next.itemSchemas.map { it.javaClass.simpleName }}"
    ) {}

    return itemsSchemaChangedValidation
}

internal fun CombinedSchema.computeCompatibility(next: CombinedSchema): ValidatedNel<DomainError, Unit> {
    val enumSchema = enumSchemaOrNull
    val nextEnumSchema = next.enumSchemaOrNull
    val enumValidation = if (enumSchema != null && nextEnumSchema != null) {
        enumSchema.computeCompatibility(nextEnumSchema)
    } else {
        Validated.validNel(Unit)
    }

    val typeSchema = typeSchemaOrNull
    val nextTypeSchema = next.typeSchemaOrNull
    val enumSupportingJsonSchema = typeSchema?.resolveEnumSupportingJsonSchema()
    val delegateValidation = if (enumSupportingJsonSchema != null && nextTypeSchema != null) {
        enumSupportingJsonSchema.delegateCompatibilityCheck(typeSchema, nextTypeSchema)
    } else {
        Validated.validNel(Unit)
    }

    return listOf(enumValidation, delegateValidation).reduceWithFirstValue()
}

internal fun EnumSchema.computeCompatibility(next: EnumSchema): ValidatedNel<DomainError, Unit> {
    val removedPossibleValues = possibleValues.filter { !next.possibleValues.contains(it) }

    return createValidation(
        errorCondition = removedPossibleValues.isNotEmpty(),
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.ENUM_PROPERTY_POSSIBLE_VALUE_REMOVED,
        errorDetails = removedPossibleValues.sortedBy { it.toString() }.toSet().toString()
    ) {}
}

internal fun NumberSchema.computeCompatibility(next: NumberSchema): ValidatedNel<DomainError, Unit> {
    val minIncreased = combinedMinimum.toLong() < next.combinedMinimum.toLong()
    val minIncreasedValidation = createValidation(
        errorCondition = minIncreased,
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.NUMBER_PROPERTY_MIN_INCREASED,
        errorDetails = "$combinedMinimum -> ${next.combinedMinimum}"
    ) {}

    val maxDecreased = combinedMaximum.toLong() > next.combinedMaximum.toLong()
    val maxDecreasedValidation = createValidation(
        errorCondition = maxDecreased,
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.NUMBER_PROPERTY_MAX_DECREASED,
        errorDetails = "$combinedMaximum -> ${next.combinedMaximum}"
    ) {}

    val multipleOfIntroduced = multipleOf == null && next.multipleOf != null
    val multipleOfChangedAndMoreStrict = multipleOf != null && next.multipleOf != null && multipleOf.toLong().rem(next.multipleOf.toLong()) != 0.toLong()
    val multipleOfMoreStrict = multipleOfIntroduced || multipleOfChangedAndMoreStrict
    val multipleOfMoreStrictValidation = createValidation(
        errorCondition = multipleOfMoreStrict,
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.NUMBER_PROPERTY_MULTIPLE_OF_MORE_STRICT,
        errorDetails = "$multipleOf -> ${next.multipleOf}"
    ) {}

    return listOf(minIncreasedValidation, maxDecreasedValidation, multipleOfMoreStrictValidation).reduceWithFirstValue()
}

internal fun StringSchema.computeCompatibility(next: StringSchema): ValidatedNel<DomainError, Unit> {
    val minIncreased = minLengthNullSafe < next.minLengthNullSafe
    val minIncreasedValidation = createValidation(
        errorCondition = minIncreased,
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.STRING_PROPERTY_MIN_LENGTH_INCREASED,
        errorDetails = "$minLengthNullSafe -> ${next.minLengthNullSafe}"
    ) {}

    val maxDecreased = maxLengthNullSafe > next.maxLengthNullSafe
    val maxDecreasedValidation = createValidation(
        errorCondition = maxDecreased,
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.STRING_PROPERTY_MAX_LENGTH_DECREASED,
        errorDetails = "$maxLengthNullSafe -> ${next.maxLengthNullSafe}"
    ) {}

    val patternChanged = next.pattern != null && pattern != next.pattern
    val patternChangedValidation = createValidation(
        errorCondition = patternChanged,
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.STRING_PROPERTY_PATTERN_CHANGED,
        errorDetails = "$pattern -> ${next.pattern}"
    ) {}

    val formatChanged = next.formatValidator != null && next.formatValidator != FormatValidator.NONE &&
            formatValidator != next.formatValidator
    val formatChangedValidation = createValidation(
        errorCondition = formatChanged,
        domainErrorCode = SchemaCompatibilityDomainErrorCodes.STRING_PROPERTY_FORMAT_CHANGED,
        errorDetails = "${formatValidator.formatName()} -> ${next.formatValidator.formatName()}"
    ) {}

    return listOf(
        minIncreasedValidation,
        maxDecreasedValidation,
        patternChangedValidation,
        formatChangedValidation
    ).reduceWithFirstValue()
}
