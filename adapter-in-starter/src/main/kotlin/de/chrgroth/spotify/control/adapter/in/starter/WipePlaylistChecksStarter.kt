package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class WipePlaylistChecksStarter(
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
) : Starter {

  override val id = "WipePlaylistChecksStarter-v2"

  override fun execute() {
    logger.info { "Wiping all playlist check documents" }
    playlistCheckRepository.deleteAll()
    logger.info { "All playlist check documents wiped" }
  }

  companion object : KLogging()
}
