package de.chrgroth.james.platform.adapter.`in`.outbox

import de.chrgroth.james.platform.domain.outbox.DomainOutboxPartition
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxEvent
import de.chrgroth.quarkus.outbox.domain.DispatchResult
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DomainOutboxTaskDispatcherTests {

  private val dispatcher = DomainOutboxTaskDispatcher()

  @Test
  fun `all partitions returns the single domain partition`() {
    assertThat(dispatcher.getAllPartitions()).containsExactly(DomainOutboxPartition.Domain)
  }

  @Test
  fun `deserialize throws for any event type since none are registered yet`() {
    assertThatThrownBy { dispatcher.deserialize(DomainOutboxPartition.Domain, "unknown-event", "{}") }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `dispatch returns success for an event with no registered handler`() {
    val event = mockk<ApplicationOutboxEvent>()
    every { event.key } returns "unhandled-event"

    val result = dispatcher.dispatch(event)

    assertThat(result).isEqualTo(DispatchResult.Success)
  }
}
