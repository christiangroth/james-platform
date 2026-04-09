package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class UserRepositoryAdapter(
  private val userDocumentRepository: UserDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : UserRepositoryPort {

  override fun findById(spotifyUserId: UserId): User? =
    mongoQueryMetrics.timed("app_user.findById") {
      userDocumentRepository.findById(spotifyUserId.value)?.toDomain()
    }

  override fun findAll(): List<User> =
    mongoQueryMetrics.timed("app_user.findAll") {
      userDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun upsert(user: User) {
    val now = java.time.Instant.now()
    mongoQueryMetrics.timed("app_user.upsert") {
      userDocumentRepository.mongoCollection().updateOne(
        Filters.eq(ID_FIELD, user.spotifyUserId.value),
        Updates.combine(
          Updates.set(DISPLAY_NAME_FIELD, user.displayName),
          Updates.set(ENCRYPTED_ACCESS_TOKEN_FIELD, user.encryptedAccessToken),
          Updates.set(ENCRYPTED_REFRESH_TOKEN_FIELD, user.encryptedRefreshToken),
          Updates.set(TOKEN_EXPIRES_AT_FIELD, user.tokenExpiresAt.toJavaInstant()),
          Updates.set(LAST_LOGIN_AT_FIELD, user.lastLoginAt.toJavaInstant()),
          Updates.setOnInsert(CREATED_AT_FIELD, now),
        ),
        UpdateOptions().upsert(true),
      )
    }
  }

  private fun UserDocument.toDomain() = User(
    spotifyUserId = UserId(spotifyUserId),
    displayName = displayName,
    encryptedAccessToken = encryptedAccessToken,
    encryptedRefreshToken = encryptedRefreshToken,
    tokenExpiresAt = tokenExpiresAt.toKotlinInstant(),
    lastLoginAt = lastLoginAt.toKotlinInstant(),
  )

  companion object {
    internal const val ID_FIELD = "_id"
    internal const val DISPLAY_NAME_FIELD = "displayName"
    internal const val ENCRYPTED_ACCESS_TOKEN_FIELD = "encryptedAccessToken"
    internal const val ENCRYPTED_REFRESH_TOKEN_FIELD = "encryptedRefreshToken"
    internal const val TOKEN_EXPIRES_AT_FIELD = "tokenExpiresAt"
    internal const val LAST_LOGIN_AT_FIELD = "lastLoginAt"
    internal const val CREATED_AT_FIELD = "createdAt"
  }
}
