package de.chrgroth.james.platform.adapter.`in`.outbox

import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.outbox.DomainOutboxPartition
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import de.chrgroth.quarkus.outbox.domain.DispatchResult
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DomainOutboxTaskDispatcherTests {

  private val importPort = mockk<ImportPort>()
  private val dispatcher = DomainOutboxTaskDispatcher(importPort)

  private val event = DomainOutboxEvent.AcceptDryRun(importJobId = "job-1", userId = "user-1", replaceExisting = true)

  @Test
  fun `all partitions returns the single domain partition`() {
    assertThat(dispatcher.getAllPartitions()).containsExactly(DomainOutboxPartition.Domain)
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
}
