package de.chrgroth.james

import com.github.glwithu06.semver.Semver
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class EventingTests {

  private val eventBus = EventBus()

  @Test
  fun `published events gets consumed`() {

    var gotEvent = false
    eventBus.receiver<DomainEvent.UserRegistered> {
      gotEvent = true
    }

    eventBus.publish(DomainEvent.UserRegistered(UUID.randomUUID()))
    runBlocking { delay(1.seconds) }
    assertThat(gotEvent).isTrue
  }

  @Test
  fun `no side effects if no matching receiver`() {

    var gotEvent = false
    eventBus.receiver<DomainEvent.UserRegistered> {
      gotEvent = true
    }

    eventBus.publish(DomainEvent.AppVersionReleased(UUID.randomUUID(), Semver("1.0.0"), emptyMap()))
    runBlocking { delay(1.seconds) }
    assertThat(gotEvent).isFalse
  }
}
