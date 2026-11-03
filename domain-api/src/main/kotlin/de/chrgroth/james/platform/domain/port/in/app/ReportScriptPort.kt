package de.chrgroth.james.platform.domain.port.`in`.app

import java.time.LocalDate

/**
 * Runs the Kotlin scripts of report `kotlin` transformation steps (ADR 0028): the JSR-223 Kotlin engine with the timeout of computed properties
 * (`app.script.timeout-ms`, ADR 0008) and no sandbox beyond it.
 */
interface ReportScriptPort {

  /**
   * Runs [script] with the bindings `rows` ([rows], raw storage values by field), `config` ([config], configuration values by field name) and
   * `today`; [report] and [label] identify the script in logs and script metrics. Never throws: failures and timeouts are a [ReportScriptResult.Failure].
   */
  fun execute(script: String, rows: List<Map<String, String?>>, config: Map<String, String?>, today: LocalDate, report: String, label: String): ReportScriptResult

  /**
   * Compiles [script] without running it, for validation on save. Null when it compiles or when that cannot be decided (engine unavailable,
   * compilation not finished in time); else the compiler's problem.
   */
  fun compileProblem(script: String): ReportScriptProblem?
}

sealed interface ReportScriptResult {
  /** The rows the script returned, values converted to raw strings. */
  data class Success(val rows: List<Map<String, String?>>) : ReportScriptResult

  /** Why the script produced no rows, as German text for the report page. */
  data class Failure(val message: String) : ReportScriptResult
}

/** A compiler problem: [message] as reported by the compiler, [line] the 1-based line within the script (null if unknown). */
data class ReportScriptProblem(val message: String, val line: Int?)
