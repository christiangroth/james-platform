package de.chrgroth.james.platform.adapter.`in`.outbox

import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AggregationError
import de.chrgroth.james.platform.domain.error.AppError
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.error.UserAdminError
import de.chrgroth.james.platform.domain.error.UserAppStoreError
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.outbox.DomainOutboxPartition
import de.chrgroth.james.platform.domain.error.TestDataGeneratorError
import de.chrgroth.james.platform.domain.port.`in`.app.AggregationPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.TestDataGeneratorPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import de.chrgroth.james.platform.domain.port.`in`.user.AdminUserManagementPort
import de.chrgroth.quarkus.outbox.domain.DispatchResult
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DomainOutboxTaskDispatcherTests {

  private val importPort = mockk<ImportPort>()
  private val userAppStorePort = mockk<UserAppStorePort>()
  private val appManagementPort = mockk<AppManagementPort>()
  private val adminUserManagementPort = mockk<AdminUserManagementPort>()
  private val appVersionManagementPort = mockk<AppVersionManagementPort>()
  private val testDataGeneratorPort = mockk<TestDataGeneratorPort>()
  private val aggregationPort = mockk<AggregationPort>()
  private val dispatcher = DomainOutboxTaskDispatcher(
    importPort,
    userAppStorePort,
    appManagementPort,
    adminUserManagementPort,
    appVersionManagementPort,
    testDataGeneratorPort,
    aggregationPort,
  )

  private val event = DomainOutboxEvent.AcceptDryRun(importJobId = "job-1", userId = "user-1", replaceExisting = true)
  private val uninstallEvent = DomainOutboxEvent.UninstallApp(installedAppId = "inst-1", userId = "user-1")
  private val deleteAppEvent = DomainOutboxEvent.DeleteApp(appId = "app-1", developerId = "dev-1")
  private val deleteUserEvent = DomainOutboxEvent.DeleteUser(userId = "user-1", username = "user")
  private val autoUpgradeEvent =
    DomainOutboxEvent.AutoUpgradeInstallation(installedAppId = "inst-1", appId = "app-1", fromVersionNumber = "1.0.0", toVersionNumber = "1.1.0")
  private val generateTestDataEvent =
    DomainOutboxEvent.GenerateTestData(appId = "app-1", installedAppId = "inst-1", entityId = "entity-1", count = 150, developerId = "dev-1", seed = 42L)
  private val recomputeAggregationEvent = DomainOutboxEvent.RecomputeAggregation(installedAppId = "inst-1", aggregationDefinitionId = "agg-1")

  @Test
  fun `all partitions returns the domain, test data generation and aggregation recompute partitions`() {
    assertThat(dispatcher.getAllPartitions())
      .containsExactly(DomainOutboxPartition.Domain, DomainOutboxPartition.TestDataGeneration, DomainOutboxPartition.AggregationRecompute)
  }

  @Test
  fun `deserialize throws for an unknown event type`() {
    assertThatThrownBy { dispatcher.deserialize(DomainOutboxPartition.Domain, "unknown-event", "{}") }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `deserialize reconstructs a known event type from its serialized payload`() {
    val deserialized = dispatcher.deserialize(DomainOutboxPartition.Domain, DomainOutboxEvent.AcceptDryRun.KEY, event.serializePayload)

    assertThat(deserialized).isEqualTo(event)
  }

  @Test
  fun `dispatch routes AcceptDryRun to ImportPort#handle and returns success when it succeeds`() {
    every { importPort.handle(event) } returns Unit.right()

    val result = dispatcher.dispatch(event)

    assertThat(result).isEqualTo(DispatchResult.Success)
  }

  @Test
  fun `dispatch returns failed when the domain handler reports an error`() {
    every { importPort.handle(event) } returns ImportError.INSTALLED_APP_NOT_FOUND.left()

    val result = dispatcher.dispatch(event)

    assertThat(result).isInstanceOf(DispatchResult.Failed::class.java)
  }

  @Test
  fun `dispatch returns failed when the domain handler throws unexpectedly`() {
    every { importPort.handle(event) } throws IllegalStateException("boom")

    val result = dispatcher.dispatch(event)

    assertThat(result).isInstanceOf(DispatchResult.Failed::class.java)
  }

  @Test
  fun `deserialize reconstructs an UninstallApp event from its serialized payload`() {
    val deserialized = dispatcher.deserialize(DomainOutboxPartition.Domain, DomainOutboxEvent.UninstallApp.KEY, uninstallEvent.serializePayload)

    assertThat(deserialized).isEqualTo(uninstallEvent)
  }

  @Test
  fun `dispatch routes UninstallApp to UserAppStorePort#handle and returns success when it succeeds`() {
    every { userAppStorePort.handle(uninstallEvent) } returns Unit.right()

    val result = dispatcher.dispatch(uninstallEvent)

    assertThat(result).isEqualTo(DispatchResult.Success)
  }

  @Test
  fun `dispatch returns failed when the UninstallApp handler reports an error`() {
    every { userAppStorePort.handle(uninstallEvent) } returns UserAppStoreError.INSTALLED_APP_NOT_FOUND.left()

    val result = dispatcher.dispatch(uninstallEvent)

    assertThat(result).isInstanceOf(DispatchResult.Failed::class.java)
  }

  @Test
  fun `deserialize reconstructs a DeleteApp event from its serialized payload`() {
    val deserialized = dispatcher.deserialize(DomainOutboxPartition.Domain, DomainOutboxEvent.DeleteApp.KEY, deleteAppEvent.serializePayload)

    assertThat(deserialized).isEqualTo(deleteAppEvent)
  }

  @Test
  fun `dispatch routes DeleteApp to AppManagementPort#handle and returns success when it succeeds`() {
    every { appManagementPort.handle(deleteAppEvent) } returns Unit.right()

    val result = dispatcher.dispatch(deleteAppEvent)

    assertThat(result).isEqualTo(DispatchResult.Success)
  }

  @Test
  fun `dispatch returns failed when the DeleteApp handler reports an error`() {
    every { appManagementPort.handle(deleteAppEvent) } returns AppError.APP_NOT_FOUND.left()

    val result = dispatcher.dispatch(deleteAppEvent)

    assertThat(result).isInstanceOf(DispatchResult.Failed::class.java)
  }

  @Test
  fun `deserialize reconstructs a DeleteUser event from its serialized payload`() {
    val deserialized = dispatcher.deserialize(DomainOutboxPartition.Domain, DomainOutboxEvent.DeleteUser.KEY, deleteUserEvent.serializePayload)

    assertThat(deserialized).isEqualTo(deleteUserEvent)
  }

  @Test
  fun `dispatch routes DeleteUser to AdminUserManagementPort#handle and returns success when it succeeds`() {
    every { adminUserManagementPort.handle(deleteUserEvent) } returns Unit.right()

    val result = dispatcher.dispatch(deleteUserEvent)

    assertThat(result).isEqualTo(DispatchResult.Success)
  }

  @Test
  fun `dispatch returns failed when the DeleteUser handler reports an error`() {
    every { adminUserManagementPort.handle(deleteUserEvent) } returns UserAdminError.USER_NOT_FOUND.left()

    val result = dispatcher.dispatch(deleteUserEvent)

    assertThat(result).isInstanceOf(DispatchResult.Failed::class.java)
  }

  @Test
  fun `deserialize reconstructs an AutoUpgradeInstallation event from its serialized payload`() {
    val deserialized = dispatcher.deserialize(DomainOutboxPartition.Domain, DomainOutboxEvent.AutoUpgradeInstallation.KEY, autoUpgradeEvent.serializePayload)

    assertThat(deserialized).isEqualTo(autoUpgradeEvent)
  }

  @Test
  fun `dispatch routes AutoUpgradeInstallation to AppVersionManagementPort#handle and returns success when it succeeds`() {
    every { appVersionManagementPort.handle(autoUpgradeEvent) } returns Unit.right()

    val result = dispatcher.dispatch(autoUpgradeEvent)

    assertThat(result).isEqualTo(DispatchResult.Success)
  }

  @Test
  fun `dispatch returns failed when the AutoUpgradeInstallation handler reports an error`() {
    every { appVersionManagementPort.handle(autoUpgradeEvent) } returns AppVersionError.VERSION_NOT_FOUND.left()

    val result = dispatcher.dispatch(autoUpgradeEvent)

    assertThat(result).isInstanceOf(DispatchResult.Failed::class.java)
  }

  @Test
  fun `deserialize reconstructs a GenerateTestData event from its serialized payload`() {
    val deserialized = dispatcher.deserialize(DomainOutboxPartition.TestDataGeneration, DomainOutboxEvent.GenerateTestData.KEY, generateTestDataEvent.serializePayload)

    assertThat(deserialized).isEqualTo(generateTestDataEvent)
  }

  @Test
  fun `dispatch routes GenerateTestData to TestDataGeneratorPort#handle and returns success when it succeeds`() {
    every { testDataGeneratorPort.handle(generateTestDataEvent) } returns Unit.right()

    val result = dispatcher.dispatch(generateTestDataEvent)

    assertThat(result).isEqualTo(DispatchResult.Success)
  }

  @Test
  fun `dispatch returns failed when the GenerateTestData handler reports an error`() {
    every { testDataGeneratorPort.handle(generateTestDataEvent) } returns TestDataGeneratorError.GENERATION_FAILED.left()

    val result = dispatcher.dispatch(generateTestDataEvent)

    assertThat(result).isInstanceOf(DispatchResult.Failed::class.java)
  }

  @Test
  fun `deserialize reconstructs a RecomputeAggregation event from its serialized payload`() {
    val deserialized =
      dispatcher.deserialize(DomainOutboxPartition.AggregationRecompute, DomainOutboxEvent.RecomputeAggregation.KEY, recomputeAggregationEvent.serializePayload)

    assertThat(deserialized).isEqualTo(recomputeAggregationEvent)
  }

  @Test
  fun `dispatch routes RecomputeAggregation to AggregationPort#handle and returns success when it succeeds`() {
    every { aggregationPort.handle(recomputeAggregationEvent) } returns Unit.right()

    val result = dispatcher.dispatch(recomputeAggregationEvent)

    assertThat(result).isEqualTo(DispatchResult.Success)
  }

  @Test
  fun `dispatch returns failed when the RecomputeAggregation handler reports an error`() {
    every { aggregationPort.handle(recomputeAggregationEvent) } returns AggregationError.INSTALLED_APP_NOT_FOUND.left()

    val result = dispatcher.dispatch(recomputeAggregationEvent)

    assertThat(result).isInstanceOf(DispatchResult.Failed::class.java)
  }
}
