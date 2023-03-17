package de.chrgroth.james.app.jsonschema

import arrow.core.andThen
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toArrayProperty
import de.chrgroth.james.toIntegerProperty
import de.chrgroth.james.toNumberProperty
import de.chrgroth.james.toStringProperty
import de.chrgroth.james.toTestSchema
import org.everit.json.schema.ObjectSchema
import org.junit.jupiter.api.Test
import de.chrgroth.james.Error

class ObjectSchemaCompatibilityTests {

    @Test
    fun `adding new property is compatible`() {
        val current =
            """ "properties": { "name": { "type": "string" } }, 
            "required": [ "name" ] """.trimMargin().toTestSchema()
        val next =
            """ "properties": { "name": { "type": "string" }, "credit_card": { "type": "number" } }, 
            "required": [ "name" ] """.trimMargin().toTestSchema()

        expectCompatible(current, next)
    }

    @Test
    fun `adding new required property is breaking`() {
        val current =
            """ "properties": { "name": { "type": "string" } }, 
            "required": [ "name" ] """.trimMargin().toTestSchema()
        val next =
            """ "properties": { "name": { "type": "string" }, "credit_card": { "type": "number" } }, 
            "required": [ "name", "credit_card" ] """.trimMargin().toTestSchema()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.NEW_REQUIRED_PROPERTY_WITHOUT_DEFAULT,
                details = "[credit_card]"
            )
        )
    }

    @Test
    fun `adding new required property with default is compatible`() {
        val current =
            """ "properties": { "name": { "type": "string" } }, 
            "required": [ "name" ] """.trimMargin().toTestSchema()
        val next =
            """ "properties": { "name": { "type": "string" }, "credit_card": { "type": "number", "default": 5 } }, 
            "required": [ "name", "credit_card" ] """.trimMargin().toTestSchema()

        expectCompatible(current, next)
    }

    @Test
    fun `property made required is breaking`() {
        val current =
            """ "properties": { "name": { "type": "string" } }, 
            "required": [ ] """.trimMargin().toTestSchema()
        val next =
            """ "properties": { "name": { "type": "string" } }, 
            "required": [ "name" ] """.trimMargin().toTestSchema()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.PROPERTY_MADE_REQUIRED_WITHOUT_DEFAULT,
                details = "[name]"
            )
        )
    }

    @Test
    fun `property with default made required is compatible`() {
        val current =
            """ "properties": { "name": { "type": "string", "default": "hello world" } }, 
            "required": [ ] """.trimMargin().toTestSchema()
        val next =
            """ "properties": { "name": { "type": "string", "default": "hello world" } }, 
            "required": [ "name" ] """.trimMargin().toTestSchema()

        expectCompatible(current, next)
    }

    @Test
    fun `property made required and add default is compatible`() {
        val current =
            """ "properties": { "name": { "type": "string" } }, 
            "required": [ ] """.trimMargin().toTestSchema()
        val next =
            """ "properties": { "name": { "type": "string", "default": "hello world" } }, 
            "required": [ "name" ] """.trimMargin().toTestSchema()

        expectCompatible(current, next)
    }

    @Test
    fun `property with default made required and remove default is breaking`() {
        val current =
            """ "properties": { "name": { "type": "string", "default": "hello world" } }, 
            "required": [ ] """.trimMargin().toTestSchema()
        val next =
            """ "properties": { "name": { "type": "string" } }, 
            "required": [ "name" ] """.trimMargin().toTestSchema()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.PROPERTY_MADE_REQUIRED_WITHOUT_DEFAULT,
                details = "[name]"
            )
        )
    }

    @Test
    fun `removing property is breaking`() {
        val current =
            """ "properties": { "name": { "type": "string" }, "credit_card": { "type": "number" } }, 
            "required": [ "name" ] """.trimMargin().toTestSchema()
        val next =
            """ "properties": { "name": { "type": "string" } }, 
            "required": [ "name" ] """.trimMargin().toTestSchema()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.PROPERTY_REMOVED,
                details = "[credit_card]"
            )
        )
    }
}

class ArraySchemaCompatibilityTests {

