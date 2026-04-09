package de.chrgroth.spotify.control.domain.port.out.catalog

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumSyncResult
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.user.UserId

interface SpotifyCatalogPort {
  fun getArtist(userId: UserId, accessToken: AccessToken, artistId: String): Either<DomainError, AppArtist?>
  fun getAlbum(userId: UserId, accessToken: AccessToken, albumId: String): Either<DomainError, AlbumSyncResult>
  fun getArtistAlbumIds(userId: UserId, accessToken: AccessToken, artistId: String): Either<DomainError, List<String>>
}
