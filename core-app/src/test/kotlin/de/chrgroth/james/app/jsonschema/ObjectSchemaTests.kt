package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toPropertyInSchemaContent
import de.chrgroth.james.toTestSchema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObjectSchemaTests : AnnotationsBaseTests() {

    override val toPropertyConverter: (String) -> String
        get() = { it.toTestSchema() }

    @Test
    fun `title allowed`() {
        val testSchema = "".toTestSchema()
        assertThat(testSchema).contains(""""title": """")
        testSchema.parseToObjectSchema().expectSuccess()
    }

    @Test
    fun `default not allowed`() =
        """ $prefixForAnnotationTests "default": { "foo": "bar" } """.toTestSchema().parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_DEFAULT_ONLY_SUPPORTED_BOOLEAN_NUMBER_STRING,
                details = null
            )
        )

    @Test
    fun `min properties in object definition`() {
        val schemaContent = """ "minProperties": 7 """.toTestSchema()
        schemaContent.parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_MIN_PROPERTIES_NOT_SUPPORTED,
                details = null,
            )
        )
    }

    @Test
    fun `max properties in object definition`() {
        val schemaContent = """ "maxProperties": 7 """.toTestSchema()
        schemaContent.parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_MAX_PROPERTIES_NOT_SUPPORTED,
                details = null,
            )
        )
    }

    @Test
    fun `additional properties in object definition`() {
        val schemaContent = "".toTestSchema().replace(""""additionalProperties": false""", """"additionalProperties": true""")
        schemaContent.parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ADDITIONAL_PROPERTIES_NOT_SUPPORTED,
                details = null,
            )
        )
    }

    @Test
    fun `pattern properties not supported`() {
        val schemaContent = """ "patternProperties": { "^S_": { "type": "string" }, "^I_": { "type": "integer" } } """.toTestSchema()
        schemaContent.parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_PATTERN_PROPERTIES_NOT_SUPPORTED,
                details = null,
            )
        )
    }

    @Test
    fun `property names schema not supported`() {
        val schemaContent = """ "propertyNames": { "pattern": "^[A-Za-z_][A-Za-z0-9_]*${'$'}" } """.toTestSchema()
        schemaContent.parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_PROPERTY_NAME_SCHEMA_NOT_SUPPORTED,
                details = null,
            )
        )
    }

    @Test
    fun `property dependencies not supported`() {
        val schemaContent =
            """ "properties": { "name": { "type": "string" }, "credit_card": { "type": "number" }, "billing_address": { "type": "string" } }, 
                 "dependencies": { "credit_card": ["billing_address"], "billing_address": ["credit_card"] } """.trimMargin().toTestSchema()
        schemaContent.parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_PROPERTY_DEPENDENCIES_NOT_SUPPORTED,
                details = null,
            )
        )
    }

    @Test
    fun `schema dependencies not supported`() {
        val schemaContent =
            """ "properties": { "name": { "type": "string" }, "credit_card": { "type": "number" } }, 
            "dependencies": { "credit_card": { "properties": { "billing_address": { "type": "string" } }, 
            "required": ["billing_address"] } }""".trimMargin().toTestSchema()
        schemaContent.parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_SCHEMA_DEPENDENCIES_NOT_SUPPORTED,
                details = null,
            )
        )
    }

    @Test
    fun `invalid properties type in object definition`() {
        val schemaContent = "".toPropertyInSchemaContent("object")
        schemaContent.parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_PROPERTIES_INVALID_TYPE,
                details = "[testPropertyName=ObjectSchema]",
            )
        )
    }

    @Test
    fun `required properties not existent lead to error`() {
        val schemaContent =
            """ "properties": { "name": { "type": "string" }, "credit_card": { "type": "number" } }, 
            "required": [ "name", "creditCard", "billingAddress" ] """.trimMargin().toTestSchema()
        schemaContent.parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_REQUIRED_PROPERTIES_DO_NOT_EXIST,
                details = "[billingAddress, creditCard]",
            )
        )
    }

    @Test
    fun `unprocessed properties in object definition`() {
        val schemaContent = """ "foo": "bar" """.toTestSchema()
        schemaContent.parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                details = "{foo=bar}",
            )
        )
    }

    @Test
    fun `conditionals not supported`() {
        """
            {
              "type": "object",
              "title": "Foo",
              "properties": {
                "street_address": {
                  "type": "string"
                },
                "country": {
                  "type": "string",
                  "default": "United States of America",
                  "enum": ["United States of America", "Canada"]
                }
              },
              "if": {
                "properties": { "country": { "const": "United States of America" } }
              },
              "then": {
                "properties": { "postal_code": { "pattern": "[0-9]{5}(-[0-9]{4})?" } }
              },
              "else": {
                "properties": { "postal_code": { "pattern": "[A-Z][0-9][A-Z] [0-9][A-Z][0-9]" } }
              }
            }
        """.trimIndent().parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_IS_NOT_OBJECT_SCHEMA,
                details = "CombinedSchema",
            )
        )
    }

    @Test
    fun `structuring not supported`() {
        """
            {
              "title": "Foo",
              "type": "object",
              "additionalProperties": false,
              "definitions": {
                "addressTuple": {
                  "type": "array",
                  "items": [ { "type": "string" }, { "type": "string" }, { "type": "string" } ]
                }
              },
              "properties": {
                "billing_address": { "${"$"}ref": "#/definitions/addressTuple" },
                "shipping_address": { "${"$"}ref": "#/definitions/addressTuple" }
              }
            }
        """.trimIndent().parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_PROPERTIES_INVALID_TYPE,
                details = "[billing_address=ReferenceSchema, shipping_address=ReferenceSchema]",
            ),
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                details = "{definitions={addressTuple={type=array, items=[{type=string}, {type=string}, {type=string}]}}}",
            ),
        )
    }
}