    @Test
    fun `change from list to tuple is breaking`() {
        val current = """ "items": { "type": "number" } """.toArrayProperty()
        val next = """ "items": [ { "type": "number" }, { "type": "string" } ] """.toArrayProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_MODE_CHANGED,
                details = "LIST -> TUPLE",
            )
        )
    }

    @Test
    fun `introducing list min items is breaking`() {
        val current = """ "items": { "type": "number" } """.toArrayProperty()
        val next = """ "items": { "type": "number" }, "minItems": 2 """.toArrayProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_LIST_MIN_ITEMS_INCREASED,
                details = "0 -> 2",
            )
        )
    }

    @Test
    fun `increasing list min items is breaking`() {
        val current = """ "items": { "type": "number" }, "minItems": 1 """.toArrayProperty()
        val next = """ "items": { "type": "number" }, "minItems": 2 """.toArrayProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_LIST_MIN_ITEMS_INCREASED,
                details = "1 -> 2",
            )
        )
    }

    @Test
    fun `decreasing list min items is compatible`() {
        val current = """ "items": { "type": "number" }, "minItems": 3 """.toArrayProperty()
        val next = """ "items": { "type": "number" }, "minItems": 2 """.toArrayProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `removing list min item is compatible`() {
        val current = """ "items": { "type": "number" }, "minItems": 2 """.toArrayProperty()
        val next = """ "items": { "type": "number" } """.toArrayProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `introducing list max items is breaking`() {
        val current = """ "items": { "type": "number" } """.toArrayProperty()
        val next = """ "items": { "type": "number" }, "maxItems": 2 """.toArrayProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_LIST_MAX_ITEMS_DECREASED,
                details = "${Int.MAX_VALUE} -> 2",
            )
        )
    }

    @Test
    fun `decreasing list max items is breaking`() {
        val current = """ "items": { "type": "number" }, "maxItems": 3 """.toArrayProperty()
        val next = """ "items": { "type": "number" }, "maxItems": 2 """.toArrayProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_LIST_MAX_ITEMS_DECREASED,
                details = "3 -> 2",
            )
        )
    }

    @Test
    fun `increasing list max items is compatible`() {
        val current = """ "items": { "type": "number" }, "maxItems": 1 """.toArrayProperty()
        val next = """ "items": { "type": "number" }, "maxItems": 2 """.toArrayProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `removing list max item is compatible`() {
        val current = """ "items": { "type": "number" }, "maxItems": 2 """.toArrayProperty()
        val next = """ "items": { "type": "number" } """.toArrayProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `changing list item schema is breaking`() {
        val current = """ "items": { "type": "number" } """.toArrayProperty()
        val next = """ "items": { "type": "string" } """.toArrayProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_LIST_ITEMS_SCHEMA_CHANGED,
                details = "NumberSchema -> StringSchema",
            )
        )
    }

    @Test
    fun `change from tuple to list is breaking`() {
        val current = """ "items": [ { "type": "number" }, { "type": "string" } ] """.toArrayProperty()
        val next = """ "items": { "type": "number" } """.toArrayProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_MODE_CHANGED,
                details = "TUPLE -> LIST",
            )
        )
    }

    @Test
    fun `adding tuple entry is breaking`() {
        val current = """ "items": [ { "type": "number" } ] """.toArrayProperty()
        val next = """ "items": [ { "type": "number" }, { "type": "number" } ] """.toArrayProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_TUPLE_ITEMS_SCHEMA_CHANGED,
                details = "[NumberSchema] -> [NumberSchema, NumberSchema]",
            )
        )
    }

    @Test
    fun `changing tuple entry schema is breaking`() {
        val current = """ "items": [ { "type": "number" }, { "type": "string" } ] """.toArrayProperty()
        val next = """ "items": [ { "type": "number" }, { "type": "number" } ] """.toArrayProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_TUPLE_ITEMS_SCHEMA_CHANGED,
                details = "[NumberSchema, StringSchema] -> [NumberSchema, NumberSchema]",
            )
        )
    }

    @Test
    fun `removing tuple entry is breaking`() {
        val current = """ "items": [ { "type": "number" }, { "type": "string" } ] """.toArrayProperty()
        val next = """ "items": [ { "type": "number" } ] """.toArrayProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ARRAY_PROPERTY_TUPLE_ITEMS_SCHEMA_CHANGED,
                details = "[NumberSchema, StringSchema] -> [NumberSchema]",
            )
        )
    }
}

class CombinedEnumSchemaCompatibilityTests {

