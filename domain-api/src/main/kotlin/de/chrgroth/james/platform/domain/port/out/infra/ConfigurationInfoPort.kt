package de.chrgroth.james.platform.domain.port.out.infra

import de.chrgroth.james.platform.domain.model.infra.ConfigurationStats

interface ConfigurationInfoPort {
  fun getConfigurationStats(): ConfigurationStats
}
