package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.app.PropertyConstraintService
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
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
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookup
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookupCriterion
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MappingValidatorTests {

  private val propertyConstraint = PropertyConstraintService()
  private val propertyId = PropertyId("prop-1")
  private val referencedEntityId = EntityDefinitionId("entity-2")

  private fun validate(
    mapping: Mapping,
    entityDefinition: EntityDefinition,
    schema: List<SchemaProperty>,
    entityDefinitions: List<EntityDefinition> = listOf(entityDefinition),
  ) = MappingValidator.validate(mapping, entityDefinition, schema, entityDefinitions, propertyConstraint)

  @Test
  fun `mandatory property without any mapping is reported`() {
    val entityDefinition = entityDefinition(stringProperty(nullable = false))
    val mapping = mapping()

    val result = validate(mapping, entityDefinition, emptyList())

    assertThat(result.blockingIssues).containsExactly(MappingIssue.MissingMandatoryField(propertyId))
    assertThat(result.isReady).isFalse()
  }

  @Test
  fun `mandatory property with a static fallback value is satisfied without a source mapping`() {
    val entityDefinition = entityDefinition(stringProperty(nullable = false))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, fallbackValue = "n/a"))

    val result = validate(mapping, entityDefinition, emptyList())

    assertThat(result.isReady).isTrue()
  }

  @Test
  fun `mandatory property mapped to an optional source field without a fallback is reported`() {
    val entityDefinition = entityDefinition(stringProperty(nullable = false))
    val schema = listOf(SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 1), mandatory = false))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "name"))

    val result = validate(mapping, entityDefinition, schema)

    assertThat(result.blockingIssues).containsExactly(MappingIssue.MissingMandatoryField(propertyId))
  }

  @Test
  fun `directly compatible types produce no issues`() {
    val entityDefinition = entityDefinition(stringProperty(nullable = true))
    val schema = listOf(SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 3), mandatory = true))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "name"))

    val result = validate(mapping, entityDefinition, schema)

    assertThat(result.issues).isEmpty()
  }

  @Test
  fun `incompatible source type without a conversion is reported`() {
    val entityDefinition = entityDefinition(Property(id = propertyId, name = "Age", type = PropertyType.LONG, nullable = true))
    val schema = listOf(SchemaProperty("age", mapOf(SchemaPropertyType.STRING to 1), mandatory = true))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "age"))

    val result = validate(mapping, entityDefinition, schema)

    assertThat(result.blockingIssues).containsExactly(MappingIssue.IncompatibleType(propertyId, SchemaPropertyType.STRING, PropertyType.LONG))
  }

  @Test
  fun `configured conversion resolves an otherwise incompatible type mismatch`() {
    val entityDefinition = entityDefinition(Property(id = propertyId, name = "Age", type = PropertyType.LONG, nullable = true))
    val schema = listOf(SchemaProperty("age", mapOf(SchemaPropertyType.STRING to 1), mandatory = true))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "age", conversion = FieldMappingConversion.STRING_TO_LONG))

    val result = validate(mapping, entityDefinition, schema)

    assertThat(result.issues).isEmpty()
  }

  @Test
  fun `numeric value below the target minimum is reported`() {
    val entityDefinition = entityDefinition(
      Property(id = propertyId, name = "Age", type = PropertyType.LONG, nullable = true, constraints = setOf(PropertyConstraint.MinLong(18))),
    )
    val schema = listOf(SchemaProperty("age", mapOf(SchemaPropertyType.LONG to 2), mandatory = true, numericRange = NumericRange(min = 5.0, max = 40.0)))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "age"))

    val result = validate(mapping, entityDefinition, schema)

    assertThat(result.blockingIssues).containsExactly(MappingIssue.NumericRangeViolation(propertyId, 18.0, null, 5.0, 40.0))
  }

  @Test
  fun `numeric value within the target range produces no issue`() {
    val entityDefinition = entityDefinition(
      Property(id = propertyId, name = "Age", type = PropertyType.LONG, nullable = true, constraints = setOf(PropertyConstraint.MinLong(0), PropertyConstraint.MaxLong(100))),
    )
    val schema = listOf(SchemaProperty("age", mapOf(SchemaPropertyType.LONG to 2), mandatory = true, numericRange = NumericRange(min = 5.0, max = 40.0)))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "age"))

    val result = validate(mapping, entityDefinition, schema)

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

    val result = validate(mapping, entityDefinition, schema)

    assertThat(result.blockingIssues).containsExactly(MappingIssue.StringLengthViolation(propertyId, null, 5, 3, 10))
  }

  @Test
  fun `pattern constraint is reported as not statically validated but does not block READY`() {
    val entityDefinition = entityDefinition(
      Property(id = propertyId, name = "Code", type = PropertyType.STRING, nullable = true, constraints = setOf(PropertyConstraint.Pattern("^[A-Z]+$"))),
    )
    val schema = listOf(SchemaProperty("code", mapOf(SchemaPropertyType.STRING to 1), mandatory = true))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "code"))

    val result = validate(mapping, entityDefinition, schema)

    assertThat(result.notStaticallyValidated).containsExactly(MappingIssue.NotStaticallyValidated(propertyId, "^[A-Z]+$"))
    assertThat(result.isReady).isTrue()
  }

  @Test
  fun `fallback value violating a target constraint is reported`() {
    val entityDefinition = entityDefinition(
      Property(id = propertyId, name = "Age", type = PropertyType.LONG, nullable = false, constraints = setOf(PropertyConstraint.MaxLong(10))),
    )
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, fallbackValue = "42"))

    val result = validate(mapping, entityDefinition, emptyList())

    assertThat(result.blockingIssues).containsExactly(MappingIssue.FallbackValueViolatesConstraint(propertyId, PropertyConstraintViolation.MaxValueViolation(10L)))
  }

  @Test
  fun `fallback value violating a pattern constraint is not statically reported`() {
    val entityDefinition = entityDefinition(
      Property(id = propertyId, name = "Code", type = PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.Pattern("^[A-Z]+$"))),
    )
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, fallbackValue = "not-matching"))

    val result = validate(mapping, entityDefinition, emptyList())

    assertThat(result.blockingIssues).isEmpty()
    assertThat(result.isReady).isTrue()
  }

  @Test
  fun `reference lookup with no criteria is reported and blocks a mandatory property`() {
    val entityDefinition = entityDefinition(referenceProperty(nullable = false))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, referenceLookup = ReferenceLookup(emptyList())))

    val result = validate(mapping, entityDefinition, emptyList(), listOf(entityDefinition, referencedEntity()))

    assertThat(result.blockingIssues).contains(MappingIssue.ReferenceLookupMissingCriteria(propertyId))
  }

  @Test
  fun `reference lookup criterion targeting an unknown property of the referenced entity is reported`() {
    val entityDefinition = entityDefinition(referenceProperty(nullable = true))
    val mapping = mapping(
      FieldMapping(
        targetPropertyId = propertyId,
        referenceLookup = ReferenceLookup(listOf(ReferenceLookupCriterion(PropertyId("unknown"), "code"))),
      ),
    )

    val result = validate(mapping, entityDefinition, listOf(SchemaProperty("code", mapOf(SchemaPropertyType.STRING to 1), mandatory = true)), listOf(entityDefinition, referencedEntity()))

    assertThat(result.blockingIssues).containsExactly(MappingIssue.ReferenceLookupInvalidCriterion(propertyId, PropertyId("unknown")))
  }

  @Test
  fun `reference lookup criterion with an incompatible source type is reported`() {
    val entityDefinition = entityDefinition(referenceProperty(nullable = true))
    val referenced = referencedEntity()
    val mapping = mapping(
      FieldMapping(
        targetPropertyId = propertyId,
        referenceLookup = ReferenceLookup(listOf(ReferenceLookupCriterion(referenced.properties.single().id, "code"))),
      ),
    )
    val schema = listOf(SchemaProperty("code", mapOf(SchemaPropertyType.BOOLEAN to 1), mandatory = true))

    val result = validate(mapping, entityDefinition, schema, listOf(entityDefinition, referenced))

    assertThat(result.blockingIssues).containsExactly(
      MappingIssue.ReferenceLookupIncompatibleType(propertyId, referenced.properties.single().id, SchemaPropertyType.BOOLEAN, PropertyType.STRING),
    )
  }

  @Test
  fun `reference lookup with a valid criterion produces no issue`() {
    val entityDefinition = entityDefinition(referenceProperty(nullable = true))
    val referenced = referencedEntity()
    val mapping = mapping(
      FieldMapping(
        targetPropertyId = propertyId,
        referenceLookup = ReferenceLookup(listOf(ReferenceLookupCriterion(referenced.properties.single().id, "code"))),
      ),
    )
    val schema = listOf(SchemaProperty("code", mapOf(SchemaPropertyType.STRING to 1), mandatory = true))

    val result = validate(mapping, entityDefinition, schema, listOf(entityDefinition, referenced))

    assertThat(result.issues).isEmpty()
  }

  private fun stringProperty(nullable: Boolean) = Property(id = propertyId, name = "Name", type = PropertyType.STRING, nullable = nullable)

  private fun referenceProperty(nullable: Boolean) =
    Property(id = propertyId, name = "Company", type = PropertyType.REF, nullable = nullable, targetEntityId = referencedEntityId)

  private fun referencedEntity() = EntityDefinition(
    id = referencedEntityId,
    name = "Company",
    properties = listOf(Property(id = PropertyId("code-prop"), name = "Code", type = PropertyType.STRING, nullable = false)),
  )

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
