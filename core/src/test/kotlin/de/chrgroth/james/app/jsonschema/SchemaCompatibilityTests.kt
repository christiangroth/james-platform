package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toIntegerProperty
import de.chrgroth.james.toStringProperty
import org.everit.json.schema.ObjectSchema
import org.junit.jupiter.api.Test

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

        expectErrors(current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ENUM_PROPERTY_POSSIBLE_VALUE_REMOVED,
                details = "[bar]",
            )
        )
    }

    @Test
    fun `removing enum value is compatible`() {
        val current = """ "enum": ["foo", "bar"] """.toStringProperty()
        val next = """ "enum": ["foo"] """.toStringProperty()

        expectErrors(current, next,
            Error(
                code = SchemaCompatibilityErrorCodes.ENUM_PROPERTY_POSSIBLE_VALUE_REMOVED,
                details = "[bar]",
            )
        )
    }

    @Test
    fun `transitive incompatibility is delegated`() {
        val current = "".toStringProperty()
        val next = """ "minLength": 7 """.toStringProperty()

        expectErrors(current, next,
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

        expectErrors(current, next,
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

        expectErrors(current, next,
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

        expectErrors(current, next,
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

        expectErrors(current, next,
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
}

class StringSchemaCompatibilityTests {

    @Test
    fun `introducing min is breaking`() {
        val current = "".toStringProperty()
        val next = """ "minLength": 3 """.toStringProperty()

        expectErrors(current, next,
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

        expectErrors(current, next,
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

        expectErrors(current, next,
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

        expectErrors(current, next,
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

        expectErrors(current, next,
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

        expectErrors(current, next,
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

        expectErrors(current, next,
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

        expectErrors(current, next,
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

private fun expectErrors(currentSchemaContent: String, nextSchemaContent: String, vararg expectedErrors: Error<Unit>) {
    val (current, next) = expectParsedSchemas(currentSchemaContent, nextSchemaContent)
    current.computeCompatibility(next).expectErrors(*expectedErrors)
}

private fun expectParsedSchemas(currentSchemaContent: String, nextSchemaContent: String): Pair<ObjectSchema, ObjectSchema> {
    val current = currentSchemaContent.parseJsonSchema().transform { it.validateTopLevelSchema() }.expectSuccess()
    val next = nextSchemaContent.parseJsonSchema().transform { it.validateTopLevelSchema() }.expectSuccess()

    return current to next
}
