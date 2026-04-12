package de.chrgroth.james.platform.domain.model.infra

data class ConfigurationStats(
  val configEntries: List<ConfigEntry>,
  val envEntries: List<ConfigEntry>,
)
