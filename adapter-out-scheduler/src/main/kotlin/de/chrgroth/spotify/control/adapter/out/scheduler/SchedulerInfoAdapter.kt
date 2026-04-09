package de.chrgroth.spotify.control.adapter.out.scheduler

import de.chrgroth.spotify.control.domain.model.infra.CronjobStats
import de.chrgroth.spotify.control.domain.port.out.infra.CronjobInfoPort
import io.quarkus.scheduler.Scheduled
import io.quarkus.scheduler.Scheduler
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class SchedulerInfoAdapter(
  private val scheduler: Scheduler,
) : CronjobInfoPort {

  override fun getCronjobStats(): List<CronjobStats> =
    scheduler.scheduledJobs
      .mapNotNull { trigger ->
        val methodDescription = trigger.methodDescription
        if (methodDescription.isNullOrEmpty()) return@mapNotNull null
        val hashIdx = methodDescription.lastIndexOf('#')
        if (hashIdx < 0) return@mapNotNull null
        val className = methodDescription.substring(0, hashIdx)
        val methodName = methodDescription.substring(hashIdx + 1)
        try {
          val clazz = Class.forName(className)
          val method = clazz.getDeclaredMethod(methodName)
          val scheduled = method.getAnnotation(Scheduled::class.java) ?: return@mapNotNull null
          CronjobStats(
            simpleName = clazz.simpleName,
            cronSchedule = scheduled.cron.ifEmpty { "every ${scheduled.every}" },
            nextExecution = trigger.nextFireTime?.toKotlinInstant(),
            running = scheduler.isRunning && !scheduler.isPaused(trigger.id),
          )
        } catch (e: ReflectiveOperationException) {
          logger.warn(e) { "Could not resolve cronjob metadata for trigger '${trigger.id}' (method: '$methodDescription')" }
          null
        }
      }
      .sortedBy { it.simpleName }
      .toList()

  companion object : KLogging()
}
