package de.chrgroth.james.platform.domain.port.out.user

import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.Username

interface UserRepositoryPort {
  fun findById(id: UserId): User?
  fun findByUsername(username: Username): User?
  fun findAll(): List<User>
  fun save(user: User)
  fun delete(id: UserId)
  fun backfillCreatedAtAndActive()
  fun backfillUserIds()
}
