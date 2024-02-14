package de.chrgroth.james.typesystem

import arrow.core.Either
import de.chrgroth.james.DomainError
import de.chrgroth.james.typesystem.DataobjectFieldSpecFormat.YAML
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.util.UUID

// TODO #2 add read only?
// TODO #2 add value proposal dependencies?
// TODO #2 add default value callback?
// TODO #2 reduce massive usage of listOf and listOfNotNull

data class Dataobject(
    val id: UUID,
    val datatypeName: String,
    val datatypeVersionMajor: ULong,
    val datatypeVersionMinor: ULong,
    // TODO #2 maybe define a FieldPath instead of string to avoid nested maps? or ensure no "." and "[]" in values key
    val values: Map<String, Any>
)

// TODO #2 combine with DomainError somehow?
// TODO #2 add details like field name etc
enum class DatatypeBreakingChange {
    NAME_CHANGED,

    MANDATORY_FIELD_ADDED,
    FIELD_REMOVED,

    FIELD_NAME_CHANGED,
    FIELD_TYPE_CHANGED,
    FIELD_SPEC_TYPE_CHANGED,

    FIELD_MADE_MANDATORY_WITHOUT_DEFAULT,
    REMOVED_DEFAULT_FOR_MANDATORY_FIELD,

    FIELD_INTRODUCES_ALLOWED_VALUES,
    FIELD_RESTRICTS_ALLOWED_VALUES,

    STRING_FIELD_RESTRICTS_MIN_LENGTH,
    STRING_FIELD_RESTRICTS_MAX_LENGTH,
    STRING_FIELD_RESTRICTS_PATTERN,

    NUMERIC_FIELD_RESTRICTS_MIN_VALUE,
    NUMERIC_FIELD_RESTRICTS_MAX_VALUE,

    DATE_FIELD_RESTRICTS_MIN_VALUE,
    DATE_FIELD_RESTRICTS_MAX_VALUE,

    TIME_FIELD_RESTRICTS_MIN_VALUE,
    TIME_FIELD_RESTRICTS_MAX_VALUE,

    DATE_TIME_FIELD_RESTRICTS_MIN_VALUE,
    DATE_TIME_FIELD_RESTRICTS_MAX_VALUE,
}

data class Datatype(
    val name: String,
    val displayName: String,
    val versionMajor: ULong,
    val versionMinor: ULong,
    val dataobjectDefinition: DataobjectFieldSpec
) {

    companion object {
        val NAME_PATTERN = Regex("[a-zA-Z]+[a-zA-Z0-9_-]*")

        fun create(name: String, displayName: String) = Datatype(
            name = name,
            displayName = displayName,
            versionMajor = 0.toULong(),
            versionMinor = 1.toULong(),
            dataobjectDefinition = DataobjectFieldSpec(
                fields = emptyList(),
                description = null,
            ),
        )

        @Suppress("LongParameterList")
        fun parse(name: String, displayName: String, versionMajor: ULong, versionMinor: ULong, content: String, format: DataobjectFieldSpecFormat, description: String?) =
            Datatype(
                name = name,
                displayName = displayName,
                versionMajor = versionMajor,
                versionMinor = versionMinor,
                dataobjectDefinition = DataobjectFieldSpec.parse(content, format, description)
            )
    }

    fun validate(): List<DomainError> {
        val nameIssue = if (!name.matches(NAME_PATTERN)) {
            DomainError(
                code = TypesystemDomainErrorCodes.DATATYPE_NAME_INVALID,
                errorMessage = null,
            )
        } else null

        return listOfNotNull(nameIssue) + dataobjectDefinition.validate()
    }

    fun validateValue(value: Dataobject): List<DomainError> {
        return dataobjectDefinition.validateValue(value)
    }

    fun computeBreakingChanges(newVersion: Datatype): List<DatatypeBreakingChange> {
        val nameBreakingChange = if (name != newVersion.name) {
            DatatypeBreakingChange.NAME_CHANGED
        } else null

        return dataobjectDefinition.computeBreakingChanges(newVersion.dataobjectDefinition)
            .plus(nameBreakingChange)
            .filterNotNull()
    }

    fun dump(format: DataobjectFieldSpecFormat): String =
        when(format) {
            YAML -> dumpYaml()
        }

    private fun dumpYaml(): String {
        return """
                # $name $displayName $versionMajor.$versionMinor
                ${dataobjectDefinition.dumpYaml()}
            """.trimIndent()
    }
}

