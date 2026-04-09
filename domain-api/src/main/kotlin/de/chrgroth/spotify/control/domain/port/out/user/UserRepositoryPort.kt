package de.chrgroth.spotify.control.domain.port.out.user

import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId

interface UserRepositoryPort {
  fun findById(spotifyUserId: UserId): User?
  fun findAll(): List<User>
  fun upsert(user: User)
}
