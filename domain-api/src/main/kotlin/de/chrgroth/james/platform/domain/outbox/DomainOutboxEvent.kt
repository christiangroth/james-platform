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

  companion object {
    val allKeys: List<String> = listOf(AcceptDryRun.KEY)

    fun fromKey(key: String, payload: String): DomainOutboxEvent = when (key) {
      AcceptDryRun.KEY -> AcceptDryRun.fromPayload(payload)
      else -> throw IllegalArgumentException("Unknown outbox event type: $key")
    }
  }
}
