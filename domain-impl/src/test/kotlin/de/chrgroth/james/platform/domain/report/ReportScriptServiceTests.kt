package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.app.ScriptMetrics
import de.chrgroth.james.platform.domain.model.infra.ScriptType
import de.chrgroth.james.platform.domain.port.`in`.app.ReportScriptResult
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate

/** The Kotlin engine behind report `kotlin` steps (ADR 0028): bindings, result conversion, failures and the compile check. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportScriptServiceTests {

  private val metrics = ScriptMetrics(SimpleMeterRegistry())
  private val service = ReportScriptService(metrics, 30_000L)
  private val today = LocalDate.of(2026, 6, 15)

  private fun run(script: String, rows: List<Map<String, String?>> = emptyList(), config: Map<String, String?> = emptyMap()) =
    service.execute(script, rows, config, today, "Report", "q.kotlin[0]")

  @Test
  fun `execute sees rows, config and today and converts the returned values to raw strings`() {
    val result = run(
      """
      val faktor = config["faktor"]!!.toDouble()
      rows.map { row ->
        row + mapOf("doppelt" to row["wert"]!!.toLong() * faktor, "datum" to today, "gross" to (row["wert"]!!.toLong() > 1), "leer" to null)
      }
      """.trimIndent(),
      rows = listOf(mapOf("wert" to "1"), mapOf("wert" to "3")),
      config = mapOf("faktor" to "2"),
    )

    assertThat(result).isInstanceOf(ReportScriptResult.Success::class.java)
    val rows = (result as ReportScriptResult.Success).rows
    assertThat(rows).containsExactly(
      mapOf("wert" to "1", "doppelt" to "2", "datum" to "2026-06-15", "gross" to "false", "leer" to null),
      mapOf("wert" to "3", "doppelt" to "6", "datum" to "2026-06-15", "gross" to "true", "leer" to null),
    )
    assertThat(metrics.getStats().single { it.type == ScriptType.REPORT }.errorCount).isZero()
  }

  @Test
  fun `execute allows an early return from the script`() {
    val result = run("if (rows.isEmpty()) return emptyList<Map<String, Any?>>()\nrows")

    assertThat(result).isEqualTo(ReportScriptResult.Success(emptyList()))
  }

  @Test
  fun `execute answers a German failure for exceptions, wrong results and timeouts`() {
    val thrown = run("error(\"kaputt\")") as ReportScriptResult.Failure
    assertThat(thrown.message).startsWith("Der Kotlin-Schritt ist fehlgeschlagen").contains("kaputt")

    val wrongResult = run("42") as ReportScriptResult.Failure
    assertThat(wrongResult.message).contains("Liste von Zeilen")

    val slow = ReportScriptService(metrics, 50L).execute("Thread.sleep(5_000)\nrows", emptyList(), emptyMap(), today, "Report", "slow")
    assertThat((slow as ReportScriptResult.Failure).message).contains("Zeitüberschreitung")
  }

  @Test
  fun `compileProblem reports compiler errors with the line within the script and accepts valid scripts without running them`() {
    assertThat(service.compileProblem("error(\"läuft nicht\")\nrows")).isNull()

    val problem = service.compileProblem("val a = 1\nunbekannt(a)")

    assertThat(problem).isNotNull
    assertThat(problem!!.message).containsIgnoringCase("unbekannt")
    assertThat(problem.line).isEqualTo(2)
  }
}
