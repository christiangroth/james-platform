package de.chrgroth.james

internal fun String.toPropertyInSchemaContent(): String {
    val schemaContent = """
  "properties": {
    "foo": $this
  }
""".trimIndent()
    return jsonObjectSchemaFor("FooType", "A test schema", schemaContent)
}
