package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.Date

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

  override fun backfillCreatedAtAndActive() {
    mongoQueryMetrics.timed("app_user.backfillCreatedAtAndActive") {
      val collection = userDocumentRepository.mongoCollection()
      val missingField = Filters.not(Filters.exists(CREATED_AT_FIELD))
      collection.updateMany(missingField, Updates.set(CREATED_AT_FIELD, Date.from(Instant.EPOCH)))
      val missingActive = Filters.not(Filters.exists(ACTIVE_FIELD))
      collection.updateMany(missingActive, Updates.set(ACTIVE_FIELD, true))
    }
  }

  private fun UserDocument.toDomain() = User(
    username = Username(username),
    passwordHash = passwordHash,
    roles = roles.mapNotNull { runCatching { UserRole.valueOf(it) }.getOrNull() }.toSet(),
    createdAt = createdAt,
    lastLoginAt = lastLoginAt,
    active = active,
  )

  private fun User.toDocument() = UserDocument().also { doc ->
    doc.username = username.value
    doc.passwordHash = passwordHash
    doc.roles = roles.map { it.name }.toSet()
    doc.createdAt = createdAt
    doc.lastLoginAt = lastLoginAt
    doc.active = active
  }

  companion object {
    internal const val ID_FIELD = "_id"
    private const val CREATED_AT_FIELD = "createdAt"
    private const val ACTIVE_FIELD = "active"
  }
}
