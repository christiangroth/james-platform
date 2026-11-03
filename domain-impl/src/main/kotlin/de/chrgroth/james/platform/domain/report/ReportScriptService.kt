package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.app.ScriptMetrics
import de.chrgroth.james.platform.domain.model.infra.ScriptType
import de.chrgroth.james.platform.domain.port.`in`.app.ReportScriptPort
import de.chrgroth.james.platform.domain.port.`in`.app.ReportScriptProblem
import de.chrgroth.james.platform.domain.port.`in`.app.ReportScriptResult
import jakarta.enterprise.context.ApplicationScoped
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.TemporalAccessor
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * Runs the scripts of report `kotlin` steps (ADR 0028) like computed properties run theirs (ADR 0008): the JSR-223 Kotlin engine, each run on its
 * own virtual thread bounded by `app.script.timeout-ms`, no sandbox beyond that timeout (the Developer role is trusted). The script is wrapped
 * into a function `(rows, config, today) -> Any?`, so a compile check can define it without running any of the Developer's code.
 */
@ApplicationScoped
class ReportScriptService(
  private val scriptMetrics: ScriptMetrics,
  @param:ConfigProperty(name = "app.script.timeout-ms", defaultValue = "500")
  private val scriptTimeoutMs: Long,
) : ReportScriptPort {

  private val scriptExecutor = Executors.newVirtualThreadPerTaskExecutor()

  // See ComputedPropertyService: the context class loader is required in Quarkus to find the JSR 223 Kotlin engine.
  private val scriptEngineManager: ScriptEngineManager by lazy { ScriptEngineManager(Thread.currentThread().contextClassLoader) }

  private val scriptEngine: ScriptEngine? by lazy {
    scriptEngineManager.getEngineByExtension("kts").also {
      if (it == null) logger.warn { "Kotlin scripting engine not available – report kotlin steps will fail" }
    }
  }

  override fun execute(
    script: String,
    rows: List<Map<String, String?>>,
    config: Map<String, String?>,
    today: LocalDate,
    report: String,
    label: String,
  ): ReportScriptResult {
    val engine = scriptEngine ?: return ReportScriptResult.Failure(UNAVAILABLE)
    val startNs = System.nanoTime()
    var success = false
    try {
      val bindings = engine.createBindings()
      bindings[BINDING_ROWS] = rows
      bindings[BINDING_CONFIG] = config
      bindings[BINDING_TODAY] = today
      val future = scriptExecutor.submit(Callable { engine.eval(wrap(script) + CALL, bindings) })
      val value = future.get(scriptTimeoutMs, TimeUnit.MILLISECONDS)
      return toRows(value).also { success = it is ReportScriptResult.Success }
    } catch (e: TimeoutException) {
      logger.warn { "Report kotlin step timed out after ${scriptTimeoutMs}ms: $report / $label" }
      return ReportScriptResult.Failure("Der Kotlin-Schritt wurde nach $scriptTimeoutMs ms abgebrochen (Zeitüberschreitung).")
    } catch (e: ExecutionException) {
      val cause = e.cause ?: e
      logger.warn { "Report kotlin step failed: $report / $label: ${cause.message}" }
      return ReportScriptResult.Failure("Der Kotlin-Schritt ist fehlgeschlagen: ${detail(cause)}")
    } catch (e: Exception) {
      logger.warn { "Report kotlin step failed: $report / $label: ${e.message}" }
      return ReportScriptResult.Failure("Der Kotlin-Schritt ist fehlgeschlagen: ${detail(e)}")
    } finally {
      scriptMetrics.record(ScriptType.REPORT, report, label, (System.nanoTime() - startNs) / NANOS_PER_MILLI, success)
    }
  }

  override fun compileProblem(script: String): ReportScriptProblem? {
    val engine = scriptEngine ?: return null
    // Only defines the wrapping function: none of the script's statements run.
    val future = scriptExecutor.submit(Callable { engine.eval(wrap(script), engine.createBindings()) })
    return try {
      future.get(maxOf(scriptTimeoutMs, COMPILE_TIMEOUT_MS), TimeUnit.MILLISECONDS)
      null
    } catch (e: TimeoutException) {
      logger.warn { "Report kotlin step compile check did not finish in time, skipped" }
      null
    } catch (e: ExecutionException) {
      problem(e.cause ?: e)
    } catch (e: ScriptException) {
      problem(e)
    }
  }

  private fun problem(e: Throwable): ReportScriptProblem {
    val message = firstError(e)
    val line = LINE_IN_MESSAGE.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: (e as? ScriptException)?.lineNumber?.takeIf { it > 0 }
    return ReportScriptProblem(shorten(LOCATION.replace(message, "").removePrefix(ERROR_PREFIX).trim()), line?.minus(HEADER_LINES)?.takeIf { it > 0 })
  }

  /** The compiler's first error line (it also reports warnings, e.g. unused bindings), else the first line of the message. */
  private fun firstError(e: Throwable): String {
    val lines = e.message.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    return lines.firstOrNull { it.startsWith(ERROR_PREFIX) } ?: lines.firstOrNull() ?: e.javaClass.simpleName
  }

  /** The script's result as rows: an iterable of maps, values converted to raw strings (numbers plain, dates ISO). */
  private fun toRows(value: Any?): ReportScriptResult {
    val items = (value as? Iterable<*>)?.toList() ?: return ReportScriptResult.Failure(RESULT_INVALID)
    if (items.size > MAX_ROWS) return ReportScriptResult.Failure("Der Kotlin-Schritt hat mehr als $MAX_ROWS Zeilen zurückgegeben.")
    val rows = items.map { item ->
      val map = item as? Map<*, *> ?: return ReportScriptResult.Failure(RESULT_INVALID)
      map.entries.associate { (key, raw) -> key.toString() to raw(raw) }
    }
    return ReportScriptResult.Success(rows)
  }

  private fun raw(value: Any?): String? = when (value) {
    null -> null
    is String -> value
    is Double -> value.takeIf { it.isFinite() }?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }
    is Float -> value.takeIf { it.isFinite() }?.let { BigDecimal.valueOf(it.toDouble()).stripTrailingZeros().toPlainString() }
    is BigDecimal -> value.stripTrailingZeros().toPlainString()
    is TemporalAccessor -> value.toString()
    else -> value.toString()
  }

  /** The first error line of an exception's message, shortened, without the engine's location noise. */
  private fun detail(e: Throwable): String = shorten(LOCATION.replace(firstError(e), "").removePrefix(ERROR_PREFIX).trim())

  private fun shorten(message: String): String = if (message.length > MAX_DETAIL) message.take(MAX_DETAIL) + "…" else message

  private fun wrap(script: String): String = HEADER + script + FOOTER

  companion object : KLogging() {
    private const val BINDING_ROWS = "_reportRows"
    private const val BINDING_CONFIG = "_reportConfig"
    private const val BINDING_TODAY = "_reportToday"
    private const val FUNCTION = "__jamesReportStep"

    private val HEADER = listOf(
      "@file:Suppress(\"UNCHECKED_CAST\", \"UNUSED_PARAMETER\")",
      "fun $FUNCTION(rows: List<Map<String, String?>>, config: Map<String, String?>, today: java.time.LocalDate): Any? {",
      "return run {",
    ).joinToString("\n", postfix = "\n")

    /** Lines before the script's first line, to translate compiler line numbers back into the script. */
    private val HEADER_LINES = HEADER.count { it == '\n' }
    private const val FOOTER = "\n}\n}\n"

    private const val CALL = "$FUNCTION(" +
      "$BINDING_ROWS as List<Map<String, String?>>, $BINDING_CONFIG as Map<String, String?>, $BINDING_TODAY as java.time.LocalDate)\n"

    private val LINE_IN_MESSAGE = Regex("""\.kts:(\d+):\d+""")
    private val LOCATION = Regex("""\s*\([^()]*\.kts:\d+:\d+\)""")
    private const val ERROR_PREFIX = "ERROR "
    private const val COMPILE_TIMEOUT_MS = 15_000L
    private const val MAX_ROWS = 100_000
    private const val MAX_DETAIL = 300
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val UNAVAILABLE = "Kotlin-Skripte sind auf diesem Server nicht verfügbar."
    private const val RESULT_INVALID = "Der Kotlin-Schritt muss eine Liste von Zeilen (Maps von Feldname auf Wert) zurückgeben."
  }
}
