package de.chrgroth.james.platform.adapter.out.postgres.user

import de.chrgroth.james.platform.domain.user.PasswordStatus
import de.chrgroth.james.platform.domain.user.UserRole
import de.chrgroth.james.platform.domain.user.UserStatus
import org.jooq.Converter

class PasswordStatusConverter : Converter<Any, PasswordStatus> {
  override fun from(databaseObject: Any?): PasswordStatus? {
    return when (databaseObject?.toString()?.uppercase()) {
      "ONE_TIME" -> PasswordStatus.ONE_TIME
      "PERMANENT" -> PasswordStatus.PERMANENT
      else -> null
    }
  }

  override fun to(userObject: PasswordStatus?): Any? {
    return userObject?.name
  }

  override fun fromType(): Class<Any> = Any::class.java
  override fun toType(): Class<PasswordStatus> = PasswordStatus::class.java
}

class UserRoleConverter : Converter<Any, UserRole> {
  override fun from(databaseObject: Any?): UserRole? {
    return when (databaseObject?.toString()?.uppercase()) {
      "ADMIN" -> UserRole.ADMIN
      "DEVELOPER" -> UserRole.DEVELOPER
      "USER" -> UserRole.USER
      else -> null
    }
  }

  override fun to(userObject: UserRole?): Any? {
    return userObject?.name
  }

  override fun fromType(): Class<Any> = Any::class.java
  override fun toType(): Class<UserRole> = UserRole::class.java
}

class UserStatusConverter : Converter<Any, UserStatus> {
  override fun from(databaseObject: Any?): UserStatus? {
    return when (databaseObject?.toString()?.uppercase()) {
      "ACTIVE" -> UserStatus.ACTIVE
      "INACTIVE" -> UserStatus.INACTIVE
      else -> null
    }
  }

  override fun to(userObject: UserStatus?): Any? {
    return userObject?.name
  }

  override fun fromType(): Class<Any> = Any::class.java
  override fun toType(): Class<UserStatus> = UserStatus::class.java
}
