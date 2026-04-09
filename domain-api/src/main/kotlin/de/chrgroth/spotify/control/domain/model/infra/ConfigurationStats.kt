package de.chrgroth.spotify.control.domain.model.infra

data class ConfigurationStats(
  val configEntries: List<ConfigEntry>,
  val envEntries: List<ConfigEntry>,
)
