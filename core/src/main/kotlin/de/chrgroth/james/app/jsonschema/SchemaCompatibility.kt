package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.ErrorCode
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.combine
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.CombinedSchema
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.StringSchema
import kotlin.reflect.KClass

enum class SchemaCompatibilityErrorCodes : ErrorCode {

    PROPERTY_REMOVED,
    NEW_REQUIRED_PROPERTY_WITHOUT_DEFAULT,
    EXISTING_PROPERTY_MADE_REQUIRED_OR_ALREADY_WAS_REQUIRED_BUT_DEFAULT_REMOVED;

    override val prefix = "SCHEMA_COMPATIBILITY"
    override val id = ordinal.toLong()
}

// TODO #17 tests
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
        .filter { next.propertySchemas[it]?.defaultValue == null ?: false }
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
                !requiredProperties.contains(it) || propertySchemas[it]?.defaultValue != null ?: false
            val isNowRequiredWithoutDefault =
                next.requiredProperties.contains(it) && next.propertySchemas[it]?.defaultValue == null ?: false

            wasNotRequiredOrHadDefault && isNowRequiredWithoutDefault
        }
    val keptPropertiesMadeRequiredWithoutDefaultError = if (keptPropertiesMadeRequiredWithoutDefault.isNotEmpty()) {
        Error<Unit>(
            code = SchemaCompatibilityErrorCodes.EXISTING_PROPERTY_MADE_REQUIRED_OR_ALREADY_WAS_REQUIRED_BUT_DEFAULT_REMOVED,
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

// TODO #17 check all kept array properties for breaking changes
internal fun ArraySchema.computeCompatibility(next: ArraySchema): Errors<Unit>? {
    return null
}

// TODO #17 check all kept combined/enum properties for breaking changes
internal fun CombinedSchema.computeCompatibility(next: CombinedSchema): Errors<Unit>? {
    return null
}

// TODO #17 check all kept number properties for breaking changes
internal fun NumberSchema.computeCompatibility(next: NumberSchema): Errors<Unit>? {
    return null
}

// TODO #17 check all kept string properties for breaking changes
internal fun StringSchema.computeCompatibility(next: StringSchema): Errors<Unit>? {
    return null
}