    @Test
    fun `introducing enum value is compatible`() {
        val current = """ "enum": ["foo"] """.toStringProperty()
        val next = """ "enum": ["foo", "bar"] """.toStringProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `changing enum value is compatible`() {
        val current = """ "enum": ["foo", "bar"] """.toStringProperty()
        val next = """ "enum": ["foo", "baz"] """.toStringProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ENUM_PROPERTY_POSSIBLE_VALUE_REMOVED,
                details = "[bar]",
            )
        )
    }

    @Test
    fun `removing enum value is breaking`() {
        val current = """ "enum": ["foo", "bar"] """.toStringProperty()
        val next = """ "enum": ["foo"] """.toStringProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ENUM_PROPERTY_POSSIBLE_VALUE_REMOVED,
                details = "[bar]",
            )
        )
    }

    @Test
    fun `transitive number incompatibility is delegated`() {
        val current = """ "enum": [1, 2, 3] """.toNumberProperty()
        val next = """ "enum": [1, 2, 3, 7], "minimum": 7 """.toNumberProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MIN_INCREASED,
                details = "${Int.MIN_VALUE} -> 7",
            )
        )
    }

    @Test
    fun `transitive string incompatibility is delegated`() {
        val current = """ "enum": ["something", "or something other"] """.toStringProperty()
        val next = """ "enum": ["something", "or something other"], "minLength": 7 """.toStringProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_MIN_LENGTH_INCREASED,
                details = "0 -> 7",
            )
        )
    }
}

class NumberSchemaCompatibilityTests {

    @Test
    fun `introducing min is breaking`() {
        val current = "".toIntegerProperty()
        val next = """ "minimum": 3 """.toIntegerProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MIN_INCREASED,
                details = "${Int.MIN_VALUE} -> 3",
            )
        )
    }

    @Test
    fun `increasing min is breaking`() {
        val current = """ "minimum": 2 """.toIntegerProperty()
        val next = """ "minimum": 3 """.toIntegerProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MIN_INCREASED,
                details = "2 -> 3",
            )
        )
    }

    @Test
    fun `decreasing min is compatible`() {
        val current = """ "minimum": 2 """.toIntegerProperty()
        val next = """ "minimum": 1 """.toIntegerProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `removing min is compatible`() {
        val current = """ "minimum": 3 """.toIntegerProperty()
        val next = "".toIntegerProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `introducing max is breaking`() {
        val current = "".toIntegerProperty()
        val next = """ "maximum": 2 """.toIntegerProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MAX_DECREASED,
                details = "${Int.MAX_VALUE} -> 2",
            )
        )
    }

    @Test
    fun `decreasing max is breaking`() {
        val current = """ "maximum": 3 """.toIntegerProperty()
        val next = """ "maximum": 2 """.toIntegerProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MAX_DECREASED,
                details = "3 -> 2",
            )
        )
    }

    @Test
    fun `increasing max is compatible`() {
        val current = """ "maximum": 2 """.toIntegerProperty()
        val next = """ "maximum": 3 """.toIntegerProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `removing max is compatible`() {
        val current = """ "maximum": 3 """.toIntegerProperty()
        val next = "".toIntegerProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `introducing multipleOf is breaking`() {
        val current = "".toIntegerProperty()
        val next = """ "multipleOf": 2 """.toIntegerProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MULTIPLE_OF_MORE_STRICT,
                details = "null -> 2",
            )
        )
    }

    @Test
    fun `changing multipleOf is breaking`() {
        val current = """ "multipleOf": 2 """.toIntegerProperty()
        val next = """ "multipleOf": 7 """.toIntegerProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MULTIPLE_OF_MORE_STRICT,
                details = "2 -> 7",
            )
        )
    }

    @Test
    fun `changing multipleOf (to more strict subset) is breaking`() {
        val current = """ "multipleOf": 2 """.toIntegerProperty()
        val next = """ "multipleOf": 4 """.toIntegerProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MULTIPLE_OF_MORE_STRICT,
                details = "2 -> 4",
            )
        )
    }

    @Test
    fun `changing multipleOf to more relaxed (not allowing all old values) is compatible`() {
        val current = """ "multipleOf": 7 """.toIntegerProperty()
        val next = """ "multipleOf": 2 """.toIntegerProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.NUMBER_PROPERTY_MULTIPLE_OF_MORE_STRICT,
                details = "7 -> 2",
            )
        )
    }

    @Test
    fun `changing multipleOf to more relaxed (allowing all old values) is compatible`() {
        val current = """ "multipleOf": 4 """.toIntegerProperty()
        val next = """ "multipleOf": 2 """.toIntegerProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `removing multipleOf is compatible`() {
        val current = """ "multipleOf": 2 """.toIntegerProperty()
        val next = "".toIntegerProperty()

        expectCompatible(current, next)
    }
}