sealed interface Field<SpecType, ValueType> {

    val name: String
    val displayName: String
    val isMandatory: Boolean
    val spec: FieldSpec<SpecType>
    val defaultValue: ValueType?
    val allowedValues: Set<SpecType>

    fun validateDefinition(): List<DomainError> {

        val nameIssue = if (!name.matches(NAME_PATTERN)) {
            DomainError(
                code = TypesystemDomainErrorCodes.FIELD_NAME_INVALID,
                errorMessage = null,
            )
        } else null

        val allowedValueIssues = allowedValues.flatMap { spec.validateValue(it) }

        val dataobjectFieldIssues = listOfNotNull(
            if (spec is DataobjectFieldSpec && defaultValue != null) {
                DomainError(
                    code = TypesystemDomainErrorCodes.OBJECT_FIELD_DEFAULT_VALUE_NOT_SUPPORTED,
                    errorMessage = null,
                )
            } else null,

            if (spec is DataobjectFieldSpec && allowedValues.isNotEmpty()) {
                DomainError(
                    code = TypesystemDomainErrorCodes.OBJECT_FIELD_ALLOWED_VALUES_NOT_SUPPORTED,
                    errorMessage = null,
                )
            } else null,
        )

        return listOfNotNull(nameIssue) + spec.validate() + validateDefaultValueDefinition() + allowedValueIssues + dataobjectFieldIssues
    }

    fun validateDefaultValueDefinition(): List<DomainError>

    fun validateValue(value: Any?): List<DomainError> {
        val mandatoryIssue = if (isMandatory && value == null) {
            DomainError(
                code = TypesystemDomainErrorCodes.MANDATORY_VALUE_MISSING,
                errorMessage = null,
            )
        } else null

        val validationIssues = if (value != null) {
            convertAndValidate(value)
        } else emptyList()

        return listOfNotNull(mandatoryIssue) + validationIssues
    }

    fun convertAndValidate(value: Any): List<DomainError>

    fun computeBreakingChanges(newVersion: Field<*, *>): List<DatatypeBreakingChange> {
        val nameBreakingChange = if (name != newVersion.name) {
            DatatypeBreakingChange.FIELD_NAME_CHANGED
        } else null

        val typeChanged = if (this::class != newVersion::class)
            DatatypeBreakingChange.FIELD_TYPE_CHANGED
        else null

        val newVersionMandatoryWithoutDefaultValue = newVersion.isMandatory && newVersion.defaultValue == null
        val madeMandatoryWithoutDefaultValue = if (!isMandatory && newVersionMandatoryWithoutDefaultValue)
            DatatypeBreakingChange.FIELD_MADE_MANDATORY_WITHOUT_DEFAULT
        else null

        val removedDefaultValueForMandatoryField =
            if (isMandatory && defaultValue != null && newVersionMandatoryWithoutDefaultValue)
                DatatypeBreakingChange.REMOVED_DEFAULT_FOR_MANDATORY_FIELD
            else null

        val allowedValuesAdded = if (allowedValues.isEmpty() && newVersion.allowedValues.isNotEmpty())
            DatatypeBreakingChange.FIELD_INTRODUCES_ALLOWED_VALUES
        else null

        val allowedValuesRestricted =
            if (allowedValues.isNotEmpty() && newVersion.allowedValues.isNotEmpty() && allowedValues.minus(newVersion.allowedValues)
                    .isNotEmpty()
            )
                DatatypeBreakingChange.FIELD_RESTRICTS_ALLOWED_VALUES
            else null

        val specBreakingChanges = spec.computeBreakingChanges(newVersion.spec)

        return listOfNotNull(
            nameBreakingChange,
            typeChanged,
            madeMandatoryWithoutDefaultValue,
            removedDefaultValueForMandatoryField,
            allowedValuesAdded,
            allowedValuesRestricted,
        ) + specBreakingChanges
    }

