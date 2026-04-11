package de.chrgroth.james.platform.domain.port.out.infra

import de.chrgroth.james.platform.domain.model.infra.CronjobStats

interface CronjobInfoPort {
  fun getCronjobStats(): List<CronjobStats>
}
