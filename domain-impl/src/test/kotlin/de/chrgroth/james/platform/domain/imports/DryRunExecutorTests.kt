package de.chrgroth.james.platform.domain.imports

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.chrgroth.james.platform.domain.app.PropertyConstraintService
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.imports.DryRunIssue
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.MappingType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DryRunExecutorTests {

  private val objectMapper = jacksonObjectMapper()
  private val propertyConstraint = PropertyConstraintService()
  private val propertyId = PropertyId("prop-1")

  private fun records(json: String) = objectMapper.readTree(json).toList()

  private fun mapping(vararg fieldMappings: FieldMapping) =
    Mapping(name = "Contact", type = MappingType.FIND, targetEntityDefinitionId = EntityDefinitionId("entity-1"), fieldMappings = fieldMappings.toList())

  private fun entityDefinition(vararg properties: Property) = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Contact", properties = properties.toList())

  private fun execute(records: List<com.fasterxml.jackson.databind.JsonNode>, mapping: Mapping, entityDefinition: EntityDefinition, existingAppData: List<AppData> = emptyList()) =
    DryRunExecutor.execute(records, mapping, entityDefinition, existingAppData, propertyConstraint)

  @Test
  fun `directly mapped value with no violated constraints is valid`() {
    val entity = entityDefinition(Property(id = propertyId, name = "Name", type = PropertyType.STRING, nullable = false))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "name"))

    val result = execute(records("""[{"name":"Alice"}]"""), mapping, entity)

    assertThat(result).hasSize(1)
    assertThat(result.single().isValid).isTrue()
    assertThat(result.single().targetData).isEqualTo(mapOf(propertyId to "Alice"))
  }

  @Test
  fun `missing value for a mandatory property is reported as statically checked`() {
    val entity = entityDefinition(Property(id = propertyId, name = "Name", type = PropertyType.STRING, nullable = false))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "name"))

    val result = execute(records("""[{"name":null}]"""), mapping, entity)

    val issue = result.single().issues.single()
    assertThat(issue).isEqualTo(DryRunIssue.MissingMandatoryValue(propertyId))
    assertThat(issue.staticallyChecked).isTrue()
  }

  @Test
  fun `fallback value is used when the source path is absent`() {
    val entity = entityDefinition(Property(id = propertyId, name = "Name", type = PropertyType.STRING, nullable = false))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "missing", fallbackValue = "n/a"))

    val result = execute(records("""[{"other":"x"}]"""), mapping, entity)

    assertThat(result.single().isValid).isTrue()
    assertThat(result.single().targetData).isEqualTo(mapOf(propertyId to "n/a"))
  }

  @Test
  fun `numeric range violation is reported as statically checked`() {
    val entity = entityDefinition(
      Property(id = propertyId, name = "Age", type = PropertyType.LONG, nullable = false, constraints = setOf(PropertyConstraint.MaxLong(10))),
    )
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "age"))

    val result = execute(records("""[{"age":42}]"""), mapping, entity)

    val issue = result.single().issues.single() as DryRunIssue.ConstraintViolated
    assertThat(issue.violation).isEqualTo(PropertyConstraintViolation.MaxValueViolation(10L))
    assertThat(issue.staticallyChecked).isTrue()
  }

  @Test
  fun `string length violation is reported as statically checked`() {
    val entity = entityDefinition(
      Property(id = propertyId, name = "Code", type = PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.MaxLength(3))),
    )
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "code"))

    val result = execute(records("""[{"code":"ABCDE"}]"""), mapping, entity)

    val issue = result.single().issues.single() as DryRunIssue.ConstraintViolated
    assertThat(issue.violation).isEqualTo(PropertyConstraintViolation.MaxLengthViolation(3))
    assertThat(issue.staticallyChecked).isTrue()
  }

  @Test
  fun `pattern violation is reported as not statically checked`() {
    val entity = entityDefinition(
      Property(id = propertyId, name = "Code", type = PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.Pattern("[A-Z]+"))),
    )
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "code"))

    val result = execute(records("""[{"code":"abc"}]"""), mapping, entity)

    val issue = result.single().issues.single() as DryRunIssue.ConstraintViolated
    assertThat(issue.violation).isEqualTo(PropertyConstraintViolation.PatternViolation("[A-Z]+"))
    assertThat(issue.staticallyChecked).isFalse()
  }

  @Test
  fun `unique key violation against already persisted data is reported as not statically checked`() {
    val entity = entityDefinition(
      Property(id = propertyId, name = "Code", type = PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.UniqueKey)),
    )
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "code"))
    val existing = listOf(
      AppData(
        id = AppDataId("existing-1"),
        userId = "user-1",
        installedAppId = InstalledAppId("installed-1"),
        appVersion = VersionNumber("1.0.0"),
        entityType = EntityDefinitionId("entity-1"),
        objectVersion = 1,
        createdAt = Instant.now(),
        lastChangedAt = Instant.now(),
        data = mapOf(propertyId.value to "DUP"),
      ),
    )

    val result = execute(records("""[{"code":"DUP"}]"""), mapping, entity, existing)

    val issue = result.single().issues.single() as DryRunIssue.ConstraintViolated
    assertThat(issue.violation).isEqualTo(PropertyConstraintViolation.UniqueKeyViolation)
    assertThat(issue.staticallyChecked).isFalse()
  }

  @Test
  fun `unique key violation between two records of the same batch is detected`() {
    val entity = entityDefinition(
      Property(id = propertyId, name = "Code", type = PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.UniqueKey)),
    )
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "code"))

    val result = execute(records("""[{"code":"DUP"},{"code":"DUP"}]"""), mapping, entity)

    assertThat(result[0].isValid).isTrue()
    assertThat(result[1].issues.single()).isEqualTo(DryRunIssue.ConstraintViolated(propertyId, PropertyConstraintViolation.UniqueKeyViolation, false))
  }
}
