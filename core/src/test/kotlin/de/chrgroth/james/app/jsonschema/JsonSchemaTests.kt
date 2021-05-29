package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectError
import de.chrgroth.james.toTestSchema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class JsonSchemaGenerationTests {

    @Test
    fun `schema id for SNAPSHOT version`() {
        val appId = UUID.fromString("c5ded581-413d-4b3c-a712-0b178452ae66")
        val version = null
        val datatypeName = "Foo"
        assertThat(jsonSchemaIdFor(appId, version, datatypeName))
            .isEqualTo("/apps/c5ded581-413d-4b3c-a712-0b178452ae66/versions/SNAPSHOT/datatypes/Foo.schema.json")
    }

    @Test
    fun `schema id for specific version`() {
        val appId = UUID.fromString("191c7c65-1cde-4220-b695-9d814bf7b4e8")
        val version = 724.toString()
        val datatypeName = "Bar"
        assertThat(jsonSchemaIdFor(appId, version, datatypeName))
            .isEqualTo("/apps/191c7c65-1cde-4220-b695-9d814bf7b4e8/versions/724/datatypes/Bar.schema.json")
    }

    @Test
    fun `empty object json schema has base properties`() {
        assertThat(jsonObjectSchemaFor("Foo", "Foos are really great!", "")).isEqualTo(
            """|{
            |  "title": "Foo",
            |  "description": "Foos are really great!",
            |  "type": "object",
            |  "additionalProperties": false,
            |
            |}""".trimMargin()
        )
    }

    @Test
    fun `json schema contains additional content`() {
        assertThat(
            jsonObjectSchemaFor("Foo", "Foos are really great!", """
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
            |  "additionalProperties": false,
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

class JsonSchemaParsingTests {

    @Test
    fun `invalid json schema syntax fails`() {
        val schemaContent = ",;,;".toTestSchema()
        schemaContent.validateJsonSchema().expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_INVALID,
            details = "Missing value at 111 [character 0 line 6]"
        )
    }

    @Test
    fun `not object schema (cannot happen when using datatypes api everytime)`() {
        """{ "type": "string" }""".parseJsonSchema().expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_IS_NOT_OBJECT_SCHEMA,
            details = "StringSchema"
        )
    }
}