    companion object {
        val NAME_PATTERN = Regex("[a-zA-Z]+[a-zA-Z0-9_-]*")
    }
}

data class SingleValueField<T>(
    override val name: String,
    override val displayName: String,
    override val isMandatory: Boolean,
    override val spec: FieldSpec<T>,
    override val defaultValue: T?,
    override val allowedValues: Set<T>,
) : Field<T, T> {

    override fun validateDefaultValueDefinition(): List<DomainError> =
        defaultValue?.let { spec.validateValue(it) } ?: emptyList()

    override fun convertAndValidate(value: Any): List<DomainError> {
        return when (val conversion = spec.convert(value)) {
            is Either.Left -> conversion.value
            is Either.Right -> emptyList()
        }
    }
}

data class ListValueField<T>(
    override val name: String,
    override val displayName: String,
    override val isMandatory: Boolean,
    override val spec: FieldSpec<T>,
    override val defaultValue: List<T>?,
    override val allowedValues: Set<T>,
    val allowDuplicates: Boolean = true,
    val minValues: ULong = ULong.MIN_VALUE,
    val maxValues: ULong = ULong.MAX_VALUE,
) : Field<T, List<T>> {

    override fun validateDefaultValueDefinition(): List<DomainError> {
        if (defaultValue.isNullOrEmpty()) {
            return emptyList()
        }

        val valueIssues = defaultValue.flatMap { spec.validateValue(it) }

        val allowDuplicatesIssues = if (!allowDuplicates && defaultValue.findDuplicates().isNotEmpty()) {
            DomainError(
                code = TypesystemDomainErrorCodes.LIST_VALUE_FIELD_DEFAULT_VALUES_CONTAIN_DUPLICATES,
                errorMessage = null,
            )
        } else null

        val minMaxValuesIssues = listOfNotNull(
            if (defaultValue.size < minValues.toLong()) {
                DomainError(
                    code = TypesystemDomainErrorCodes.LIST_VALUE_FIELD_DEFAULT_VALUES_BELOW_MIN_VALUES,
                    errorMessage = null,
                )
            } else null,

            if (defaultValue.size > maxValues.toLong()) {
                DomainError(
                    code = TypesystemDomainErrorCodes.LIST_VALUE_FIELD_DEFAULT_VALUES_ABOVE_MAX_VALUES,
                    errorMessage = null,
                )
            } else null,
        )

        return valueIssues + listOfNotNull(allowDuplicatesIssues) + minMaxValuesIssues
    }

    override fun convertAndValidate(value: Any): List<DomainError> {
        if (value !is List<*>) {
            return listOf(
                DomainError(
                    code = TypesystemDomainErrorCodes.VALUE_TYPE_MISMATCH,
                    errorMessage = null,
                )
            )
        }

        val valueIssues = value.filterNotNull()
            .map { spec.convert(it) }
            .filterIsInstance<Either.Left<List<DomainError>>>()
            .flatMap { it.value }

        val duplicateValuesIssues = if (!allowDuplicates && value.findDuplicates().isNotEmpty()) {
            DomainError(
                code = TypesystemDomainErrorCodes.LIST_VALUE_FIELD_CONTAINS_DUPLICATES,
                errorMessage = null,
            )
        } else null

        val minMaxValuesIssues = listOfNotNull(
            if (value.size < minValues.toLong()) {
                DomainError(
                    code = TypesystemDomainErrorCodes.LIST_VALUE_FIELD_VALUES_BELOW_MIN_VALUES,
                    errorMessage = null,
                )
            } else null,

            if (value.size > maxValues.toLong()) {
                DomainError(
                    code = TypesystemDomainErrorCodes.LIST_VALUE_FIELD_VALUES_ABOVE_MAX_VALUES,
                    errorMessage = null,
                )
            } else null,
        )

        val nullValuesIssues = if (value.any { it == null }) {
            DomainError(
                code = TypesystemDomainErrorCodes.LIST_VALUE_FIELD_CONTAINS_NULL,
                errorMessage = null,
            )
        } else null

        return valueIssues + minMaxValuesIssues + listOfNotNull(duplicateValuesIssues, nullValuesIssues)
    }
}

