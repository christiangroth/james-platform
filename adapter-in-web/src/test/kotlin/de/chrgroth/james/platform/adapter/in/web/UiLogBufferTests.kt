package de.chrgroth.james.platform.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.logging.Level
import java.util.logging.LogRecord

class UiLogBufferTests {

  @Test
  fun `add filters to warning and above and maps levels`() {
    val buffer = UiLogBuffer()

    buffer.add(LogRecord(Level.INFO, "ignore me").apply { loggerName = "MyInfo" })
    buffer.add(LogRecord(Level.WARNING, "warn me").apply { loggerName = "MyWarn" })
    buffer.add(LogRecord(Level.SEVERE, "error me").apply { loggerName = "MyError" })

    val entries = buffer.getRecent()
    assertThat(entries).hasSize(2)
    assertThat(entries.map { it.level }).containsExactly("ERROR", "WARN")
  }

  @Test
  fun `getRecent removes entries older than one hour and returns newest first`() {
    val buffer = UiLogBuffer()
    val now = Instant.now()

    buffer.add(UiLogEntry(timestamp = now.minusSeconds(3700), level = "WARN", clazz = "A", message = "too old", stacktrace = null))
    buffer.add(UiLogEntry(timestamp = now.minusSeconds(20), level = "WARN", clazz = "B", message = "older", stacktrace = null))
    buffer.add(UiLogEntry(timestamp = now.minusSeconds(10), level = "ERROR", clazz = "C", message = "newest", stacktrace = "stack"))

    val entries = buffer.getRecent()
    assertThat(entries.map { it.message }).containsExactly("newest", "older")
  }

  @Test
  fun `add keeps at most 200 entries`() {
    val buffer = UiLogBuffer()
    val now = Instant.now()

    (1..205).forEach { index ->
      buffer.add(UiLogEntry(timestamp = now.plusSeconds(index.toLong()), level = "WARN", clazz = "C$index", message = "m$index", stacktrace = null))
    }

    val entries = buffer.getRecent()
    assertThat(entries).hasSize(200)
    assertThat(entries.first().message).isEqualTo("m205")
    assertThat(entries.last().message).isEqualTo("m6")
  }
}
