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
import de.chrgroth.james.platform.domain.model.imports.DurationConversionUnit
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookup
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookupCriterion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DryRunExecutorTests {

  private val objectMapper = jacksonObjectMapper()
  private val propertyConstraint = PropertyConstraintService()
  private val propertyId = PropertyId("prop-1")

  private fun records(json: String) = objectMapper.readTree(json).toList()

  private fun mapping(vararg fieldMappings: FieldMapping) =
    Mapping(fieldMappings = fieldMappings.toList())

  private fun entityDefinition(vararg properties: Property) = EntityDefinition(id = EntityDefinitionId("entity-1"), name = "Contact", properties = properties.toList())

  private fun execute(
    records: List<com.fasterxml.jackson.databind.JsonNode>,
    mapping: Mapping,
    entityDefinition: EntityDefinition,
    existingAppData: List<AppData> = emptyList(),
    entityDefinitionsById: Map<EntityDefinitionId, EntityDefinition> = mapOf(entityDefinition.id to entityDefinition),
    referencedAppDataByEntityId: Map<EntityDefinitionId, List<AppData>> = emptyMap(),
  ) = DryRunExecutor.execute(records, mapping, entityDefinition, existingAppData, entityDefinitionsById, referencedAppDataByEntityId, propertyConstraint)

  private fun appData(entityType: EntityDefinitionId, data: Map<String, String?>, id: String = "existing-1") = AppData(
    id = AppDataId(id),
    userId = "user-1",
    installedAppId = InstalledAppId("installed-1"),
    appVersion = VersionNumber("1.0.0"),
    entityType = entityType,
    objectVersion = 1,
    createdAt = Instant.now(),
    lastChangedAt = Instant.now(),
    data = data,
  )

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
  fun `datetime to date conversion truncates the raw value to its date part`() {
    val entity = entityDefinition(Property(id = propertyId, name = "Birthday", type = PropertyType.DATE, nullable = false))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "createdAt", conversion = FieldMappingConversion.DATETIME_TO_DATE))

    val result = execute(records("""[{"createdAt":"2024-01-15T10:30:00Z"}]"""), mapping, entity)

    assertThat(result.single().isValid).isTrue()
    assertThat(result.single().targetData).isEqualTo(mapOf(propertyId to "2024-01-15"))
  }

  @Test
  fun `long to duration conversion formats a raw integer as a duration in the configured unit`() {
    val entity = entityDefinition(Property(id = propertyId, name = "Runtime", type = PropertyType.DURATION, nullable = false))
    val mapping = mapping(
      FieldMapping(targetPropertyId = propertyId, sourcePath = "minutes", conversion = FieldMappingConversion.LONG_TO_DURATION, conversionUnit = DurationConversionUnit.MINUTES),
    )

    val result = execute(records("""[{"minutes":90}]"""), mapping, entity)

    assertThat(result.single().isValid).isTrue()
    assertThat(result.single().targetData).isEqualTo(mapOf(propertyId to "1:30:00"))
  }

  @Test
  fun `long to duration conversion without a configured unit defaults to seconds`() {
    val entity = entityDefinition(Property(id = propertyId, name = "Runtime", type = PropertyType.DURATION, nullable = false))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "seconds", conversion = FieldMappingConversion.LONG_TO_DURATION))

    val result = execute(records("""[{"seconds":90}]"""), mapping, entity)

    assertThat(result.single().targetData).isEqualTo(mapOf(propertyId to "0:01:30"))
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
    assertThat(result[1].issues.single()).isEqualTo(
      DryRunIssue.ConstraintViolated(propertyId, PropertyConstraintViolation.UniqueKeyViolation, staticallyChecked = false, expected = true),
    )
  }

  @Test
  fun `fan-in mapping keeps only the first record per unique value and reports records without or with an already used value as expected skips, not invalid`() {
    val entity = entityDefinition(
      Property(id = propertyId, name = "Code", type = PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.UniqueKey)),
    )
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "code"))

    val result = execute(records("""[{"code":"A"},{},{"code":"A"},{"code":"B"}]"""), mapping, entity)

    assertThat(result[0].isValid).isTrue()
    assertThat(result[1].issues.single()).isEqualTo(DryRunIssue.MissingMandatoryValue(propertyId, expected = true))
    assertThat(result[1].isSkipped).isTrue()
    assertThat(result[1].isInvalid).isFalse()
    assertThat(result[2].issues.single()).isEqualTo(
      DryRunIssue.ConstraintViolated(propertyId, PropertyConstraintViolation.UniqueKeyViolation, staticallyChecked = false, expected = true),
    )
    assertThat(result[2].isSkipped).isTrue()
    assertThat(result[2].isInvalid).isFalse()
    assertThat(result[3].isValid).isTrue()
  }

  @Test
  fun `reference lookup resolves to the id of the matching referenced entity`() {
    val referencedEntityId = EntityDefinitionId("entity-2")
    val codePropertyId = PropertyId("code-prop")
    val referencedEntity = EntityDefinition(id = referencedEntityId, name = "Company", properties = listOf(Property(id = codePropertyId, name = "Code", type = PropertyType.STRING, nullable = false)))
    val entity = entityDefinition(Property(id = propertyId, name = "Company", type = PropertyType.REF, nullable = false, targetEntityId = referencedEntityId))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, referenceLookup = ReferenceLookup(listOf(ReferenceLookupCriterion(codePropertyId, "companyCode")))))
    val matching = appData(referencedEntityId, mapOf(codePropertyId.value to "ACME"), id = "company-1")

    val result = execute(
      records("""[{"companyCode":"ACME"}]"""),
      mapping,
      entity,
      entityDefinitionsById = mapOf(entity.id to entity, referencedEntityId to referencedEntity),
      referencedAppDataByEntityId = mapOf(referencedEntityId to listOf(matching)),
    )

    assertThat(result.single().isValid).isTrue()
    assertThat(result.single().targetData).isEqualTo(mapOf(propertyId to "company-1"))
  }

  @Test
  fun `reference lookup without a match falls back to the configured fallback value`() {
    val referencedEntityId = EntityDefinitionId("entity-2")
    val codePropertyId = PropertyId("code-prop")
    val referencedEntity = EntityDefinition(id = referencedEntityId, name = "Company", properties = listOf(Property(id = codePropertyId, name = "Code", type = PropertyType.STRING, nullable = false)))
    val entity = entityDefinition(Property(id = propertyId, name = "Company", type = PropertyType.REF, nullable = false, targetEntityId = referencedEntityId))
    val mapping = mapping(
      FieldMapping(
        targetPropertyId = propertyId,
        referenceLookup = ReferenceLookup(listOf(ReferenceLookupCriterion(codePropertyId, "companyCode"))),
        fallbackValue = "default-company",
      ),
    )

    val result = execute(
      records("""[{"companyCode":"UNKNOWN"}]"""),
      mapping,
      entity,
      entityDefinitionsById = mapOf(entity.id to entity, referencedEntityId to referencedEntity),
      referencedAppDataByEntityId = mapOf(referencedEntityId to emptyList()),
    )

    assertThat(result.single().isValid).isTrue()
    assertThat(result.single().targetData).isEqualTo(mapOf(propertyId to "default-company"))
  }

  @Test
  fun `reference lookup without a match and without a fallback is reported as a missing mandatory value`() {
    val referencedEntityId = EntityDefinitionId("entity-2")
    val codePropertyId = PropertyId("code-prop")
    val referencedEntity = EntityDefinition(id = referencedEntityId, name = "Company", properties = listOf(Property(id = codePropertyId, name = "Code", type = PropertyType.STRING, nullable = false)))
    val entity = entityDefinition(Property(id = propertyId, name = "Company", type = PropertyType.REF, nullable = false, targetEntityId = referencedEntityId))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, referenceLookup = ReferenceLookup(listOf(ReferenceLookupCriterion(codePropertyId, "companyCode")))))

    val result = execute(
      records("""[{"companyCode":"UNKNOWN"}]"""),
      mapping,
      entity,
      entityDefinitionsById = mapOf(entity.id to entity, referencedEntityId to referencedEntity),
      referencedAppDataByEntityId = mapOf(referencedEntityId to emptyList()),
    )

    assertThat(result.single().issues).contains(DryRunIssue.MissingMandatoryValue(propertyId))
  }

  @Test
  fun `directly mapped REF value pointing to an existing instance is valid`() {
    val referencedEntityId = EntityDefinitionId("entity-2")
    val entity = entityDefinition(Property(id = propertyId, name = "Company", type = PropertyType.REF, nullable = false, targetEntityId = referencedEntityId))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "companyId"))
    val existingCompany = appData(referencedEntityId, emptyMap(), id = "company-1")

    val result = execute(
      records("""[{"companyId":"company-1"}]"""),
      mapping,
      entity,
      referencedAppDataByEntityId = mapOf(referencedEntityId to listOf(existingCompany)),
    )

    assertThat(result.single().isValid).isTrue()
  }

  @Test
  fun `directly mapped REF value that does not point to an existing instance is reported as not statically checked`() {
    val referencedEntityId = EntityDefinitionId("entity-2")
    val entity = entityDefinition(Property(id = propertyId, name = "Company", type = PropertyType.REF, nullable = false, targetEntityId = referencedEntityId))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "companyId"))

    val result = execute(
      records("""[{"companyId":"unknown-company"}]"""),
      mapping,
      entity,
      referencedAppDataByEntityId = mapOf(referencedEntityId to emptyList()),
    )

    val issue = result.single().issues.single() as DryRunIssue.ConstraintViolated
    assertThat(issue.violation).isEqualTo(PropertyConstraintViolation.InvalidReferenceViolation)
    assertThat(issue.staticallyChecked).isFalse()
  }

  @Test
  fun `REF fallback value used without a lookup is checked for existence`() {
    val referencedEntityId = EntityDefinitionId("entity-2")
    val entity = entityDefinition(Property(id = propertyId, name = "Company", type = PropertyType.REF, nullable = false, targetEntityId = referencedEntityId))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, fallbackValue = "unknown-company"))

    val result = execute(
      records("""[{}]"""),
      mapping,
      entity,
      referencedAppDataByEntityId = mapOf(referencedEntityId to emptyList()),
    )

    val issue = result.single().issues.single() as DryRunIssue.ConstraintViolated
    assertThat(issue.violation).isEqualTo(PropertyConstraintViolation.InvalidReferenceViolation)
  }

  @Test
  fun `reference lookup fallback value is exempt from the direct-mapping existence check`() {
    val referencedEntityId = EntityDefinitionId("entity-2")
    val codePropertyId = PropertyId("code-prop")
    val referencedEntity = EntityDefinition(id = referencedEntityId, name = "Company", properties = listOf(Property(id = codePropertyId, name = "Code", type = PropertyType.STRING, nullable = false)))
    val entity = entityDefinition(Property(id = propertyId, name = "Company", type = PropertyType.REF, nullable = false, targetEntityId = referencedEntityId))
    val mapping = mapping(
      FieldMapping(
        targetPropertyId = propertyId,
        referenceLookup = ReferenceLookup(listOf(ReferenceLookupCriterion(codePropertyId, "companyCode"))),
        fallbackValue = "default-company",
      ),
    )

    val result = execute(
      records("""[{"companyCode":"UNKNOWN"}]"""),
      mapping,
      entity,
      entityDefinitionsById = mapOf(entity.id to entity, referencedEntityId to referencedEntity),
      referencedAppDataByEntityId = mapOf(referencedEntityId to emptyList()),
    )

    assertThat(result.single().isValid).isTrue()
    assertThat(result.single().targetData).isEqualTo(mapOf(propertyId to "default-company"))
  }

  @Test
  fun `executeSingle produces the same result as execute with a batch of one record`() {
    val entity = entityDefinition(Property(id = propertyId, name = "Name", type = PropertyType.STRING, nullable = false))
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "name"))
    val record = records("""[{"name":"Alice"}]""").single()

    val result = DryRunExecutor.executeSingle(record, mapping, entity, emptyList(), mapOf(entity.id to entity), emptyMap(), propertyConstraint)

    assertThat(result.isValid).isTrue()
    assertThat(result.targetData).isEqualTo(mapOf(propertyId to "Alice"))
  }

  @Test
  fun `executeSingle cannot detect a unique key collision with another record of the same import job, only with already persisted data`() {
    val entity = entityDefinition(
      Property(id = propertyId, name = "Code", type = PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.UniqueKey)),
    )
    val mapping = mapping(FieldMapping(targetPropertyId = propertyId, sourcePath = "code"))
    val record = records("""[{"code":"DUP"}]""").single()

    val withoutExistingCollision = DryRunExecutor.executeSingle(record, mapping, entity, emptyList(), mapOf(entity.id to entity), emptyMap(), propertyConstraint)
    assertThat(withoutExistingCollision.isValid).isTrue()

    val withExistingCollision = DryRunExecutor.executeSingle(
      record,
      mapping,
      entity,
      existingAppData = listOf(appData(entity.id, mapOf(propertyId.value to "DUP"))),
      entityDefinitionsById = mapOf(entity.id to entity),
      referencedAppDataByEntityId = emptyMap(),
      propertyConstraint = propertyConstraint,
    )
    val issue = withExistingCollision.issues.single() as DryRunIssue.ConstraintViolated
    assertThat(issue.violation).isEqualTo(PropertyConstraintViolation.UniqueKeyViolation)
  }
}
