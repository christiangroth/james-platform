package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant

@ApplicationScoped
class UserRepositoryAdapter(
  private val userDocumentRepository: UserDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : UserRepositoryPort {

  override fun findByUsername(username: Username): User? =
    mongoQueryMetrics.timed("app_user.findByUsername") {
      userDocumentRepository.findById(username.value)?.toDomain()
    }

  override fun findAll(): List<User> =
    mongoQueryMetrics.timed("app_user.findAll") {
      userDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun save(user: User) {
    mongoQueryMetrics.timed("app_user.save") {
      val doc = user.toDocument()
      userDocumentRepository.mongoCollection().replaceOne(
        Filters.eq(ID_FIELD, user.username.value),
        doc,
        ReplaceOptions().upsert(true),
      )
    }
  }

  override fun delete(username: Username) {
    mongoQueryMetrics.timed("app_user.delete") {
      userDocumentRepository.mongoCollection().deleteOne(
        Filters.eq(ID_FIELD, username.value),
      )
    }
  }

  private fun UserDocument.toDomain() = User(
    username = Username(username),
    passwordHash = passwordHash,
    roles = roles.mapNotNull { runCatching { UserRole.valueOf(it) }.getOrNull() }.toSet(),
    createdAt = createdAt,
    lastLoginAt = lastLoginAt,
  )

  private fun User.toDocument() = UserDocument().also { doc ->
    doc.username = username.value
    doc.passwordHash = passwordHash
    doc.roles = roles.map { it.name }.toSet()
    doc.createdAt = createdAt
    doc.lastLoginAt = lastLoginAt
  }

  companion object {
    internal const val ID_FIELD = "_id"
  }
}
