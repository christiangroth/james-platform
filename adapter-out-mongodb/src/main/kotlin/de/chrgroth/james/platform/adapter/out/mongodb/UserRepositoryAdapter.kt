package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.Date
import java.util.UUID

@ApplicationScoped
class UserRepositoryAdapter(
  private val userDocumentRepository: UserDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : UserRepositoryPort {

  override fun findById(id: UserId): User? =
    mongoQueryMetrics.timed("app_user.findById") {
      userDocumentRepository.findById(id.value)?.toDomain()
    }

  override fun findByUsername(username: Username): User? =
    mongoQueryMetrics.timed("app_user.findByUsername") {
      userDocumentRepository.find(USERNAME_FIELD, username.value).firstResult()?.toDomain()
    }

  override fun findAll(): List<User> =
    mongoQueryMetrics.timed("app_user.findAll") {
      userDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun save(user: User) {
    mongoQueryMetrics.timed("app_user.save") {
      val doc = user.toDocument()
      userDocumentRepository.mongoCollection().replaceOne(
        Filters.eq(ID_FIELD, user.id.value),
        doc,
        ReplaceOptions().upsert(true),
      )
    }
  }

  override fun delete(id: UserId) {
    mongoQueryMetrics.timed("app_user.delete") {
      userDocumentRepository.mongoCollection().deleteOne(
        Filters.eq(ID_FIELD, id.value),
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

  override fun backfillUserIds() {
    mongoQueryMetrics.timed("app_user.backfillUserIds") {
      val rawCollection = userDocumentRepository.mongoCollection().withDocumentClass(org.bson.Document::class.java)
      val missingUsername = Filters.not(Filters.exists(USERNAME_FIELD))
      val documentsWithoutUsername = rawCollection.find(missingUsername).toList()
      documentsWithoutUsername.forEach { doc ->
        val currentId = doc.getString(ID_FIELD) ?: return@forEach
        val newId = UUID.randomUUID().toString()
        val newDoc = org.bson.Document(doc)
        newDoc[ID_FIELD] = newId
        newDoc[USERNAME_FIELD] = currentId
        rawCollection.insertOne(newDoc)
        rawCollection.deleteOne(Filters.eq(ID_FIELD, currentId))
      }
    }
  }

  private fun UserDocument.toDomain() = User(
    id = UserId(id),
    username = Username(username),
    passwordHash = passwordHash,
    roles = roles.mapNotNull { runCatching { UserRole.valueOf(it) }.getOrNull() }.toSet(),
    createdAt = createdAt,
    lastLoginAt = lastLoginAt,
    active = active,
  )

  private fun User.toDocument() = UserDocument().also { doc ->
    doc.id = id.value
    doc.username = username.value
    doc.passwordHash = passwordHash
    doc.roles = roles.map { it.name }.toSet()
    doc.createdAt = createdAt
    doc.lastLoginAt = lastLoginAt
    doc.active = active
  }

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val USERNAME_FIELD = "username"
    private const val CREATED_AT_FIELD = "createdAt"
    private const val ACTIVE_FIELD = "active"
  }
}
