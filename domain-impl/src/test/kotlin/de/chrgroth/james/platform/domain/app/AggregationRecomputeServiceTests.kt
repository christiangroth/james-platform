package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.error.AggregationError
import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
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
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValue
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueId
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueStatus
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.readmodel.AggregationRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AggregationRecomputeServiceTests {

  private val installedAppRepository: InstalledAppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val appDataRepository: AppDataRepositoryPort = mockk()
  private val aggregationRepository: AggregationRepositoryPort = mockk()
  private val service = AggregationRecomputeService(installedAppRepository, appVersionRepository, appDataRepository, aggregationRepository)

  private val installedAppId = InstalledAppId("installed-app-1")
  private val appId = AppId("app-1")
  private val entityId = EntityDefinitionId("entity-1")
  private val amountPropId = PropertyId("amount")
  private val amountProp = Property(id = amountPropId, name = "Amount", type = PropertyType.LONG)
  private val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Total", function = AggregationFunction.SUM, sourceProperty = amountPropId)
  private val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = listOf(amountProp), aggregations = listOf(aggregation))
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
  private val installedApp = InstalledApp(id = installedAppId, userId = "user-1", appId = appId, installedVersionNumber = VersionNumber("1.0.0"), installedAt = Instant.now())

  private fun appData(id: String, value: String?) = AppData(
    id = AppDataId(id),
    userId = "user-1",
    installedAppId = installedAppId,
    appVersion = VersionNumber("1.0.0"),
    lastValidatedWithVersion = VersionNumber("1.0.0"),
    entityType = entityId,
    objectVersion = 1,
    createdAt = Instant.now(),
    lastChangedAt = Instant.now(),
    data = mapOf(amountPropId.value to value),
  )

  @Test
  fun `recomputes and saves the full scan result, after clearing previous values`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns listOf(appData("d1", "10"), appData("d2", "5"))
    justRun { aggregationRepository.deleteAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregation.id) }
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    val result = service.handle(DomainOutboxEvent.RecomputeAggregation(installedAppId.value, aggregation.id.value))

    assertThat(result.isRight()).isTrue()
    verify { aggregationRepository.deleteAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregation.id) }
    assertThat(savedSlot.captured.value).isEqualTo(15.0)
    assertThat(savedSlot.captured.status).isEqualTo(AggregationValueStatus.UP_TO_DATE)
  }

  @Test
  fun `treats an already-gone installation as already processed`() {
    every { installedAppRepository.findById(installedAppId) } returns null

    val result = service.handle(DomainOutboxEvent.RecomputeAggregation(installedAppId.value, aggregation.id.value))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { aggregationRepository.save(any()) }
  }

  @Test
  fun `fails when the app version cannot be resolved`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns null

    val result = service.handle(DomainOutboxEvent.RecomputeAggregation(installedAppId.value, aggregation.id.value))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AggregationError.INSTALLED_APP_NOT_FOUND)
  }

  @Test
  fun `clears stale values and does not save anything when the aggregation definition no longer exists`() {
    val entityWithoutAggregation = entityDef.copy(aggregations = emptyList())
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion.copy(entityDefinitions = listOf(entityWithoutAggregation))
    justRun { aggregationRepository.deleteAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregation.id) }

    val result = service.handle(DomainOutboxEvent.RecomputeAggregation(installedAppId.value, aggregation.id.value))

    assertThat(result.isRight()).isTrue()
    verify { aggregationRepository.deleteAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregation.id) }
    verify(exactly = 0) { aggregationRepository.save(any()) }
    verify(exactly = 0) { appDataRepository.findAllByInstalledAppIdAndEntityType(any(), any()) }
  }

  @Test
  fun `an item without a numeric source value does not contribute`() {
    every { installedAppRepository.findById(installedAppId) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(appId, VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns listOf(appData("d1", null), appData("d2", "5"))
    justRun { aggregationRepository.deleteAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregation.id) }
    val savedSlot = slot<AggregationValue>()
    justRun { aggregationRepository.save(capture(savedSlot)) }

    val result = service.handle(DomainOutboxEvent.RecomputeAggregation(installedAppId.value, aggregation.id.value))

    assertThat(result.isRight()).isTrue()
    assertThat(savedSlot.captured.value).isEqualTo(5.0)
    assertThat(savedSlot.captured.sampleCount).isEqualTo(1)
  }
}
