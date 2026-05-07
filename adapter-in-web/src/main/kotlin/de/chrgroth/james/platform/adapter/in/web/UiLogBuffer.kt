package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import java.io.PrintWriter
import java.io.StringWriter
import java.text.MessageFormat
import java.time.Duration
import java.time.Instant
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

data class UiLogEntry(
  val timestamp: Instant,
  val timestampEpochMillis: Long = timestamp.toEpochMilli(),
  val level: String,
  val clazz: String,
  val message: String,
  val stacktrace: String?,
)

@ApplicationScoped
class UiLogBuffer {
  private val retention: Duration = Duration.ofHours(1)
  private val maxEntries: Int = 200
  private val entries: ArrayDeque<UiLogEntry> = ArrayDeque()

  @Synchronized
  fun add(record: LogRecord) {
    if (record.level.intValue() < Level.WARNING.intValue()) {
      return
    }
    val now = Instant.now()
    pruneOld(now)
    val entry = UiLogEntry(
      timestamp = Instant.ofEpochMilli(record.millis),
      level = toUiLevel(record.level),
      clazz = record.sourceClassName ?: record.loggerName ?: "Unknown",
      message = formatMessage(record),
      stacktrace = record.thrown?.stacktraceAsString(),
    )
    entries.addLast(entry)
    while (entries.size > maxEntries) {
      entries.removeFirst()
    }
  }

  @Synchronized
  fun add(entry: UiLogEntry) {
    pruneOld(Instant.now())
    entries.addLast(entry)
    while (entries.size > maxEntries) {
      entries.removeFirst()
    }
  }

  @Synchronized
  fun getRecent(): List<UiLogEntry> {
    pruneOld(Instant.now())
    return entries.reversed()
  }

  @Synchronized
  fun clear() {
    entries.clear()
  }

  private fun toUiLevel(level: Level): String = when (level.name) {
    "SEVERE" -> "ERROR"
    "WARNING" -> "WARN"
    else -> level.name
  }

  private fun formatMessage(record: LogRecord): String {
    val rawMessage = record.message ?: ""
    val parameters = record.parameters ?: return rawMessage
    if (parameters.isEmpty()) {
      return rawMessage
    }
    return runCatching { MessageFormat.format(rawMessage, *parameters) }
      .getOrElse { rawMessage }
  }

  private fun pruneOld(now: Instant) {
    val oldestAllowed = now.minus(retention)
    while (entries.isNotEmpty() && entries.first().timestamp.isBefore(oldestAllowed)) {
      entries.removeFirst()
    }
  }
}

@ApplicationScoped
class UiLogBufferLifecycle(
  private val logBuffer: UiLogBuffer,
) {
  private val handler = object : Handler() {
    override fun publish(record: LogRecord?) {
      if (record == null || !isLoggable(record)) {
        return
      }
      logBuffer.add(record)
    }

    override fun flush() = Unit

    override fun close() = Unit
  }

  init {
    handler.level = Level.WARNING
  }

  fun onStartup(@Observes event: StartupEvent) {
    Logger.getLogger("").addHandler(handler)
  }

  fun onShutdown(@Observes event: ShutdownEvent) {
    Logger.getLogger("").removeHandler(handler)
  }
}

private fun Throwable.stacktraceAsString(): String {
  val writer = StringWriter()
  PrintWriter(writer).use { printWriter ->
    this.printStackTrace(printWriter)
  }
  return writer.toString()
}
