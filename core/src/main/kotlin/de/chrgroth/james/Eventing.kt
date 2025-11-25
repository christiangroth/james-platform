package de.chrgroth.james

import com.github.glwithu06.semver.Semver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

// TODO #2 maybe switch to Channels and Actors??

// TODO replace with ports and hook adapters to Quarkus event bus

class EventBus {

  private val _events = MutableSharedFlow<DomainEvent>(
    replay = 100, // aka buffer size
  )
  val events = _events.asSharedFlow()

  val eventingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  fun publish(event: DomainEvent) {
    eventingScope.launch {
      withTimeout(50.milliseconds) {
        _events.emit(event)
      }
    }
  }

  inline fun <reified T : DomainEvent> receiver(crossinline handler: (T) -> Unit) {
    eventingScope.launch {
      events.filterIsInstance<T>().collect { handler(it) }
    }
  }
}

sealed class DomainEvent {
  data class UserRegistered(val id: UUID) : DomainEvent()

  data class AppVersionReleased(val appId: UUID, val version: Semver, val datatypesYamlContent: Map<String, String>) :
    DomainEvent()
}