sealed class FieldSpec<T> {
    abstract fun validate(): List<DomainError>
    abstract fun validateValue(value: T): List<DomainError>
    abstract fun convert(value: Any): Either<List<DomainError>, T>
    fun computeBreakingChanges(newVersion: FieldSpec<*>): List<DatatypeBreakingChange> =
        if (this.javaClass == newVersion.javaClass) {
            when (this) {
                is StringFieldSpec -> computeBreakingChanges(newVersion as StringFieldSpec)
                is LongFieldSpec -> computeBreakingChanges(newVersion as LongFieldSpec)
                is DoubleFieldSpec -> computeBreakingChanges(newVersion as DoubleFieldSpec)
                is BooleanFieldSpec -> computeBreakingChanges()
                is LocalDateFieldSpec -> computeBreakingChanges(newVersion as LocalDateFieldSpec)
                is LocalTimeFieldSpec -> computeBreakingChanges(newVersion as LocalTimeFieldSpec)
                is LocalDateTimeFieldSpec -> computeBreakingChanges(newVersion as LocalDateTimeFieldSpec)
                is DataobjectReferenceFieldSpec -> computeBreakingChanges(newVersion as DataobjectReferenceFieldSpec)
                is DataobjectFieldSpec -> computeBreakingChanges(newVersion as DataobjectFieldSpec)
            }
        } else {
            listOf(DatatypeBreakingChange.FIELD_SPEC_TYPE_CHANGED)
        }
}

data class StringFieldSpec(
    val minLength: UInt = UInt.MIN_VALUE,
    val maxLength: UInt = UInt.MAX_VALUE,
    val pattern: Regex? = null,
) : FieldSpec<String>() {
    override fun validate(): List<DomainError> {
        val minMaxIssue = if (minLength > maxLength) {
            DomainError(
                code = TypesystemDomainErrorCodes.STRING_FIELD_MIN_LENGTH_GREATER_MAX_LENGTH,
                errorMessage = null,
            )
        } else {
            null
        }

        return listOfNotNull(minMaxIssue)
    }

    override fun validateValue(value: String): List<DomainError> {
        val minLengthIssue = if (minLength > value.length.toUInt()) {
            DomainError(
                code = TypesystemDomainErrorCodes.STRING_VALUE_LENGTH_BELOW_MIN,
                errorMessage = null,
            )
        } else {
            null
        }

        val maxLengthIssue = if (maxLength < value.length.toUInt()) {
            DomainError(
                code = TypesystemDomainErrorCodes.STRING_VALUE_LENGTH_ABOVE_MAX,
                errorMessage = null,
            )
        } else {
            null
        }

        val patternIssue = if (pattern != null && !pattern.matches(value)) {
            DomainError(
                code = TypesystemDomainErrorCodes.STRING_VALUE_PATTERN_NOT_MATCHED,
                errorMessage = null,
            )
        } else {
            null
        }

        return listOfNotNull(minLengthIssue, maxLengthIssue, patternIssue)
    }

    override fun convert(value: Any): Either<List<DomainError>, String> =
        Either.conditionally(value is String, {
            listOf(
                DomainError(
                    code = TypesystemDomainErrorCodes.VALUE_TYPE_MISMATCH,
                    errorMessage = null,
                )
            )
        }, {
            value as String
        })

    fun computeBreakingChanges(newVersion: StringFieldSpec): List<DatatypeBreakingChange> {
        val minLengthValueBreakingChange = if (minLength < newVersion.minLength) {
            DatatypeBreakingChange.STRING_FIELD_RESTRICTS_MIN_LENGTH
        } else null

        val maxValueBreakingChange = if (maxLength > newVersion.maxLength) {
            DatatypeBreakingChange.STRING_FIELD_RESTRICTS_MAX_LENGTH
        } else null

        val patternIntroduced = pattern == null && newVersion.pattern != null
        val patternChanged = pattern != null && newVersion.pattern != null && pattern != newVersion.pattern
        val patternBreakingChange = if (patternIntroduced || patternChanged) {
            DatatypeBreakingChange.STRING_FIELD_RESTRICTS_PATTERN
        } else null

        return listOfNotNull(minLengthValueBreakingChange, maxValueBreakingChange, patternBreakingChange)
    }
}

