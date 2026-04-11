package de.chrgroth.james.platform.domain.port.out.user

import de.chrgroth.james.platform.domain.model.user.User

interface UserRepositoryPort {
  fun findByUsername(username: String): User?
  fun findAll(): List<User>
  fun save(user: User)
}
