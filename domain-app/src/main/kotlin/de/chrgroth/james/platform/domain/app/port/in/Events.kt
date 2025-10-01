package de.chrgroth.james.platform.domain.app.port.`in`

const val EVENT_TOPIC_TO_DOMAIN_APP = "to-domain-app"

sealed interface DomainAppEvents {
  data object PersistenceInitialized : DomainAppEvents
}
