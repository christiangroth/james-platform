package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AppDataMigrationServiceTests {

  private val appRepository: AppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val service = AppDataMigrationService(appRepository, appVersionRepository)

  private val entityWithDisplayText = EntityDefinition(
    id = EntityDefinitionId("entity-1"),
    name = "EntityWithText",
    displayText = "some template",
  )
  private val entityWithoutDisplayText = EntityDefinition(
    id = EntityDefinitionId("entity-2"),
    name = "EntityWithoutText",
    displayText = null,
  )

  private fun version(id: String, entities: List<EntityDefinition>) = AppVersion(
    id = AppVersionId(id),
    appId = AppId("app-1"),
    versionNumber = VersionNumber("1.0.0"),
    releaseNotes = "notes",
    entityDefinitions = entities,
    reports = emptyList(),
    status = AppVersionStatus.PUBLISHED,
    createdAt = Instant.now(),
  )

  @Test
  fun `backfillEntityDisplayText does nothing when all entities already have displayText`() {
    val ver = version("ver-1", listOf(entityWithDisplayText))
    every { appVersionRepository.findAll() } returns listOf(ver)

    service.backfillEntityDisplayText()

    verify(exactly = 0) { appVersionRepository.save(any()) }
  }

  @Test
  fun `backfillEntityDisplayText does nothing when no versions exist`() {
    every { appVersionRepository.findAll() } returns emptyList()

    service.backfillEntityDisplayText()

    verify(exactly = 0) { appVersionRepository.save(any()) }
  }

  @Test
  fun `backfillEntityDisplayText backfills null displayText with fallback static text`() {
    val ver = version("ver-1", listOf(entityWithoutDisplayText))
    every { appVersionRepository.findAll() } returns listOf(ver)
    val savedSlot = slot<AppVersion>()
    justRun { appVersionRepository.save(capture(savedSlot)) }

    service.backfillEntityDisplayText()

    verify(exactly = 1) { appVersionRepository.save(any()) }
    val saved = savedSlot.captured
    val entity = saved.entityDefinitions.single()
    assertThat(entity.displayText).isEqualTo(AppDataMigrationService.FALLBACK_DISPLAY_TEXT)
  }

  @Test
  fun `backfillEntityDisplayText only updates entities without displayText and leaves others unchanged`() {
    val ver = version("ver-1", listOf(entityWithDisplayText, entityWithoutDisplayText))
    every { appVersionRepository.findAll() } returns listOf(ver)
    val savedSlot = slot<AppVersion>()
    justRun { appVersionRepository.save(capture(savedSlot)) }

    service.backfillEntityDisplayText()

    verify(exactly = 1) { appVersionRepository.save(any()) }
    val saved = savedSlot.captured
    val (first, second) = saved.entityDefinitions
    assertThat(first.displayText).isEqualTo("some template")
    assertThat(second.displayText).isEqualTo(AppDataMigrationService.FALLBACK_DISPLAY_TEXT)
  }

  @Test
  fun `backfillEntityDisplayText saves only versions that have at least one null displayText`() {
    val versionNeedingUpdate = version("ver-1", listOf(entityWithoutDisplayText))
    val versionOk = version("ver-2", listOf(entityWithDisplayText))
    every { appVersionRepository.findAll() } returns listOf(versionNeedingUpdate, versionOk)
    justRun { appVersionRepository.save(any()) }

    service.backfillEntityDisplayText()

    verify(exactly = 1) { appVersionRepository.save(match { it.id == AppVersionId("ver-1") }) }
    verify(exactly = 0) { appVersionRepository.save(match { it.id == AppVersionId("ver-2") }) }
  }
}
