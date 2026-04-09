package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.infra.ConfigurationStats

interface ConfigurationInfoPort {
  fun getConfigurationStats(): ConfigurationStats
}
