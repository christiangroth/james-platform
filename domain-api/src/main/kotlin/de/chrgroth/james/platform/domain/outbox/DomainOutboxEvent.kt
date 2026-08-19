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

  companion object {
    val allKeys: List<String> = listOf(AcceptDryRun.KEY, UninstallApp.KEY, DeleteApp.KEY, DeleteUser.KEY)

    fun fromKey(key: String, payload: String): DomainOutboxEvent = when (key) {
      AcceptDryRun.KEY -> AcceptDryRun.fromPayload(payload)
      UninstallApp.KEY -> UninstallApp.fromPayload(payload)
      DeleteApp.KEY -> DeleteApp.fromPayload(payload)
      DeleteUser.KEY -> DeleteUser.fromPayload(payload)
      else -> throw IllegalArgumentException("Unknown outbox event type: $key")
    }
  }
}
