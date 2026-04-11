package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.port.`in`.user.AdminUserInitializerPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class AdminUserInitializerStarter(
  private val adminUserInitializerService: AdminUserInitializerPort,
) : Starter {

  override val id = "AdminUserInitializerStarter-v1"

  override fun execute() {
    adminUserInitializerService.initializeAdminUser()
  }
}
