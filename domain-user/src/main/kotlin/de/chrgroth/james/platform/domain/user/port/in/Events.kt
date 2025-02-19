package de.chrgroth.james.platform.domain.user.port.`in`

const val EVENT_TOPIC_TO_DOMAIN_USER = "to-domain-user"

sealed interface DomainUserEvents {
  data object PersistenceInitialized : DomainUserEvents
}
