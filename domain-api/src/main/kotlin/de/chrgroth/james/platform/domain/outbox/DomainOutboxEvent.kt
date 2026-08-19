package de.chrgroth.james.platform.domain.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxEvent
import de.chrgroth.quarkus.outbox.domain.OutboxEventPriority

sealed interface DomainOutboxEvent : ApplicationOutboxEvent {
  override val partition: DomainOutboxPartition
  override val priority: OutboxEventPriority get() = OutboxEventPriority.MEDIUM
  override val serializePayload: String

  /**
   * Accepts a dry run in the background: saves every valid object, discards invalid ones, deletes the source
   * [de.chrgroth.james.platform.domain.model.imports.ImportJob], and - when [replaceExisting] is set - first
   * deletes every existing instance of the target entity (see `ImportPort.acceptDryRun`).
   * Deduplicated per import job so a repeated/double-submitted accept click does not enqueue a second run.
   * payload = "$importJobId\n$userId\n$replaceExisting"
   */
  data class AcceptDryRun(val importJobId: String, val userId: String, val replaceExisting: Boolean) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$importJobId"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = "$importJobId\n$userId\n$replaceExisting"

    companion object {
      const val KEY = "AcceptDryRun"
      fun fromPayload(payload: String): AcceptDryRun {
        val parts = payload.split("\n")
        return AcceptDryRun(importJobId = parts[0], userId = parts[1], replaceExisting = parts[2].toBoolean())
      }
    }
  }

  /**
   * Deletes an installed app's [de.chrgroth.james.platform.domain.model.app.AppData] and the installed app itself
   * in the background (see `UserAppStorePort.uninstallApp`). Deduplicated per installed app so a repeated/double
   * submitted uninstall click does not enqueue a second run.
   * payload = "$installedAppId\n$userId"
   */
  data class UninstallApp(val installedAppId: String, val userId: String) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$installedAppId"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = "$installedAppId\n$userId"

    companion object {
      const val KEY = "UninstallApp"
      fun fromPayload(payload: String): UninstallApp {
        val parts = payload.split("\n")
        return UninstallApp(installedAppId = parts[0], userId = parts[1])
      }
    }
  }

  /**
   * Deletes an app and all of its [de.chrgroth.james.platform.domain.model.app.AppVersion]s in the background
   * (see `AppManagementPort.deleteApp`). Deduplicated per app so a repeated/double submitted delete click does not
   * enqueue a second run.
   * payload = "$appId\n$developerId"
   */
  data class DeleteApp(val appId: String, val developerId: String) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$appId"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = "$appId\n$developerId"

    companion object {
      const val KEY = "DeleteApp"
      fun fromPayload(payload: String): DeleteApp {
        val parts = payload.split("\n")
        return DeleteApp(appId = parts[0], developerId = parts[1])
      }
    }
  }

  /**
   * Deletes a user's [de.chrgroth.james.platform.domain.model.app.InstalledApp]s (including their
   * [de.chrgroth.james.platform.domain.model.app.AppData]) and the user itself in the background (see
   * `AdminUserManagementService.deleteUser`). Deduplicated per user so a repeated/double submitted delete click
   * does not enqueue a second run.
   * payload = "$userId\n$username"
   */
  data class DeleteUser(val userId: String, val username: String) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$userId"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = "$userId\n$username"

    companion object {
      const val KEY = "DeleteUser"
      fun fromPayload(payload: String): DeleteUser {
        val parts = payload.split("\n")
        return DeleteUser(userId = parts[0], username = parts[1])
      }
    }
  }

  /**
   * Migrates a single installation's [de.chrgroth.james.platform.domain.model.app.AppData] and advances its
   * `installedVersionNumber` in the background, one event per installation (see
   * `AppVersionManagementService.autoUpgradeInstallations`, part of the bulk auto-upgrade triggered by publishing a
   * non-breaking Version). Deduplicated per installation and target version so a repeated publish does not enqueue
   * the same upgrade twice.
   * payload = "$installedAppId\n$appId\n$fromVersionNumber\n$toVersionNumber"
   */
  data class AutoUpgradeInstallation(
    val installedAppId: String,
    val appId: String,
    val fromVersionNumber: String,
    val toVersionNumber: String,
  ) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$installedAppId:$toVersionNumber"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = "$installedAppId\n$appId\n$fromVersionNumber\n$toVersionNumber"

    companion object {
      const val KEY = "AutoUpgradeInstallation"
      fun fromPayload(payload: String): AutoUpgradeInstallation {
        val parts = payload.split("\n")
        return AutoUpgradeInstallation(installedAppId = parts[0], appId = parts[1], fromVersionNumber = parts[2], toVersionNumber = parts[3])
      }
    }
  }

  /**
   * Generates [count] test objects for entity [entityId] into test installation [installedAppId] (belonging to
   * [appId]) in the background instead of blocking the request (see docs/dev-tests.md, "Execution model - synchronous
   * vs. outbox"). Runs on its own [DomainOutboxPartition.TestDataGeneration] partition per ADR 0019's mandatory
   * per-operation partition separation, so a backlog here never delays the other domain operations sharing
   * [DomainOutboxPartition.Domain]. Deduplicated per installation and entity so a repeated/double-submitted generate
   * click while a run for the same entity is already queued does not enqueue a second one.
   * payload = "$appId\n$installedAppId\n$entityId\n$count\n$developerId\n${seed ?: ""}"
   */
  data class GenerateTestData(
    val appId: String,
    val installedAppId: String,
    val entityId: String,
    val count: Int,
    val developerId: String,
    val seed: Long?,
  ) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$installedAppId:$entityId"
    override val partition = DomainOutboxPartition.TestDataGeneration
    override val serializePayload = "$appId\n$installedAppId\n$entityId\n$count\n$developerId\n${seed ?: ""}"

    companion object {
      const val KEY = "GenerateTestData"
      fun fromPayload(payload: String): GenerateTestData {
        val parts = payload.split("\n")
        return GenerateTestData(
          appId = parts[0],
          installedAppId = parts[1],
          entityId = parts[2],
          count = parts[3].toInt(),
          developerId = parts[4],
          seed = parts[5].takeIf { it.isNotEmpty() }?.toLong(),
        )
      }
    }
  }

  companion object {
    val allKeys: List<String> = listOf(AcceptDryRun.KEY, UninstallApp.KEY, DeleteApp.KEY, DeleteUser.KEY, AutoUpgradeInstallation.KEY, GenerateTestData.KEY)

    fun fromKey(key: String, payload: String): DomainOutboxEvent = when (key) {
      AcceptDryRun.KEY -> AcceptDryRun.fromPayload(payload)
      UninstallApp.KEY -> UninstallApp.fromPayload(payload)
      DeleteApp.KEY -> DeleteApp.fromPayload(payload)
      DeleteUser.KEY -> DeleteUser.fromPayload(payload)
      AutoUpgradeInstallation.KEY -> AutoUpgradeInstallation.fromPayload(payload)
      GenerateTestData.KEY -> GenerateTestData.fromPayload(payload)
      else -> throw IllegalArgumentException("Unknown outbox event type: $key")
    }
  }
}
