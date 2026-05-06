package de.chrgroth.james.platform.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.james.platform.domain.port.`in`.user.MonitoringUserInitializerPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class MonitoringUserInitializerStarter(
  private val monitoringUserInitializerService: MonitoringUserInitializerPort,
) : Starter {

  override val id = "MonitoringUserInitializerStarter-v1"

  override fun execute() {
    monitoringUserInitializerService.initializeMonitoringUser()
  }
}