class StringSchemaCompatibilityTests {

    @Test
    fun `introducing min is breaking`() {
        val current = "".toStringProperty()
        val next = """ "minLength": 3 """.toStringProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_MIN_LENGTH_INCREASED,
                details = "0 -> 3",
            )
        )
    }

    @Test
    fun `increasing min is breaking`() {
        val current = """ "minLength": 2 """.toStringProperty()
        val next = """ "minLength": 3 """.toStringProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_MIN_LENGTH_INCREASED,
                details = "2 -> 3",
            )
        )
    }

    @Test
    fun `decreasing min is compatible`() {
        val current = """ "minLength": 2 """.toStringProperty()
        val next = """ "minLength": 1 """.toStringProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `removing min is compatible`() {
        val current = """ "minLength": 3 """.toStringProperty()
        val next = "".toStringProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `introducing max is breaking`() {
        val current = "".toStringProperty()
        val next = """ "maxLength": 2 """.toStringProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_MAX_LENGTH_DECREASED,
                details = "${Int.MAX_VALUE} -> 2",
            )
        )
    }

    @Test
    fun `decreasing max is breaking`() {
        val current = """ "maxLength": 3 """.toStringProperty()
        val next = """ "maxLength": 2 """.toStringProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_MAX_LENGTH_DECREASED,
                details = "3 -> 2",
            )
        )
    }

    @Test
    fun `increasing max is compatible`() {
        val current = """ "maxLength": 2 """.toStringProperty()
        val next = """ "maxLength": 3 """.toStringProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `removing max is compatible`() {
        val current = """ "maxLength": 3 """.toStringProperty()
        val next = "".toStringProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `introducing pattern is breaking`() {
        val current = "".toStringProperty()
        val next = """ "pattern": "some-pattern" """.toStringProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_PATTERN_CHANGED,
                details = "null -> some-pattern",
            )
        )
    }

    @Test
    fun `changing pattern is breaking`() {
        val current = """ "pattern": "some-pattern" """.toStringProperty()
        val next = """ "pattern": "some-other-pattern" """.toStringProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_PATTERN_CHANGED,
                details = "some-pattern -> some-other-pattern",
            )
        )
    }

    @Test
    fun `removing pattern is compatible`() {
        val current = """ "pattern": "some-pattern" """.toStringProperty()
        val next = "".toStringProperty()

        expectCompatible(current, next)
    }

    @Test
    fun `introducing format is breaking`() {
        val current = "".toStringProperty()
        val next = """ "format": "email" """.toStringProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_FORMAT_CHANGED,
                details = "unnamed-format -> email",
            )
        )
    }

    @Test
    fun `changing format is breaking`() {
        val current = """ "format": "date" """.toStringProperty()
        val next = """ "format": "date-time" """.toStringProperty()

        expectErrors(
            current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.STRING_PROPERTY_FORMAT_CHANGED,
                details = "date -> date-time",
            )
        )
    }

    @Test
    fun `removing format is compatible`() {
        val current = """ "format": "regex" """.toStringProperty()
        val next = "".toStringProperty()

        expectCompatible(current, next)
    }
}

private fun expectCompatible(currentSchemaContent: String, nextSchemaContent: String) {
    val (current, next) = expectParsedSchemas(currentSchemaContent, nextSchemaContent)
    current.computeCompatibility(next).expectSuccess()
}

private fun expectErrors(currentSchemaContent: String, nextSchemaContent: String, vararg expectedErrors: Error) {
    val (current, next) = expectParsedSchemas(currentSchemaContent, nextSchemaContent)
    current.computeCompatibility(next).expectErrors(*expectedErrors)
}

private fun expectParsedSchemas(currentSchemaContent: String, nextSchemaContent: String): Pair<ObjectSchema, ObjectSchema> {
    val current = currentSchemaContent.parseJsonSchema().andThen { it.validateTopLevelSchema() }.expectSuccess()
    val next = nextSchemaContent.parseJsonSchema().andThen { it.validateTopLevelSchema() }.expectSuccess()

    return current to next
}
