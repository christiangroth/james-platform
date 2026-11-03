package de.chrgroth.james.platform.domain.model.app

/**
 * A declarative check (`checks: { <name>: ... }`), executed backend-side over a query of the report; its findings are shown in `warnings` panels
 * and counted on their tab. [message] may use `{placeholder}`s (see each check type); without one a German default text is used.
 */
sealed interface ReportCheck {
  val name: String

  /** The query the check reads. */
  val query: String
  val message: String?
  val severity: ReportCheckSeverity

  companion object {
    /** Placeholder of a gap check's message: the missing period's label. */
    const val PERIOD_PLACEHOLDER = "period"

    /** Placeholders of a rule check's message besides the result fields of its query. */
    const val VALUE_PLACEHOLDER = "value"
    const val MIN_PLACEHOLDER = "min"
    const val MAX_PLACEHOLDER = "max"
    const val MISSING_PLACEHOLDER = "missing"

    /** `{name}` placeholders in a check message. */
    val PLACEHOLDER = Regex("""\{([^{}\s]+)}""")
  }
}

enum class ReportCheckSeverity(val key: String) {
  WARNING("warning"),
  ERROR("error"),
}

/**
 * The period a gap check expects one entry per: a time bucket, or [WERKTAG] - every Monday to Friday. Keys and labels are those of the
 * corresponding [bucket] (see `encodeTimeBucket`).
 */
enum class ReportCheckPeriod(val bucket: TimeBucket) {
  WERKTAG(TimeBucket.TAG),
  TAG(TimeBucket.TAG),
  WOCHE(TimeBucket.WOCHE),
  MONAT(TimeBucket.MONAT),
  QUARTAL(TimeBucket.QUARTAL),
  JAHR(TimeBucket.JAHR),
}

/**
 * `{ gap: <query>, every: MONAT, exclude: [...], current: false }`: expects at least one row of [query] (a plain query with a time field) per
 * period [every], from the start of the query's range (without one: its first entry) up to its end, at most today. The current, still running
 * period is only expected with [current]. A period is not expected when all of its days (Mondays to Fridays for [ReportCheckPeriod.WERKTAG]) are
 * dates of the [exclude] calendars, e.g. holidays or vacation. Placeholder: `{period}`.
 */
data class ReportGapCheck(
  override val name: String,
  override val query: String,
  val every: ReportCheckPeriod,
  val exclude: List<ReportCalendar> = emptyList(),
  val current: Boolean = false,
  override val message: String? = null,
  override val severity: ReportCheckSeverity = ReportCheckSeverity.WARNING,
) : ReportCheck {
  companion object {
    const val KEY = "gap"
  }
}

/** Dates excluded from a gap check. */
sealed interface ReportCalendar {
  /** `config.<field>`: a DATE field or a LIST of dates of the App configuration (ADR 0027), e.g. holidays. */
  data class Config(val field: String) : ReportCalendar

  /**
   * `{ query: urlaub, field: von, to: bis }`: the dates of [field] (default: the query's time field) of all rows of [query]; with [to] every day
   * from [field] to [to] (inclusive), e.g. vacation periods.
   */
  data class Query(val query: String, val field: String?, val to: String? = null) : ReportCalendar
}

/**
 * `{ rule: <query>, field|expr, min, max }` and/or `{ rule: <query>, required: [...], when: [...] }`: checked per row of [query]'s (transformed)
 * result. A value rule reads [field] (a numeric result field) or computes [expression] (see the `calc` step; raw storage values) and warns when it
 * lies below [min] or above [max] (numbers, unit texts like `20km` for a field with unit, or `config.<field>`); rows without a value pass. A
 * required rule warns when a row lacks a value for any [required] field - only for rows having values for all [whenPresent] fields.
 * Placeholders: every result field (formatted like table cells), `{value}`, `{min}`, `{max}`, `{missing}`.
 */
data class ReportRuleCheck(
  override val name: String,
  override val query: String,
  val field: String? = null,
  val expression: String? = null,
  val min: String? = null,
  val max: String? = null,
  val required: List<String> = emptyList(),
  val whenPresent: List<String> = emptyList(),
  override val message: String? = null,
  override val severity: ReportCheckSeverity = ReportCheckSeverity.WARNING,
) : ReportCheck {
  companion object {
    const val KEY = "rule"
  }
}

/** `{ warnings: <title>, checks: [names] }`: the findings of the listed checks (all checks without a list), in check order. */
data class ReportWarningsPanel(
  override val title: String,
  val checks: List<String> = emptyList(),
) : ReportPanel {
  override val type: ReportPanelType get() = ReportPanelType.WARNINGS
}

/**
 * The findings of a warnings panel: at most the platform's cap of [warnings], [total] of them overall ([truncated] when cut off). Gap findings
 * come newest first, rule findings in the query's row order.
 */
data class WarningsPanelSpec(
  override val title: String,
  val warnings: List<WarningSpec>,
  val total: Int,
  val truncated: Boolean,
) : PanelSpec {
  override val type: ReportPanelType get() = ReportPanelType.WARNINGS
}

/** One finding of [check]: [severity] `warning`/`error`, [label] what it is about (missing period, the row's date or bucket), [message]. */
data class WarningSpec(val check: String, val severity: String, val label: String?, val message: String)
