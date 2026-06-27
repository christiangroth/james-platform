package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.error.AppDataConstraintViolationError
import de.chrgroth.james.platform.domain.error.AppDataError
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AppDataServiceTests {

  private val installedAppRepository: InstalledAppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val appDataRepository: AppDataRepositoryPort = mockk()
  private val propertyConstraint: PropertyConstraintPort = mockk()
  private val service = AppDataService(installedAppRepository, appVersionRepository, appDataRepository, propertyConstraint)

  private val installedAppId = InstalledAppId("installed-app-1")
  private val appId = AppId("app-1")
  private val entityId = EntityDefinitionId("entity-1")
  private val prop1Id = PropertyId("prop-1")
  private val prop2Id = PropertyId("prop-2")
  private val userId = "user-1"

  private val prop1 = Property(id = prop1Id, name = "Field1", type = PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.UniqueKey))
  private val prop2 = Property(id = prop2Id, name = "Field2", type = PropertyType.LONG, nullable = true, constraints = setOf(PropertyConstraint.MinLong(10L)))
  private val entityDef = EntityDefinition(id = entityId, name = "TestEntity", properties = listOf(prop1, prop2))
  private val appVersion = AppVersion(
    id = AppVersionId("ver-1"),
    appId = appId,
    versionNumber = VersionNumber("1.0.0"),
    releaseNotes = "test",
    entityDefinitions = listOf(entityDef),
    reports = emptyList(),
    status = AppVersionStatus.PUBLISHED,
    createdAt = Instant.now(),
  )
  private val installedApp = InstalledApp(
    id = installedAppId,
    userId = userId,
    appId = appId,
    installedVersionNumber = VersionNumber("1.0.0"),
    installedAt = Instant.now(),
  )
  private val existingAppData = AppData(
    id = AppDataId("data-1"),
    userId = userId,
    installedAppId = installedAppId,
    appVersion = VersionNumber("1.0.0"),
    entityType = entityId,
    objectVersion = 1,
    createdAt = Instant.now(),
    lastChangedAt = Instant.now(),
    data = mapOf("prop-1" to "existing-value"),
  )

  // region createAppData constraint violations

  @Test
  fun `createAppData returns AppDataConstraintViolationError with all violations when multiple properties fail`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns emptyList()
    every { propertyConstraint.checkValue(prop1, "duplicate", emptyList()) } returns listOf(PropertyConstraintViolation.UniqueKeyViolation)
    every { propertyConstraint.checkValue(prop2, null, emptyList()) } returns listOf(PropertyConstraintViolation.MinValueViolation(10L))

    val data = mapOf("prop_${prop1Id.value}" to "duplicate")
    val result = service.createAppData(userId, installedAppId.value, entityId.value, data)

    assertThat(result.isLeft()).isTrue()
    val error = result.leftOrNull()
    assertThat(error).isInstanceOf(AppDataConstraintViolationError::class.java)
    val violationError = error as AppDataConstraintViolationError
    assertThat(violationError.code).isEqualTo(AppDataError.CONSTRAINT_VIOLATION.code)
    assertThat(violationError.propertyViolations).containsKey(prop1Id.value)
    assertThat(violationError.propertyViolations).containsKey(prop2Id.value)
    assertThat(violationError.propertyViolations[prop1Id.value]).containsExactly(PropertyConstraintViolation.UniqueKeyViolation)
    assertThat(violationError.propertyViolations[prop2Id.value]).containsExactly(PropertyConstraintViolation.MinValueViolation(10L))
  }

  @Test
  fun `createAppData returns AppDataConstraintViolationError for single property violation`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns emptyList()
    every { propertyConstraint.checkValue(prop1, "dup", emptyList()) } returns listOf(PropertyConstraintViolation.UniqueKeyViolation)
    every { propertyConstraint.checkValue(prop2, null, emptyList()) } returns emptyList()

    val data = mapOf("prop_${prop1Id.value}" to "dup")
    val result = service.createAppData(userId, installedAppId.value, entityId.value, data)

    assertThat(result.isLeft()).isTrue()
    val error = result.leftOrNull() as AppDataConstraintViolationError
    assertThat(error.propertyViolations).hasSize(1)
    assertThat(error.propertyViolations[prop1Id.value]).containsExactly(PropertyConstraintViolation.UniqueKeyViolation)
  }

  @Test
  fun `createAppData succeeds when no constraint violations`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns emptyList()
    every { propertyConstraint.checkValue(prop1, "value", emptyList()) } returns emptyList()
    every { propertyConstraint.checkValue(prop2, null, emptyList()) } returns emptyList()
    justRun { appDataRepository.save(any()) }

    val data = mapOf("prop_${prop1Id.value}" to "value")
    val result = service.createAppData(userId, installedAppId.value, entityId.value, data)

    assertThat(result.isRight()).isTrue()
  }

  // endregion

  // region updateAppData constraint violations

  @Test
  fun `updateAppData returns AppDataConstraintViolationError with all violations when multiple properties fail`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appDataRepository.findById(AppDataId("data-1")) } returns existingAppData
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns emptyList()
    every { propertyConstraint.checkValue(prop1, "dup", emptyList()) } returns listOf(PropertyConstraintViolation.UniqueKeyViolation)
    every { propertyConstraint.checkValue(prop2, null, emptyList()) } returns listOf(PropertyConstraintViolation.MinValueViolation(10L))

    val data = mapOf("prop_${prop1Id.value}" to "dup")
    val result = service.updateAppData(userId, installedAppId.value, "data-1", data)

    assertThat(result.isLeft()).isTrue()
    val error = result.leftOrNull() as AppDataConstraintViolationError
    assertThat(error.propertyViolations).containsKey(prop1Id.value)
    assertThat(error.propertyViolations).containsKey(prop2Id.value)
  }

  @Test
  fun `updateAppData succeeds when no constraint violations`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appDataRepository.findById(AppDataId("data-1")) } returns existingAppData
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns emptyList()
    every { propertyConstraint.checkValue(prop1, "value", emptyList()) } returns emptyList()
    every { propertyConstraint.checkValue(prop2, null, emptyList()) } returns emptyList()
    justRun { appDataRepository.save(any()) }

    val data = mapOf("prop_${prop1Id.value}" to "value")
    val result = service.updateAppData(userId, installedAppId.value, "data-1", data)

    assertThat(result.isRight()).isTrue()
  }

  // endregion

  // region deleteAppData reference checks

  private val refPropId = PropertyId("ref-prop")
  private val refEntityId = EntityDefinitionId("entity-2")
  private val refPropNullable = Property(id = refPropId, name = "RefField", type = PropertyType.REF, nullable = true)
  private val refPropNonNullable = Property(id = refPropId, name = "RefField", type = PropertyType.REF, nullable = false)
  private val refEntityDef = EntityDefinition(id = refEntityId, name = "RefEntity", properties = listOf(refPropNullable))
  private val appVersionWithRef = appVersion.copy(entityDefinitions = listOf(entityDef, refEntityDef))

  private val referencingData = AppData(
    id = AppDataId("data-2"),
    userId = userId,
    installedAppId = installedAppId,
    appVersion = VersionNumber("1.0.0"),
    entityType = refEntityId,
    objectVersion = 1,
    createdAt = Instant.now(),
    lastChangedAt = Instant.now(),
    data = mapOf(refPropId.value to "data-1"),
  )

  @Test
  fun `deleteAppData succeeds with no references`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appDataRepository.findById(AppDataId("data-1")) } returns existingAppData
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppId(installedAppId) } returns listOf(existingAppData)
    justRun { appDataRepository.delete(AppDataId("data-1")) }

    val result = service.deleteAppData(userId, installedAppId.value, "data-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).isEqualTo(0)
    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `deleteAppData nulls out nullable references and returns count`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appDataRepository.findById(AppDataId("data-1")) } returns existingAppData
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersionWithRef
    every { appDataRepository.findAllByInstalledAppId(installedAppId) } returns listOf(existingAppData, referencingData)
    justRun { appDataRepository.save(any()) }
    justRun { appDataRepository.delete(AppDataId("data-1")) }

    val result = service.deleteAppData(userId, installedAppId.value, "data-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).isEqualTo(1)
    verify(exactly = 1) { appDataRepository.save(match { it.id.value == "data-2" && it.data[refPropId.value] == null }) }
    verify(exactly = 1) { appDataRepository.delete(AppDataId("data-1")) }
  }

  @Test
  fun `deleteAppData rejects deletion when referenced by non-nullable property`() {
    val nonNullableRefEntityDef = EntityDefinition(id = refEntityId, name = "RefEntity", properties = listOf(refPropNonNullable))
    val appVersionWithNonNullableRef = appVersion.copy(entityDefinitions = listOf(entityDef, nonNullableRefEntityDef))

    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appDataRepository.findById(AppDataId("data-1")) } returns existingAppData
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersionWithNonNullableRef
    every { appDataRepository.findAllByInstalledAppId(installedAppId) } returns listOf(existingAppData, referencingData)

    val result = service.deleteAppData(userId, installedAppId.value, "data-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppDataError.REFERENCED_BY_NON_NULLABLE_PROPERTY)
    verify(exactly = 0) { appDataRepository.save(any()) }
    verify(exactly = 0) { appDataRepository.delete(any()) }
  }

  @Test
  fun `deleteAppData ignores REF properties that do not reference the deleted item`() {
    val otherData = referencingData.copy(data = mapOf(refPropId.value to "other-id"))
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appDataRepository.findById(AppDataId("data-1")) } returns existingAppData
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersionWithRef
    every { appDataRepository.findAllByInstalledAppId(installedAppId) } returns listOf(existingAppData, otherData)
    justRun { appDataRepository.delete(AppDataId("data-1")) }

    val result = service.deleteAppData(userId, installedAppId.value, "data-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).isEqualTo(0)
    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  // endregion

  // region createAppData reference validation

  private val refTargetPropId = PropertyId("ref-target-prop")
  private val refTargetProp = Property(id = refTargetPropId, name = "RefField", type = PropertyType.REF, nullable = true, targetEntityId = entityId)
  private val refTargetEntityDef = EntityDefinition(id = refEntityId, name = "RefEntity", properties = listOf(refTargetProp))
  private val appVersionWithRefTarget = appVersion.copy(entityDefinitions = listOf(entityDef, refTargetEntityDef))

  @Test
  fun `createAppData rejects REF value that does not point to an existing target instance`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersionWithRefTarget
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, refEntityId) } returns emptyList()
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns emptyList()
    every { propertyConstraint.checkValue(refTargetProp, "unknown-id", emptyList()) } returns emptyList()

    val data = mapOf("prop_${refTargetPropId.value}" to "unknown-id")
    val result = service.createAppData(userId, installedAppId.value, refEntityId.value, data)

    assertThat(result.isLeft()).isTrue()
    val error = result.leftOrNull() as AppDataConstraintViolationError
    assertThat(error.propertyViolations[refTargetPropId.value]).containsExactly(PropertyConstraintViolation.InvalidReferenceViolation)
  }

  @Test
  fun `createAppData accepts REF value pointing to an existing target instance`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersionWithRefTarget
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, refEntityId) } returns emptyList()
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns listOf(existingAppData)
    every { propertyConstraint.checkValue(refTargetProp, "data-1", emptyList()) } returns emptyList()
    justRun { appDataRepository.save(any()) }

    val data = mapOf("prop_${refTargetPropId.value}" to "data-1")
    val result = service.createAppData(userId, installedAppId.value, refEntityId.value, data)

    assertThat(result.isRight()).isTrue()
  }

  @Test
  fun `createAppData rejects REF value when property has no target entity configured`() {
    val unconfiguredRefEntityDef = EntityDefinition(id = refEntityId, name = "RefEntity", properties = listOf(refPropNullable))
    val versionWithUnconfiguredRef = appVersion.copy(entityDefinitions = listOf(entityDef, unconfiguredRefEntityDef))
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns versionWithUnconfiguredRef
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, refEntityId) } returns emptyList()
    every { propertyConstraint.checkValue(refPropNullable, "data-1", emptyList()) } returns emptyList()

    val data = mapOf("prop_${refPropId.value}" to "data-1")
    val result = service.createAppData(userId, installedAppId.value, refEntityId.value, data)

    assertThat(result.isLeft()).isTrue()
    val error = result.leftOrNull() as AppDataConstraintViolationError
    assertThat(error.propertyViolations[refPropId.value]).containsExactly(PropertyConstraintViolation.InvalidReferenceViolation)
  }

  // endregion

  // region getValueProposals

  @Test
  fun `getValueProposals returns empty list when no value proposals defined`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns listOf(existingAppData)

    val result = service.getValueProposals(userId, installedAppId.value, entityId.value, prop1Id.value, emptyMap())

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).isEmpty()
  }

  @Test
  fun `getValueProposals returns proposals sorted by frequency`() {
    val vpProp = prop1.copy(valueProposals = emptyList())
    val filterProp = prop2.copy(type = PropertyType.STRING)
    val propWithVp = vpProp.copy(valueProposals = listOf(filterProp.id.value))
    val entity = entityDef.copy(properties = listOf(propWithVp, filterProp))
    val version = appVersion.copy(entityDefinitions = listOf(entity))
    val data1 = existingAppData.copy(id = AppDataId("d1"), data = mapOf(prop1Id.value to "Alpha", prop2Id.value to "X"))
    val data2 = existingAppData.copy(id = AppDataId("d2"), data = mapOf(prop1Id.value to "Beta", prop2Id.value to "X"))
    val data3 = existingAppData.copy(id = AppDataId("d3"), data = mapOf(prop1Id.value to "Alpha", prop2Id.value to "X"))
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns version
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns listOf(data1, data2, data3)

    val result = service.getValueProposals(userId, installedAppId.value, entityId.value, prop1Id.value, mapOf(prop2Id.value to "X"))

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).containsExactly("Alpha", "Beta")
  }

  @Test
  fun `getValueProposals filters by current data values`() {
    val filterProp = prop2.copy(type = PropertyType.STRING)
    val propWithVp = prop1.copy(valueProposals = listOf(filterProp.id.value))
    val entity = entityDef.copy(properties = listOf(propWithVp, filterProp))
    val version = appVersion.copy(entityDefinitions = listOf(entity))
    val data1 = existingAppData.copy(id = AppDataId("d1"), data = mapOf(prop1Id.value to "Alpha", prop2Id.value to "X"))
    val data2 = existingAppData.copy(id = AppDataId("d2"), data = mapOf(prop1Id.value to "Beta", prop2Id.value to "Y"))
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns version
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns listOf(data1, data2)

    val result = service.getValueProposals(userId, installedAppId.value, entityId.value, prop1Id.value, mapOf(prop2Id.value to "X"))

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).containsExactly("Alpha")
  }

  @Test
  fun `getValueProposals fails when installed app not found`() {
    every { installedAppRepository.findById(installedAppId) } returns null

    val result = service.getValueProposals(userId, installedAppId.value, entityId.value, prop1Id.value, emptyMap())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppDataError.INSTALLED_APP_NOT_FOUND)
  }

  @Test
  fun `getValueProposals fails when entity not found`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion

    val result = service.getValueProposals(userId, installedAppId.value, "unknown-entity", prop1Id.value, emptyMap())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppDataError.ENTITY_NOT_FOUND)
  }

  @Test
  fun `getValueProposals fails when property not found`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion

    val result = service.getValueProposals(userId, installedAppId.value, entityId.value, "unknown-prop", emptyMap())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppDataError.PROPERTY_NOT_FOUND)
  }

  // endregion
}
