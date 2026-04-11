package de.chrgroth.james.platform.domain.playlist.check

import arrow.core.Either
import arrow.core.left
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.PlaylistFixError
import de.chrgroth.james.platform.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.james.platform.domain.model.playlist.Playlist
import de.chrgroth.james.platform.domain.model.playlist.PlaylistInfo
import de.chrgroth.james.platform.domain.model.user.UserId

interface PlaylistCheckRunner {
  val checkId: String
  val displayName: String
  fun isApplicable(playlistInfo: PlaylistInfo?): Boolean = true
  fun run(userId: UserId, playlistId: String, playlist: Playlist, currentPlaylistInfo: PlaylistInfo?, allPlaylistInfos: List<PlaylistInfo>): AppPlaylistCheck
  fun canFix(): Boolean = false
  fun fix(
    userId: UserId,
    playlistId: String,
    playlist: Playlist,
    currentPlaylistInfo: PlaylistInfo?,
    allPlaylistInfos: List<PlaylistInfo>,
  ): Either<DomainError, Unit> = PlaylistFixError.FIX_NOT_FOUND.left()
}
