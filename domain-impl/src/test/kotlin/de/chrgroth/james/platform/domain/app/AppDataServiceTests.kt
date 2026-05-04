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
    every { propertyConstraint.checkValue(prop1, "duplicate", emptyList()) } returns listOf(PropertyConstraintViolation.UNIQUE_KEY_VIOLATION)
    every { propertyConstraint.checkValue(prop2, null, emptyList()) } returns listOf(PropertyConstraintViolation.MIN_VALUE_VIOLATION)

    val data = mapOf("prop_${prop1Id.value}" to "duplicate")
    val result = service.createAppData(userId, installedAppId.value, entityId.value, data)

    assertThat(result.isLeft()).isTrue()
    val error = result.leftOrNull()
    assertThat(error).isInstanceOf(AppDataConstraintViolationError::class.java)
    val violationError = error as AppDataConstraintViolationError
    assertThat(violationError.code).isEqualTo(AppDataError.CONSTRAINT_VIOLATION.code)
    assertThat(violationError.propertyViolations).containsKey(prop1Id.value)
    assertThat(violationError.propertyViolations).containsKey(prop2Id.value)
    assertThat(violationError.propertyViolations[prop1Id.value]).containsExactly(PropertyConstraintViolation.UNIQUE_KEY_VIOLATION)
    assertThat(violationError.propertyViolations[prop2Id.value]).containsExactly(PropertyConstraintViolation.MIN_VALUE_VIOLATION)
  }

  @Test
  fun `createAppData returns AppDataConstraintViolationError for single property violation`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns emptyList()
    every { propertyConstraint.checkValue(prop1, "dup", emptyList()) } returns listOf(PropertyConstraintViolation.UNIQUE_KEY_VIOLATION)
    every { propertyConstraint.checkValue(prop2, null, emptyList()) } returns emptyList()

    val data = mapOf("prop_${prop1Id.value}" to "dup")
    val result = service.createAppData(userId, installedAppId.value, entityId.value, data)

    assertThat(result.isLeft()).isTrue()
    val error = result.leftOrNull() as AppDataConstraintViolationError
    assertThat(error.propertyViolations).hasSize(1)
    assertThat(error.propertyViolations[prop1Id.value]).containsExactly(PropertyConstraintViolation.UNIQUE_KEY_VIOLATION)
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
    every { propertyConstraint.checkValue(prop1, "dup", emptyList()) } returns listOf(PropertyConstraintViolation.UNIQUE_KEY_VIOLATION)
    every { propertyConstraint.checkValue(prop2, null, emptyList()) } returns listOf(PropertyConstraintViolation.MIN_VALUE_VIOLATION)

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
}
