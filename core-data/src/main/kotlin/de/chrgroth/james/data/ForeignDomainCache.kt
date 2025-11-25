package de.chrgroth.james.data

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.DomainEvent
import de.chrgroth.james.EventBus
import java.util.UUID

// TODO #18 bulk insert on application startup
class AppVersionDatatypesSchemaContentCache(private val eventBus: EventBus) {
  private data class CacheKey(private val appId: UUID, private val appVersion: Semver, private val datatypeName: String)

  private val data = mutableMapOf<CacheKey, String>()

  init {
    eventBus.receiver<DomainEvent.AppVersionReleased> { event ->
      event.datatypesYamlContent.forEach {
        add(CacheKey(event.appId, event.version, it.key), it.value)
      }
    }
  }

  private fun add(key: CacheKey, datatypeSchemaContent: String) {
    data.put(key, datatypeSchemaContent)
  }

  fun get(appId: UUID, appVersion: Semver, datatypeName: String): String? =
    data[CacheKey(appId, appVersion, datatypeName)]
}
