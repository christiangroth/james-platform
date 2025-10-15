package de.chrgroth.james.platform.domain.user

import io.quarkus.arc.DefaultBean
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty

internal data class DefaultUser(
  val name: String,
  val role: UserRole,
)

@Singleton
internal class UserDomainConfig {

  @ConfigProperty(name = "domain.user.defaultAdminUsername")
  lateinit var adminUsername: String

  @ConfigProperty(name = "domain.user.defaultAdminPassword")
  lateinit var adminPassword: String

  @ConfigProperty(name = "domain.user.defaultUsers")
  lateinit var defaultUsers: List<String>

  @Produces
  @DefaultBean
  internal fun produceDefaultUsers(): List<DefaultUser> {
    return defaultUsers.map { userConfig ->
      val parts = userConfig.split('|')

      val name = parts[0]
      checkNotNull(name) { "Default user name must be provided!" }

      val role = parts[1]
      checkNotNull(role) { "Default user role must be provided!" }

      DefaultUser(
        name = name,
        role = UserRole.valueOf(role)
      )
    }
  }
}