// TODO #2 add units to numeric field?
sealed class NumericFieldSpec<T : Number> : FieldSpec<T>() {
    abstract val min: T
    abstract val max: T

    override fun validate(): List<DomainError> {
        val minMaxIssue = if (min.toDouble() > max.toDouble()) {
            DomainError(
                code = TypesystemDomainErrorCodes.NUMERIC_FIELD_MIN_GREATER_MAX,
                errorMessage = null,
            )
        } else {
            null
        }

        return listOfNotNull(minMaxIssue)
    }

    override fun validateValue(value: T): List<DomainError> {
        val minIssue = if (min.toDouble() > value.toDouble()) {
            DomainError(
                code = TypesystemDomainErrorCodes.NUMERIC_VALUE_BELOW_MIN,
                errorMessage = null,
            )
        } else {
            null
        }

        val maxIssue = if (max.toDouble() < value.toDouble()) {
            DomainError(
                code = TypesystemDomainErrorCodes.NUMERIC_VALUE_ABOVE_MAX,
                errorMessage = null,
            )
        } else {
            null
        }

        return listOfNotNull(minIssue, maxIssue)
    }

    fun computeBreakingChanges(newVersion: NumericFieldSpec<T>): List<DatatypeBreakingChange> {
        val minValueBreakingChange = if (min.toDouble() < newVersion.min.toDouble()) {
            DatatypeBreakingChange.NUMERIC_FIELD_RESTRICTS_MIN_VALUE
        } else null

        val maxValueBreakingChange = if (max.toDouble() > newVersion.max.toDouble()) {
            DatatypeBreakingChange.NUMERIC_FIELD_RESTRICTS_MAX_VALUE
        } else null

        return listOfNotNull(minValueBreakingChange, maxValueBreakingChange)
    }
}

data class LongFieldSpec(
    override val min: Long = Long.MIN_VALUE,
    override val max: Long = Long.MAX_VALUE,
) : NumericFieldSpec<Long>() {
    override fun convert(value: Any): Either<List<DomainError>, Long> =
        when (value) {
            is Long -> Either.Right(value)
            is Int -> Either.Right(value.toLong())
            is Short -> Either.Right(value.toLong())
            is Byte -> Either.Right(value.toLong())
            else -> Either.Left(
                listOf(
                    DomainError(
                        code = TypesystemDomainErrorCodes.VALUE_TYPE_MISMATCH,
                        errorMessage = null,
                    )
                )
            )
        }
}

data class DoubleFieldSpec(
    override val min: Double = Double.MIN_VALUE,
    override val max: Double = Double.MAX_VALUE,
) : NumericFieldSpec<Double>() {
    override fun convert(value: Any): Either<List<DomainError>, Double> =
        when (value) {
            is Double -> Either.Right(value)
            is Float -> Either.Right(value.toDouble())
            else -> Either.Left(
                listOf(
                    DomainError(
                        code = TypesystemDomainErrorCodes.VALUE_TYPE_MISMATCH,
                        errorMessage = null,
                    )
                )
            )
        }
}

data object BooleanFieldSpec : FieldSpec<Boolean>() {

    override fun validate(): List<DomainError> = emptyList()
    override fun validateValue(value: Boolean): List<DomainError> = emptyList()

    override fun convert(value: Any): Either<List<DomainError>, Boolean> =
        Either.conditionally(value is Boolean, {
            listOf(
                DomainError(
                    code = TypesystemDomainErrorCodes.VALUE_TYPE_MISMATCH,
                    errorMessage = null,
                )
            )
        }, {
            value as Boolean
        })

    fun computeBreakingChanges(): List<DatatypeBreakingChange> = emptyList()
}

