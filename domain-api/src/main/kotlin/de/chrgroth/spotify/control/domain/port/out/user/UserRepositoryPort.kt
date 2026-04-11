package de.chrgroth.spotify.control.domain.port.out.user

import de.chrgroth.spotify.control.domain.model.user.User

interface UserRepositoryPort {
  fun findByUsername(username: String): User?
  fun findAll(): List<User>
  fun save(user: User)
}
