package de.chrgroth.james

import de.chrgroth.james.app.jsonschema.jsonObjectSchemaFor

internal fun String.toStringProperty() = toPropertyInSchemaContent("string")
internal fun String.toBooleanProperty() = toPropertyInSchemaContent("boolean")
internal fun String.toIntegerProperty() = toPropertyInSchemaContent("integer")
internal fun String.toNumberProperty() = toPropertyInSchemaContent("number")
internal fun String.toArrayProperty() = toPropertyInSchemaContent("array")

internal fun String.toPropertyInSchemaContent(propertyType: String): String = """|"properties": {
    |  "testPropertyName": {
    |    "type": "$propertyType"${if (this.isNotBlank()) "," else ""}
    |    $this
    |  }
    |}""".trimMargin().toTestSchema()

internal fun String.toTestSchema() =
    jsonObjectSchemaFor("FooType", "A test schema", this)