// TODO #2 need to save time zone too? see https://github.com/Kotlin/kotlinx-datetime
// TODO #2 add some kind of granularity to be able to select days, weeks (monday), months (1st) or years (01.01.)?
data class LocalDateFieldSpec(
    val min: LocalDate?,
    val max: LocalDate?,
) : FieldSpec<LocalDate>() {

    override fun validate(): List<DomainError> {
        val minMaxIssue = if (min != null && max != null &&  min > max) {
            DomainError(
                code = TypesystemDomainErrorCodes.DATE_FIELD_MIN_GREATER_MAX,
                errorMessage = null,
            )
        } else {
            null
        }

        return listOfNotNull(minMaxIssue)
    }

    override fun validateValue(value: LocalDate): List<DomainError> {
        val minIssue = if (min != null && min > value) {
            DomainError(
                code = TypesystemDomainErrorCodes.DATE_VALUE_BELOW_MIN,
                errorMessage = null,
            )
        } else {
            null
        }

        val maxIssue = if (max != null && max < value) {
            DomainError(
                code = TypesystemDomainErrorCodes.DATE_VALUE_ABOVE_MAX,
                errorMessage = null,
            )
        } else {
            null
        }

        return listOfNotNull(minIssue, maxIssue)
    }

    override fun convert(value: Any): Either<List<DomainError>, LocalDate> =
        Either.conditionally(value is LocalDate, {
            listOf(
                DomainError(
                    code = TypesystemDomainErrorCodes.VALUE_TYPE_MISMATCH,
                    errorMessage = null,
                )
            )
        }, {
            value as LocalDate
        })

    fun computeBreakingChanges(newVersion: LocalDateFieldSpec): List<DatatypeBreakingChange> {
        val minIntroduced = min == null && newVersion.min != null
        val minRestricted = min != null && newVersion.min != null && min < newVersion.min
        val minValueBreakingChange = if (minIntroduced || minRestricted) {
            DatatypeBreakingChange.DATE_FIELD_RESTRICTS_MIN_VALUE
        } else null

        val maxIntroduced = max == null && newVersion.max != null
        val maxRestricted = max != null && newVersion.max != null && max > newVersion.max
        val maxValueBreakingChange = if (maxIntroduced || maxRestricted) {
            DatatypeBreakingChange.DATE_FIELD_RESTRICTS_MAX_VALUE
        } else null

        return listOfNotNull(minValueBreakingChange, maxValueBreakingChange)
    }
}

// TODO #2 need to save time zone too? see https://github.com/Kotlin/kotlinx-datetime
// TODO #2 add some kind of granularity to be able to select minutes, hours, ...
data class LocalTimeFieldSpec(
    val min: LocalTime?,
    val max: LocalTime?,
) : FieldSpec<LocalTime>() {

    override fun validate(): List<DomainError> {
        val minMaxIssue = if (min != null && max != null &&  min > max) {
            DomainError(
                code = TypesystemDomainErrorCodes.TIME_FIELD_MIN_GREATER_MAX,
                errorMessage = null,
            )
        } else {
            null
        }

        return listOfNotNull(minMaxIssue)
    }

    override fun validateValue(value: LocalTime): List<DomainError> {
        val minIssue = if (min != null && min > value) {
            DomainError(
                code = TypesystemDomainErrorCodes.TIME_VALUE_BELOW_MIN,
                errorMessage = null,
            )
        } else {
            null
        }

        val maxIssue = if (max != null && max < value) {
            DomainError(
                code = TypesystemDomainErrorCodes.TIME_VALUE_ABOVE_MAX,
                errorMessage = null,
            )
        } else {
            null
        }

        return listOfNotNull(minIssue, maxIssue)
    }

    override fun convert(value: Any): Either<List<DomainError>, LocalTime> =
        Either.conditionally(value is LocalTime, {
            listOf(
                DomainError(
                    code = TypesystemDomainErrorCodes.VALUE_TYPE_MISMATCH,
                    errorMessage = null,
                )
            )
        }, {
            value as LocalTime
        })

    fun computeBreakingChanges(newVersion: LocalTimeFieldSpec): List<DatatypeBreakingChange> {
        val minIntroduced = min == null && newVersion.min != null
        val minRestricted = min != null && newVersion.min != null && min < newVersion.min
        val minValueBreakingChange = if (minIntroduced || minRestricted) {
            DatatypeBreakingChange.TIME_FIELD_RESTRICTS_MIN_VALUE
        } else null

        val maxIntroduced = max == null && newVersion.max != null
        val maxRestricted = max != null && newVersion.max != null && max > newVersion.max
        val maxValueBreakingChange = if (maxIntroduced || maxRestricted) {
            DatatypeBreakingChange.TIME_FIELD_RESTRICTS_MAX_VALUE
        } else null

        return listOfNotNull(minValueBreakingChange, maxValueBreakingChange)
    }
}

