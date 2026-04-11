package de.chrgroth.james.platform.domain.port.out.user

import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.Username

interface UserRepositoryPort {
  fun findByUsername(username: Username): User?
  fun findAll(): List<User>
  fun save(user: User)
}
