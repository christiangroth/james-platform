package de.chrgroth.james.app

// TODO #2 create from schema content
data class TypesystemDatatype private constructor(
    private val name: String,
    private val version: Long,
    private val fields: Map<String, TypesystemField>,
    private val description: String?,
)

sealed class TypesystemField

// TODO #2 specify field types
// TODO #2 add enum capabilities
// TODO #2 compare with old typesystem (what about date, datetime, ...)

data class TypesystemBooleanField(val placeholder: Unit) : TypesystemField()
data class TypesystemObjectField(val placeholder: Unit) : TypesystemField()
data class TypesystemNumberField(val placeholder: Unit) : TypesystemField()
data class TypesystemStringField(val placeholder: Unit) : TypesystemField()
data class TypesystemArrayListField(val placeholder: Unit) : TypesystemField()
data class TypesystemArrayTupleField(val placeholder: Unit) : TypesystemField()