// TODO #2 need to save time zone too? see https://github.com/Kotlin/kotlinx-datetime
// TODO #2 add some kind of granularity (see above)
data class LocalDateTimeFieldSpec(
    val min: LocalDateTime?,
    val max: LocalDateTime?,
) : FieldSpec<LocalDateTime>() {

    override fun validate(): List<DomainError> {
        val minMaxIssue = if (min != null && max != null &&  min > max) {
            DomainError(
                code = TypesystemDomainErrorCodes.DATE_TIME_FIELD_MIN_GREATER_MAX,
                errorMessage = null,
            )
        } else {
            null
        }

        return listOfNotNull(minMaxIssue)
    }

    override fun validateValue(value: LocalDateTime): List<DomainError> {
        val minIssue = if (min != null && min > value) {
            DomainError(
                code = TypesystemDomainErrorCodes.DATE_TIME_VALUE_BELOW_MIN,
                errorMessage = null,
            )
        } else {
            null
        }

        val maxIssue = if (max != null && max < value) {
            DomainError(
                code = TypesystemDomainErrorCodes.DATE_TIME_VALUE_ABOVE_MAX,
                errorMessage = null,
            )
        } else {
            null
        }

        return listOfNotNull(minIssue, maxIssue)
    }

    override fun convert(value: Any): Either<List<DomainError>, LocalDateTime> =
        Either.conditionally(value is LocalDateTime, {
            listOf(
                DomainError(
                    code = TypesystemDomainErrorCodes.VALUE_TYPE_MISMATCH,
                    errorMessage = null,
                )
            )
        }, {
            value as LocalDateTime
        })

    fun computeBreakingChanges(newVersion: LocalDateTimeFieldSpec): List<DatatypeBreakingChange> {
        val minIntroduced = min == null && newVersion.min != null
        val minRestricted = min != null && newVersion.min != null && min < newVersion.min
        val minValueBreakingChange = if (minIntroduced || minRestricted) {
            DatatypeBreakingChange.DATE_TIME_FIELD_RESTRICTS_MIN_VALUE
        } else null

        val maxIntroduced = max == null && newVersion.max != null
        val maxRestricted = max != null && newVersion.max != null && max > newVersion.max
        val maxValueBreakingChange = if (maxIntroduced || maxRestricted) {
            DatatypeBreakingChange.DATE_TIME_FIELD_RESTRICTS_MAX_VALUE
        } else null

        return listOfNotNull(minValueBreakingChange, maxValueBreakingChange)
    }
}

data class DataobjectReferenceFieldSpec(
    val datatypeName: String,
) : FieldSpec<UUID>() {

    override fun validate(): List<DomainError> {
        TODO("Not yet implemented")
    }

    override fun validateValue(value: UUID): List<DomainError> {
        TODO("Not yet implemented")
    }

    override fun convert(value: Any): Either<List<DomainError>, UUID> =
        Either.conditionally(value is UUID, {
            listOf(
                DomainError(
                    code = TypesystemDomainErrorCodes.VALUE_TYPE_MISMATCH,
                    errorMessage = null,
                )
            )
        }, {
            value as UUID
        })

    @Suppress("UnusedParameter")
    fun computeBreakingChanges(newVersion: DataobjectReferenceFieldSpec): List<DatatypeBreakingChange> {
        TODO("Not yet implemented")
    }
}

