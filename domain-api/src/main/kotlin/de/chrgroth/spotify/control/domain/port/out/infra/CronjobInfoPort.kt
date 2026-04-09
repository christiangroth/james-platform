package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.infra.CronjobStats

interface CronjobInfoPort {
  fun getCronjobStats(): List<CronjobStats>
}
