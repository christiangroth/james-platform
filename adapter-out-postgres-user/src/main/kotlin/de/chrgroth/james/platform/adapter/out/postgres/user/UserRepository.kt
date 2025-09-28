package de.chrgroth.james.platform.adapter.out.postgres.user

import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.adapter.out.postgres.user.generated.tables.Users.USERS
import de.chrgroth.james.platform.adapter.out.postgres.user.generated.tables.records.UsersRecord
import de.chrgroth.james.platform.domain.user.User
import de.chrgroth.james.platform.domain.user.UserId
import de.chrgroth.james.platform.domain.user.port.out.UserPersistencePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Named
import java.util.*
import org.jooq.DSLContext
import org.jooq.RecordMapper
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import javax.sql.DataSource

@ApplicationScoped
@Suppress("Unused")
class UserDatabaseConfig {

  @Inject
  @Named("users")
  lateinit var usersDataSource: DataSource

  @Produces
  @ApplicationScoped
  @Named("users")
  fun usersDslContext(): DSLContext {
    return DSL.using(usersDataSource, SQLDialect.POSTGRES)
  }
}

// TODO Error handling repository vs adapter
@ApplicationScoped
class UserRepository @Inject constructor(
  @Named("users") private val dsl: DSLContext,
) : UserPersistencePort {

  private val userMapper = RecordMapper<UsersRecord, User> { record ->
    User(
      id = UserId(record.id),
      username = record.username,
      passwordHash = record.passwordHash,
      passwordStatus = record.passwordStatus,
      roles = record.roles.toSet(),
      status = record.status,
      statusReason = record.statusReason,
      deactivationCounter = record.deactivationCounter
    )
  }

  override fun byId(id: UserId) = try {
    dsl.selectFrom(USERS)
      .where(USERS.ID.eq(id.value))
      .fetchOptional()
      .map { it.map(userMapper) }
      .valid()
  } catch (e: Exception) {
    e.toDomainError().invalid()
  }

  override fun byUsername(username: String) = try {
    dsl.selectFrom(USERS)
      .where(USERS.USERNAME.eq(username))
      .fetchOptional()
      .map { it.map(userMapper) }
      .valid()
  } catch (e: Exception) {
    e.toDomainError().invalid()
  }

  override fun all() = try {
    dsl.selectFrom(USERS)
      .fetch()
      .map(userMapper)
      .toSet()
      .valid()
  } catch (e: Exception) {
    e.toDomainError().invalid()
  }

  override fun create(user: User) = try {
    dsl.insertInto(USERS)
      .set(createRecord(user))
      .execute()
    Unit.valid()
  } catch (e: Exception) {
    e.toDomainError().invalid()
  }

  override fun update(user: User) = try {
    dsl.update(USERS)
      .set(createRecord(user))
      .where(USERS.ID.eq(user.id.value))
      .execute()
    Unit.valid()
  } catch (e: Exception) {
    e.toDomainError().invalid()
  }

  override fun delete(id: UserId) = try {
    dsl.deleteFrom(USERS)
      .where(USERS.ID.eq(id.value))
      .execute()
    Unit.valid()
  } catch (e: Exception) {
    e.toDomainError().invalid()
  }

  private fun createRecord(user: User): UsersRecord {
    return UsersRecord().apply {
      id = user.id.value
      username = user.username
      passwordHash = user.passwordHash
      passwordStatus = user.passwordStatus
      roles = user.roles.toTypedArray()
      status = user.status
      statusReason = user.statusReason
      deactivationCounter = user.deactivationCounter
    }
  }

  private fun <T> T?.valid() = this.validNel()
  private fun <T> DomainError.invalid() = this.invalidNel<T>()
  private fun Exception.toDomainError() = DomainError(
    code = "DATABASE_ERROR",
    errorMessage = this.message ?: "An unknown database error occurred"
  )
}