data class DataobjectFieldSpec(
    val description: String?,
    val fields: List<Field<*, *>>,
) : FieldSpec<Dataobject>() {

    companion object {
        fun parse(content: String, format: DataobjectFieldSpecFormat, description: String?) =
            when(format) {
                YAML -> parseYaml(content, description)
            }

        private fun parseYaml(content: String, description: String?): DataobjectFieldSpec {
            if(content.isBlank()) {
                return DataobjectFieldSpec(
                    description = description,
                    fields = emptyList(),
                )
            }

            // TODO #2 implement YAML parsing
            return DataobjectFieldSpec(
                description = description,
                fields = emptyList(),
            )
        }
    }

    private val fieldNames: Set<String> get() = fields.map { it.name }.toSet()

    override fun validate(): List<DomainError> {
        val noFieldsIssue = if (fields.isEmpty()) {
            DomainError(
                code = TypesystemDomainErrorCodes.OBJECT_DEFINITION_EMPTY_FIELDS,
                errorMessage = null,
            )
        } else {
            null
        }

        val duplicateFieldName = fieldNames.findDuplicates()
        val duplicateFieldNamesIssue = if (duplicateFieldName.isNotEmpty()) {
            DomainError(
                code = TypesystemDomainErrorCodes.OBJECT_DEFINITION_DUPLICATE_FIELD_NAMES,
                errorMessage = duplicateFieldName.toString(),
            )
        } else {
            null
        }

        return fields.flatMap { it.validateDefinition() }
            .plus(noFieldsIssue)
            .plus(duplicateFieldNamesIssue)
            .filterNotNull()
    }

    override fun validateValue(value: Dataobject): List<DomainError> {
        val valuesWithoutFieldDefintion = value.values.keys.minus(fieldNames).map {
            DomainError(
                code = TypesystemDomainErrorCodes.OBJECT_VALUE_WITHOUT_FIELD_DEFINITION,
                errorMessage = null,
            )
        }

        val fieldDefintionsWithoutValue = fieldNames.minus(value.values.keys).map {
            DomainError(
                code = TypesystemDomainErrorCodes.OBJECT_VALUE_FIELD_DEFINITION_WITHOUT_VALUE,
                errorMessage = null,
            )
        }

        val fieldValidationIssues = fieldNames.flatMap { this[it]!!.validateValue(value.values[it]) }

        return valuesWithoutFieldDefintion + fieldDefintionsWithoutValue + fieldValidationIssues
    }

    override fun convert(value: Any): Either<List<DomainError>, Dataobject> =
        Either.conditionally(value is Dataobject, {
            listOf(
                DomainError(
                    code = TypesystemDomainErrorCodes.VALUE_TYPE_MISMATCH,
                    errorMessage = null,
                )
            )
        }, {
            value as Dataobject
        })

    fun computeBreakingChanges(newVersion: DataobjectFieldSpec): List<DatatypeBreakingChange> {
        val addedMandatoryFields = newVersion.fieldNames.minus(fieldNames)
            .map { newVersion[it]!! }
            .filter { it.isMandatory }
            .map { DatatypeBreakingChange.MANDATORY_FIELD_ADDED }

        val removedFields = fieldNames.minus(newVersion.fieldNames)
            .map { DatatypeBreakingChange.FIELD_REMOVED }

        val remainingFieldBreakingChanges = fieldNames.intersect(fieldNames)
            .flatMap { this[it]!!.computeBreakingChanges(newVersion[it]!!) }

        return remainingFieldBreakingChanges + addedMandatoryFields + removedFields
    }

    operator fun get(fieldName: String): Field<*, *>? {
        return fields.firstOrNull { it.name == fieldName }
    }

    fun dumpYaml(): String {
        // TODO #2 implement dumping
        return """
            # ${description?.replace("\n", "\n#") ?: ""}
            # TODO
        """.trimIndent()
    }
}

enum class DataobjectFieldSpecFormat {
    YAML;
}

private fun Collection<*>.findDuplicates() =
    groupingBy { it }.eachCount().filter { it.value > 1 }
