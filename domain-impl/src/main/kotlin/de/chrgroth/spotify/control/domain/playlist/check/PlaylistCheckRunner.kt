package de.chrgroth.spotify.control.domain.playlist.check

import arrow.core.Either
import arrow.core.left
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistFixError
import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId

interface PlaylistCheckRunner {
  val checkId: String
  val displayName: String
  fun isApplicable(playlistInfo: PlaylistInfo?): Boolean = true
  fun run(userId: UserId, playlistId: String, playlist: Playlist, currentPlaylistInfo: PlaylistInfo?, allPlaylistInfos: List<PlaylistInfo>): AppPlaylistCheck
  fun canFix(): Boolean = false
  fun fix(
    userId: UserId,
    accessToken: AccessToken,
    playlistId: String,
    playlist: Playlist,
    currentPlaylistInfo: PlaylistInfo?,
    allPlaylistInfos: List<PlaylistInfo>,
  ): Either<DomainError, Unit> = PlaylistFixError.FIX_NOT_FOUND.left()
}
