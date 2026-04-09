package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserRole
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant

@ApplicationScoped
class UserRepositoryAdapter(
  private val userDocumentRepository: UserDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : UserRepositoryPort {

  override fun findByUsername(username: String): User? =
    mongoQueryMetrics.timed("app_user.findByUsername") {
      userDocumentRepository.findById(username)?.toDomain()
    }

  override fun findAll(): List<User> =
    mongoQueryMetrics.timed("app_user.findAll") {
      userDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun save(user: User) {
    mongoQueryMetrics.timed("app_user.save") {
      val doc = user.toDocument()
      userDocumentRepository.mongoCollection().replaceOne(
        Filters.eq(ID_FIELD, user.username),
        doc,
        ReplaceOptions().upsert(true),
      )
    }
  }

  private fun UserDocument.toDomain() = User(
    username = username,
    passwordHash = passwordHash,
    roles = roles.mapNotNull { runCatching { UserRole.valueOf(it) }.getOrNull() }.toSet(),
    createdAt = createdAt,
  )

  private fun User.toDocument() = UserDocument().also { doc ->
    doc.username = username
    doc.passwordHash = passwordHash
    doc.roles = roles.map { it.name }.toSet()
    doc.createdAt = createdAt
  }

  companion object {
    internal const val ID_FIELD = "_id"
  }
}
