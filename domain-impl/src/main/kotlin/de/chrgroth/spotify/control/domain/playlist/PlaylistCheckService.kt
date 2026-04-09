package de.chrgroth.spotify.control.domain.playlist

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistFixError
import de.chrgroth.spotify.control.domain.playlist.check.PlaylistCheckRunner
import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistCheckNotificationPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mu.KLogging
import java.util.concurrent.TimeUnit

@ApplicationScoped
@Suppress("Unused")
class PlaylistCheckService(
  private val checkRunners: Instance<PlaylistCheckRunner>,
  private val playlistRepository: PlaylistRepositoryPort,
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
  private val dashboardRefresh: DashboardRefreshPort,
  private val notification: PlaylistCheckNotificationPort,
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val outboxPort: OutboxPort,
  private val meterRegistry: MeterRegistry,
) : PlaylistCheckPort {

  override fun handle(event: DomainOutboxEvent.RunPlaylistChecks): Either<DomainError, Unit> {
    val playlist = playlistRepository.findByUserIdAndPlaylistId(event.userId, event.playlistId)
    if (playlist == null) {
      logger.warn { "Playlist ${event.playlistId} not found for user ${event.userId.value}, skipping checks" }
      return Unit.right()
    }

    val allPlaylistInfos = playlistRepository.findByUserId(event.userId)
    val currentPlaylistInfo = allPlaylistInfos.find { it.spotifyPlaylistId == event.playlistId }

    val applicableRunners = checkRunners.filter { it.isApplicable(currentPlaylistInfo) }
    val results = runBlocking {
      applicableRunners
        .map { runner ->
          async(Dispatchers.IO) {
            timedCheck(runner.checkId, event.playlistId) {
              runner.run(event.userId, event.playlistId, playlist, currentPlaylistInfo, allPlaylistInfos)
            }
          }
        }
        .awaitAll()
    }

    results.forEach { check ->
      val previous = playlistCheckRepository.findByCheckId(check.checkId)
      playlistCheckRepository.save(check)
      notifyIfChanged(previous, check)
    }

    val totalViolations = results.sumOf { it.violations.size }
    val status = if (totalViolations == 0) "all passed" else "$totalViolations violation(s)"
    logger.info { "Ran playlist checks for playlist ${event.playlistId} (user ${event.userId.value}): $status" }
    dashboardRefresh.notifyUserPlaylistChecks(event.userId)
    return Unit.right()
  }

  override fun getDisplayNames(): Map<String, String> =
    checkRunners.associate { it.checkId to it.displayName }

  override fun getFixableCheckIds(): Set<String> =
    checkRunners.filter { it.canFix() }.map { it.checkId }.toSet()

  override fun runFix(userId: UserId, playlistId: String, checkType: String): Either<DomainError, Unit> {
    val runner = checkRunners.find { it.checkId == checkType && it.canFix() } ?: run {
      logger.warn { "No fix runner found for checkType $checkType" }
      return PlaylistFixError.FIX_NOT_FOUND.left()
    }
    val playlist = playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) ?: run {
      logger.warn { "Playlist $playlistId not found for user ${userId.value}" }
      return PlaylistFixError.PLAYLIST_NOT_FOUND.left()
    }
    val allPlaylistInfos = playlistRepository.findByUserId(userId)
    val currentPlaylistInfo = allPlaylistInfos.find { it.spotifyPlaylistId == playlistId }
    val accessToken = spotifyAccessToken.getValidAccessToken(userId)
    logger.info { "Running fix '$checkType' for playlist $playlistId (user ${userId.value})" }
    return runner.fix(userId, accessToken, playlistId, playlist, currentPlaylistInfo, allPlaylistInfos).also { result ->
      if (result.isRight()) {
        logger.info { "Fix '$checkType' for playlist $playlistId completed, enqueueing re-check" }
        outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, playlistId))
      }
    }
  }

  private fun notifyIfChanged(previous: AppPlaylistCheck?, current: AppPlaylistCheck) {
    if (previous == null) return
    if (!previous.succeeded && current.succeeded) {
      notification.notifyCheckPassed(current)
    } else if (!previous.succeeded && !current.succeeded && previous.violations != current.violations) {
      notification.notifyViolationsChanged(current)
    }
  }

  private fun <T> timedCheck(checkId: String, playlistId: String, block: () -> T): T {
    val startNs = System.nanoTime()
    val result = block()
    val durationNs = System.nanoTime() - startNs
    Timer.builder("playlist.check")
      .tag("checkId", checkId)
      .tag("playlistId", playlistId)
      .register(meterRegistry)
      .record(durationNs, TimeUnit.NANOSECONDS)
    return result
  }

  companion object : KLogging()
}
