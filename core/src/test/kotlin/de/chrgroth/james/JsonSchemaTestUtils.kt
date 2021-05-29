package de.chrgroth.james

internal fun String.toPropertyInSchemaContent(): String = """|"properties": {
    |  "testPropertyName": $this
    |}""".trimMargin().toTestSchema()

internal fun String.toTestSchema() =
    jsonObjectSchemaFor("FooType", "A test schema", this)
