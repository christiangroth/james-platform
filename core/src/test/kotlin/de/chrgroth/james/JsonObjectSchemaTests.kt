package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import org.assertj.core.api.Assertions.assertThat
import org.everit.json.schema.StringSchema
import org.junit.jupiter.api.Test
import java.util.UUID

class JsonObjectSchemaGenerationTests {

    @Test
    fun `empty object json schema has base properties`() {
        assertThat(jsonObjectSchemaFor("Foo", "Foos are really great!", "")).isEqualTo(
            """|{
            |  "title": "Foo",
            |  "description": "Foos are really great!",
            |  "type": "object",
            |
            |}""".trimMargin()
        )
    }

    @Test
    fun `json schema contains additional content`() {
        assertThat(jsonObjectSchemaFor("Foo", "Foos are really great!", """
            |  "properties": {
            |    "productId": {
            |      "description": "The unique identifier for a product",
            |      "type": "integer"
            |    },
            |    "productName": {
            |      "description": "Name of the product",
            |      "type": "string"
            |    },
            |    "productDescription": {
            |      "description": "Description of the product (optional)",
            |      "type": "string"
            |    }
            |  },
            |  "required": [ "productId", "productName" ]""".trimMargin())
        ).isEqualTo(
            """|{
            |  "title": "Foo",
            |  "description": "Foos are really great!",
            |  "type": "object",
            |  "properties": {
            |    "productId": {
            |      "description": "The unique identifier for a product",
            |      "type": "integer"
            |    },
            |    "productName": {
            |      "description": "Name of the product",
            |      "type": "string"
            |    },
            |    "productDescription": {
            |      "description": "Description of the product (optional)",
            |      "type": "string"
            |    }
            |  },
            |  "required": [ "productId", "productName" ]
            |}""".trimMargin()
        )
    }
}

// TODO #17 assert details
class JsonObjectSchemaParsingTests {

    @Test
    fun `invalid json schema syntax fails`() {
        val result = createSchema(",;,;")
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code)
            .isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_INVALID)
    }

    @Test
    fun `not object schema (cannot happen when using datatypes api everytime)`() {
        val result = """{ "type": "string" }""".parseJsonSchema()
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code)
            .isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_IS_NOT_OBJECT_SCHEMA)
        assertThat(result.details).isEqualTo(StringSchema::class.java)
    }

    @Test
    fun `unknown properties are rejected`() {
        val result = createSchema("""
            "foo": "bar"
        """.trimIndent())
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code)
            .isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES)
        assertThat(result.details).isEqualTo(mapOf("foo" to "bar"))
    }

    private fun createSchema(schemaContent: String) =
        jsonObjectSchemaFor("TestType", "Some really nice description!", schemaContent).parseJsonSchema()
}
