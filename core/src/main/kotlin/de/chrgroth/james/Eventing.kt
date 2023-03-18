package de.chrgroth.james

import com.github.glwithu06.semver.Semver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

object EventBus {

    private val _events = MutableSharedFlow<DomainEvent>()
    val events = _events.asSharedFlow()

    suspend fun publish(event: DomainEvent) = _events.emit(event)
}

// TODO #29 emit them somewhere
sealed class DomainEvent {
    data class UserRegistered(val id: UUID): DomainEvent()
    data class UserDeactivated(val id: UUID): DomainEvent()
    data class UserActivated(val id: UUID): DomainEvent()
    data class UserDeleted(val id: UUID): DomainEvent()

    data class AppVersionReleased(val appId: UUID, val version: Semver): DomainEvent()
    data class AppVersionDeleted(val appId: UUID, val version: Semver): DomainEvent()
}
