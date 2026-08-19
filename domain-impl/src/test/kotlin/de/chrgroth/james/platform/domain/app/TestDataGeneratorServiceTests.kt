package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.app
import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.version
import de.chrgroth.james.platform.domain.app.UserAppStoreServiceTests.Companion.installedApp
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.TestDataGeneratorError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.decodeListValue
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import de.chrgroth.james.platform.domain.port.`in`.app.TestDataGenerationOutcome
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.infra.OutboxPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TestDataGeneratorServiceTests {

  private val appRepository: AppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val installedAppRepository: InstalledAppRepositoryPort = mockk()
  private val appDataRepository: AppDataRepositoryPort = mockk()
  private val appDataPort: AppDataPort = mockk()
  private val outboxPort: OutboxPort = mockk()
  private val service = TestDataGeneratorService(appRepository, appVersionRepository, installedAppRepository, appDataRepository, appDataPort, outboxPort)

  private fun Either<DomainError, TestDataGenerationOutcome>.completed(): List<AppData> =
    (getOrNull() as TestDataGenerationOutcome.Completed).objects

  private val store = mutableListOf<AppData>()

  private val existingApp = app(id = "app-1", developerId = "dev-1")
  private val entityId = EntityDefinitionId("entity-1")

  private fun stubStore() {
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(any(), any()) } answers {
      val installedAppIdArg = InstalledAppId(firstArg<String>())
      val entityTypeArg = EntityDefinitionId(secondArg<String>())
      store.filter { it.installedAppId == installedAppIdArg && it.entityType == entityTypeArg }
    }
    every { appDataRepository.save(any()) } answers { store += firstArg<AppData>() }
  }

  private fun property(
    id: String,
    name: String = id,
    type: PropertyType,
    nullable: Boolean = true,
    constraints: Set<PropertyConstraint> = emptySet(),
    targetEntityId: EntityDefinitionId? = null,
    listItemType: PropertyType? = null,
    itemConstraints: Set<PropertyConstraint> = emptySet(),
    nestedProperties: List<Property> = emptyList(),
  ) = Property(
    id = PropertyId(id),
    name = name,
    type = type,
    nullable = nullable,
    constraints = constraints,
    targetEntityId = targetEntityId,
    listItemType = listItemType,
    itemConstraints = itemConstraints,
    nestedProperties = nestedProperties,
  )

  private fun setupInstalledVersion(entityDef: EntityDefinition, installedAppIdValue: String = "installed-1") {
    val ver = version(id = "ver-1", appId = "app-1").copy(entityDefinitions = listOf(entityDef))
    val installed = installedApp(id = installedAppIdValue, userId = "dev-1", appId = "app-1", isTest = true).copy(installedVersionId = ver.id)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId(installedAppIdValue)) } returns installed
    every { appVersionRepository.findById(ver.id) } returns ver
  }

  // region ownership / lookup failures

  @Test
  fun `generateTestData fails when app not found`() {
    every { appRepository.findById(AppId("app-1")) } returns null

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 1, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(TestDataGeneratorError.APP_NOT_FOUND)
  }

  @Test
  fun `generateTestData fails when app belongs to another developer`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 1, "dev-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(TestDataGeneratorError.APP_NOT_FOUND)
  }

  @Test
  fun `generateTestData fails when installed app is not a test installation`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns
      installedApp(id = "installed-1", appId = "app-1", isTest = false)

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 1, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(TestDataGeneratorError.INSTALLATION_NOT_FOUND)
  }

  @Test
  fun `generateTestData fails when entity not found in version`() {
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = emptyList())
    setupInstalledVersion(entityDef)

    val result = service.generateTestData("app-1", "installed-1", "unknown-entity", 1, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(TestDataGeneratorError.ENTITY_NOT_FOUND)
  }

  @Test
  fun `generateTestData fails for a non-positive count`() {
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = emptyList())
    setupInstalledVersion(entityDef)

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 0, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(TestDataGeneratorError.INVALID_COUNT)
  }

  @Test
  fun `generateTestData fails for a count above the maximum`() {
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = emptyList())
    setupInstalledVersion(entityDef)

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 501, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(TestDataGeneratorError.INVALID_COUNT)
  }

  // endregion

  // region generation and persistence

  @Test
  fun `generateTestData generates and persists the requested number of valid objects`() {
    val prop = property(id = "p-1", type = PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.MinLength(3), PropertyConstraint.MaxLength(10)))
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = listOf(prop))
    setupInstalledVersion(entityDef)
    stubStore()
    every { appDataPort.validateEntityData(any(), any(), any(), any()) } returns Unit.right()

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 5, "dev-1")

    assertThat(result.isRight()).isTrue()
    val generated = result.completed()
    assertThat(generated).hasSize(5)
    assertThat(store).hasSize(5)
    generated.forEach {
      assertThat(it.installedAppId).isEqualTo(InstalledAppId("installed-1"))
      assertThat(it.entityType).isEqualTo(entityId)
      assertThat(it.userId).isEqualTo("dev-1")
      val value = it.data.getValue("p-1")
      assertThat(value).isNotNull()
      assertThat(value!!.length).isBetween(3, 10)
    }
    verify(exactly = 5) { appDataRepository.save(any()) }
  }

  @Test
  fun `generateTestData never emits null for a non-nullable property`() {
    val prop = property(id = "p-1", type = PropertyType.LONG, nullable = false, constraints = setOf(PropertyConstraint.MinLong(1L), PropertyConstraint.MaxLong(5L)))
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = listOf(prop))
    setupInstalledVersion(entityDef)
    stubStore()
    every { appDataPort.validateEntityData(any(), any(), any(), any()) } returns Unit.right()

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 50, "dev-1")

    assertThat(result.isRight()).isTrue()
    result.completed().forEach {
      val value = it.data.getValue("p-1")!!.toLong()
      assertThat(value).isBetween(1L, 5L)
    }
  }

  @Test
  fun `generateTestData respects a LIST property's size constraints`() {
    val prop = property(
      id = "p-1",
      type = PropertyType.LIST,
      nullable = false,
      constraints = setOf(PropertyConstraint.MinSize(2), PropertyConstraint.MaxSize(3)),
      listItemType = PropertyType.STRING,
      itemConstraints = setOf(PropertyConstraint.MinLength(1), PropertyConstraint.MaxLength(5)),
    )
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = listOf(prop))
    setupInstalledVersion(entityDef)
    stubStore()
    every { appDataPort.validateEntityData(any(), any(), any(), any()) } returns Unit.right()

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 20, "dev-1")

    assertThat(result.isRight()).isTrue()
    result.completed().forEach {
      val items = decodeListValue(it.data.getValue("p-1"))
      assertThat(items.size).isBetween(2, 3)
    }
  }

  @Test
  fun `generateTestData generates a target object first when a REF property has no existing targets`() {
    val targetEntityId = EntityDefinitionId("target-entity")
    val targetProp = property(id = "t-p", type = PropertyType.STRING, nullable = false)
    val targetEntityDef = EntityDefinition(id = targetEntityId, name = "Target", properties = listOf(targetProp))
    val refProp = property(id = "p-1", type = PropertyType.REF, nullable = false, targetEntityId = targetEntityId)
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = listOf(refProp))

    val ver = version(id = "ver-1", appId = "app-1").copy(entityDefinitions = listOf(entityDef, targetEntityDef))
    val installed = installedApp(id = "installed-1", userId = "dev-1", appId = "app-1", isTest = true).copy(installedVersionId = ver.id)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installed
    every { appVersionRepository.findById(ver.id) } returns ver
    stubStore()
    every { appDataPort.validateEntityData(any(), any(), any(), any()) } returns Unit.right()

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 1, "dev-1")

    assertThat(result.isRight()).isTrue()
    val generated = result.completed().single()
    val refValue = generated.data.getValue("p-1")
    assertThat(refValue).isNotNull()
    val targetObject = store.single { it.entityType == targetEntityId }
    assertThat(targetObject.id.value).isEqualTo(refValue)
  }

  @Test
  fun `generateTestData fails with GENERATION_FAILED when the safety net rejects a generated object`() {
    val prop = property(id = "p-1", type = PropertyType.STRING, nullable = false)
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = listOf(prop))
    setupInstalledVersion(entityDef)
    stubStore()
    every { appDataPort.validateEntityData(any(), any(), any(), any()) } returns TestDataGeneratorError.GENERATION_FAILED.left()

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 1, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(TestDataGeneratorError.GENERATION_FAILED)
    assertThat(store).isEmpty()
  }

  @Test
  fun `generateTestData is reproducible for the same seed`() {
    val prop = property(
      id = "p-1",
      type = PropertyType.STRING,
      nullable = true,
      constraints = setOf(PropertyConstraint.MinLength(5), PropertyConstraint.MaxLength(5)),
    )
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = listOf(prop))

    setupInstalledVersion(entityDef, "installed-a")
    stubStore()
    every { appDataPort.validateEntityData(any(), any(), any(), any()) } returns Unit.right()
    val firstRun = service.generateTestData("app-1", "installed-a", "entity-1", 10, "dev-1", seed = 42L).completed()

    store.clear()
    setupInstalledVersion(entityDef, "installed-a")
    val secondRun = service.generateTestData("app-1", "installed-a", "entity-1", 10, "dev-1", seed = 42L).completed()

    assertThat(firstRun.map { it.data["p-1"] }).isEqualTo(secondRun.map { it.data["p-1"] })
  }

  // endregion

  // region real constraint validation (uses the actual PropertyConstraintService/AppDataService, not a mock)

  @Test
  fun `generated values satisfy real constraint validation across all property types`() {
    val target = EntityDefinition(
      id = EntityDefinitionId("target"),
      name = "Target",
      properties = listOf(property(id = "t-name", type = PropertyType.STRING, nullable = false)),
    )
    val nested = property(
      id = "nested-str",
      name = "NestedStr",
      type = PropertyType.STRING,
      nullable = false,
      constraints = setOf(PropertyConstraint.MinLength(2), PropertyConstraint.MaxLength(4)),
    )
    val properties = listOf(
      property(
        id = "long",
        type = PropertyType.LONG,
        nullable = false,
        constraints = setOf(PropertyConstraint.MinLong(10L), PropertyConstraint.MaxLong(100L), PropertyConstraint.StepLong(5L)),
      ),
      property(
        id = "double",
        type = PropertyType.DOUBLE,
        nullable = false,
        constraints = setOf(PropertyConstraint.MinDouble(0.0), PropertyConstraint.MaxDouble(10.0)),
      ),
      property(id = "bool", type = PropertyType.BOOLEAN, nullable = false),
      property(
        id = "string",
        type = PropertyType.STRING,
        nullable = false,
        constraints = setOf(PropertyConstraint.MinLength(2), PropertyConstraint.MaxLength(8), PropertyConstraint.Pattern("[A-Z]{2}-[0-9]{2,4}")),
      ),
      property(
        id = "date",
        type = PropertyType.DATE,
        nullable = false,
        constraints = setOf(PropertyConstraint.MinDate(LocalDate.of(2020, 1, 1)), PropertyConstraint.MaxDate(LocalDate.of(2020, 1, 31))),
      ),
      property(
        id = "time",
        type = PropertyType.TIME,
        nullable = false,
        constraints = setOf(PropertyConstraint.MinTime(LocalTime.of(9, 0)), PropertyConstraint.MaxTime(LocalTime.of(17, 0))),
      ),
      property(
        id = "datetime",
        type = PropertyType.DATETIME,
        nullable = false,
        constraints = setOf(PropertyConstraint.MinDatetime(LocalDateTime.of(2020, 1, 1, 0, 0)), PropertyConstraint.MaxDatetime(LocalDateTime.of(2020, 12, 31, 0, 0))),
      ),
      property(id = "ref", type = PropertyType.REF, nullable = false, targetEntityId = target.id),
      property(
        id = "list",
        type = PropertyType.LIST,
        nullable = false,
        constraints = setOf(PropertyConstraint.MinSize(1), PropertyConstraint.MaxSize(4)),
        listItemType = PropertyType.LONG,
        itemConstraints = setOf(PropertyConstraint.MinLong(0L), PropertyConstraint.MaxLong(9L)),
      ),
      property(id = "object", type = PropertyType.OBJECT, nullable = false, nestedProperties = listOf(nested)),
    )
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = properties)

    val ver = version(id = "ver-1", appId = "app-1").copy(entityDefinitions = listOf(entityDef, target))
    val installed = installedApp(id = "installed-1", userId = "dev-1", appId = "app-1", isTest = true).copy(installedVersionId = ver.id)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installed
    every { appVersionRepository.findById(ver.id) } returns ver
    stubStore()

    val realPropertyConstraint: PropertyConstraintPort = PropertyConstraintService()
    val realAppData = AppDataService(installedAppRepository, appVersionRepository, appDataRepository, realPropertyConstraint)
    val realService = TestDataGeneratorService(appRepository, appVersionRepository, installedAppRepository, appDataRepository, realAppData, outboxPort)

    val result = realService.generateTestData("app-1", "installed-1", "entity-1", 30, "dev-1", seed = 7L)

    assertThat(result.isRight()).isTrue()
    assertThat(result.completed()).hasSize(30)
  }

  // endregion

  // region async / outbox threshold (see docs/dev-tests.md, "Execution model")

  @Test
  fun `generateTestData enqueues via the outbox instead of generating inline above the async threshold`() {
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = emptyList())
    setupInstalledVersion(entityDef)
    val savedInstalledApp = slot<InstalledApp>()
    justRun { installedAppRepository.save(capture(savedInstalledApp)) }
    val enqueued = slot<DomainOutboxEvent.GenerateTestData>()
    justRun { outboxPort.enqueue(capture(enqueued)) }

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 150, "dev-1", seed = 42L)

    assertThat(result.getOrNull()).isEqualTo(TestDataGenerationOutcome.Enqueued)
    assertThat(savedInstalledApp.captured.generatingEntityId).isEqualTo(entityId)
    assertThat(enqueued.captured).isEqualTo(
      DomainOutboxEvent.GenerateTestData(appId = "app-1", installedAppId = "installed-1", entityId = "entity-1", count = 150, developerId = "dev-1", seed = 42L),
    )
    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `generateTestData fails with ALREADY_GENERATING when a run is already queued for the same entity`() {
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = emptyList())
    val ver = version(id = "ver-1", appId = "app-1").copy(entityDefinitions = listOf(entityDef))
    val installed = installedApp(id = "installed-1", userId = "dev-1", appId = "app-1", isTest = true)
      .copy(installedVersionId = ver.id, generatingEntityId = entityId)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installed
    every { appVersionRepository.findById(ver.id) } returns ver

    val result = service.generateTestData("app-1", "installed-1", "entity-1", 150, "dev-1")

    assertThat(result).isEqualTo(TestDataGeneratorError.ALREADY_GENERATING.left())
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  // endregion

  // region isGenerating

  @Test
  fun `isGenerating returns true only while the installation's generatingEntityId matches`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns
      installedApp(id = "installed-1", userId = "dev-1", appId = "app-1", isTest = true).copy(generatingEntityId = entityId)

    assertThat(service.isGenerating("app-1", "installed-1", "entity-1", "dev-1")).isTrue()
    assertThat(service.isGenerating("app-1", "installed-1", "other-entity", "dev-1")).isFalse()
  }

  @Test
  fun `isGenerating returns false when the app is not owned by the developer`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp

    assertThat(service.isGenerating("app-1", "installed-1", "entity-1", "dev-2")).isFalse()
  }

  // endregion

  // region handle (async worker)

  @Test
  fun `handle generates the requested objects in the background and clears generatingEntityId`() {
    val prop = property(id = "p-1", type = PropertyType.STRING, nullable = false, constraints = setOf(PropertyConstraint.MinLength(3), PropertyConstraint.MaxLength(10)))
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = listOf(prop))
    val ver = version(id = "ver-1", appId = "app-1").copy(entityDefinitions = listOf(entityDef))
    val installed = installedApp(id = "installed-1", userId = "dev-1", appId = "app-1", isTest = true)
      .copy(installedVersionId = ver.id, generatingEntityId = entityId)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installed
    every { appVersionRepository.findById(ver.id) } returns ver
    stubStore()
    every { appDataPort.validateEntityData(any(), any(), any(), any()) } returns Unit.right()
    val savedInstalledApp = slot<InstalledApp>()
    justRun { installedAppRepository.save(capture(savedInstalledApp)) }

    val result = service.handle(
      DomainOutboxEvent.GenerateTestData(appId = "app-1", installedAppId = "installed-1", entityId = "entity-1", count = 5, developerId = "dev-1", seed = null),
    )

    assertThat(result.isRight()).isTrue()
    assertThat(store).hasSize(5)
    assertThat(savedInstalledApp.captured.generatingEntityId).isNull()
  }

  @Test
  fun `handle clears generatingEntityId even when generation fails`() {
    val prop = property(id = "p-1", type = PropertyType.STRING, nullable = false)
    val entityDef = EntityDefinition(id = entityId, name = "Entity", properties = listOf(prop))
    val ver = version(id = "ver-1", appId = "app-1").copy(entityDefinitions = listOf(entityDef))
    val installed = installedApp(id = "installed-1", userId = "dev-1", appId = "app-1", isTest = true)
      .copy(installedVersionId = ver.id, generatingEntityId = entityId)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installed
    every { appVersionRepository.findById(ver.id) } returns ver
    stubStore()
    every { appDataPort.validateEntityData(any(), any(), any(), any()) } returns TestDataGeneratorError.GENERATION_FAILED.left()
    val savedInstalledApp = slot<InstalledApp>()
    justRun { installedAppRepository.save(capture(savedInstalledApp)) }

    val result = service.handle(
      DomainOutboxEvent.GenerateTestData(appId = "app-1", installedAppId = "installed-1", entityId = "entity-1", count = 5, developerId = "dev-1", seed = null),
    )

    assertThat(result).isEqualTo(TestDataGeneratorError.GENERATION_FAILED.left())
    assertThat(savedInstalledApp.captured.generatingEntityId).isNull()
  }

  @Test
  fun `handle is a no-op success when the app or test installation is already gone`() {
    every { appRepository.findById(AppId("missing")) } returns null

    val result = service.handle(
      DomainOutboxEvent.GenerateTestData(appId = "missing", installedAppId = "installed-1", entityId = "entity-1", count = 5, developerId = "dev-1", seed = null),
    )

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  // endregion
}
