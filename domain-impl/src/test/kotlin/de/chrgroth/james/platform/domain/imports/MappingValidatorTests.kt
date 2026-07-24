package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.MappingIssue
import de.chrgroth.james.platform.domain.model.imports.MappingType
import de.chrgroth.james.platform.domain.model.imports.NumericRange
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MappingValidatorTests {

  private val propertyId = PropertyId("prop-1")

  @Test
  fun `mandatory property without any mapping is reported`() {
    val entityDefinition = entityDefinition(stringProperty(nullable = false))
    val mapping = mapping()

    val result = MappingValidator.validate(mapping, entityDefinition, emptyList())

    assertThat(result.blockingIssues).containsExactly(MappingIssue.MissingMandatoryField(propertyId))
    assertThat(result.isReady).isFalse()
  }

  @Test
  fun `mandatory property with a static fallback value is satisfied without a source mapping`() {
    val entityDefinition = entityDefinition(stringProperty(nullable = false))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, fallbackValue = "n/a"))

    val result = MappingValidator.validate(mapping, entityDefinition, emptyList())

    assertThat(result.isReady).isTrue()
  }

  @Test
  fun `mandatory property mapped to an optional source field without a fallback is reported`() {
    val entityDefinition = entityDefinition(stringProperty(nullable = false))
    val schema = listOf(SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 1), mandatory = false))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "name"))

    val result = MappingValidator.validate(mapping, entityDefinition, schema)

    assertThat(result.blockingIssues).containsExactly(MappingIssue.MissingMandatoryField(propertyId))
  }

  @Test
  fun `directly compatible types produce no issues`() {
    val entityDefinition = entityDefinition(stringProperty(nullable = true))
    val schema = listOf(SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 3), mandatory = true))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "name"))

    val result = MappingValidator.validate(mapping, entityDefinition, schema)

    assertThat(result.issues).isEmpty()
  }

  @Test
  fun `incompatible source type without a conversion is reported`() {
    val entityDefinition = entityDefinition(Property(id = propertyId, name = "Age", type = PropertyType.LONG, nullable = true))
    val schema = listOf(SchemaProperty("age", mapOf(SchemaPropertyType.STRING to 1), mandatory = true))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "age"))

    val result = MappingValidator.validate(mapping, entityDefinition, schema)

    assertThat(result.blockingIssues).containsExactly(MappingIssue.IncompatibleType(propertyId, SchemaPropertyType.STRING, PropertyType.LONG))
  }

  @Test
  fun `configured conversion resolves an otherwise incompatible type mismatch`() {
    val entityDefinition = entityDefinition(Property(id = propertyId, name = "Age", type = PropertyType.LONG, nullable = true))
    val schema = listOf(SchemaProperty("age", mapOf(SchemaPropertyType.STRING to 1), mandatory = true))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "age", conversion = FieldMappingConversion.STRING_TO_LONG))

    val result = MappingValidator.validate(mapping, entityDefinition, schema)

    assertThat(result.issues).isEmpty()
  }

  @Test
  fun `numeric value below the target minimum is reported`() {
    val entityDefinition = entityDefinition(
      Property(id = propertyId, name = "Age", type = PropertyType.LONG, nullable = true, constraints = setOf(PropertyConstraint.MinLong(18))),
    )
    val schema = listOf(SchemaProperty("age", mapOf(SchemaPropertyType.LONG to 2), mandatory = true, numericRange = NumericRange(min = 5.0, max = 40.0)))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "age"))

    val result = MappingValidator.validate(mapping, entityDefinition, schema)

    assertThat(result.blockingIssues).containsExactly(MappingIssue.NumericRangeViolation(propertyId, 18.0, null, 5.0, 40.0))
  }

  @Test
  fun `numeric value within the target range produces no issue`() {
    val entityDefinition = entityDefinition(
      Property(id = propertyId, name = "Age", type = PropertyType.LONG, nullable = true, constraints = setOf(PropertyConstraint.MinLong(0), PropertyConstraint.MaxLong(100))),
    )
    val schema = listOf(SchemaProperty("age", mapOf(SchemaPropertyType.LONG to 2), mandatory = true, numericRange = NumericRange(min = 5.0, max = 40.0)))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "age"))

    val result = MappingValidator.validate(mapping, entityDefinition, schema)

    assertThat(result.issues).isEmpty()
  }

  @Test
  fun `string length above the target maximum is reported`() {
    val entityDefinition = entityDefinition(
      Property(id = propertyId, name = "Code", type = PropertyType.STRING, nullable = true, constraints = setOf(PropertyConstraint.MaxLength(5))),
    )
    val schema = listOf(
      SchemaProperty("code", mapOf(SchemaPropertyType.STRING to 2), mandatory = true, stringLengthCounts = mapOf(3 to 1, 10 to 1)),
    )
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "code"))

    val result = MappingValidator.validate(mapping, entityDefinition, schema)

    assertThat(result.blockingIssues).containsExactly(MappingIssue.StringLengthViolation(propertyId, null, 5, 3, 10))
  }

  @Test
  fun `pattern constraint is reported as not statically validated but does not block READY`() {
    val entityDefinition = entityDefinition(
      Property(id = propertyId, name = "Code", type = PropertyType.STRING, nullable = true, constraints = setOf(PropertyConstraint.Pattern("^[A-Z]+$"))),
    )
    val schema = listOf(SchemaProperty("code", mapOf(SchemaPropertyType.STRING to 1), mandatory = true))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "code"))

    val result = MappingValidator.validate(mapping, entityDefinition, schema)

    assertThat(result.notStaticallyValidated).containsExactly(MappingIssue.NotStaticallyValidated(propertyId, "^[A-Z]+$"))
    assertThat(result.isReady).isTrue()
  }

  private fun stringProperty(nullable: Boolean) = Property(id = propertyId, name = "Name", type = PropertyType.STRING, nullable = nullable)

  private fun entityDefinition(vararg properties: Property) = EntityDefinition(
    id = EntityDefinitionId("entity-1"),
    name = "Contact",
    properties = properties.toList(),
  )

  private fun mapping(vararg fieldMappings: FieldMapping) = Mapping(
    name = "Contact",
    type = MappingType.FIND,
    targetEntityDefinitionId = EntityDefinitionId("entity-1"),
    fieldMappings = fieldMappings.toList(),
  )
}
