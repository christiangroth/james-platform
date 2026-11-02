package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.ReportAggregate
import de.chrgroth.james.platform.domain.model.app.ReportAggregateFunction
import de.chrgroth.james.platform.domain.model.app.ReportAggregateValue
import de.chrgroth.james.platform.domain.model.app.ReportAxis
import de.chrgroth.james.platform.domain.model.app.ReportChartPanel
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.ReportPanelType
import de.chrgroth.james.platform.domain.model.app.ReportReduce
import de.chrgroth.james.platform.domain.model.app.ReportSeries
import de.chrgroth.james.platform.domain.model.app.ReportSeriesStyle
import de.chrgroth.james.platform.domain.model.app.ReportStatPanel
import de.chrgroth.james.platform.domain.model.app.ReportThreshold
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.MonthDay

class ReportAggregationValidationTests {

  private fun parse(yaml: String) = ReportDefinitionParser.parse(yaml.trimIndent(), ReportFixtures.entities)

  private fun errors(yaml: String) = parse(yaml).errors.map { it.code to it.path }

  @Test
  fun `parses aggregate queries, chart and stat panels`() {
    val definition = parse(
      """
      queries:
        km:
          entity: Fahrt
          timeField: datum
          range: this_year
          aggregate:
            bucket: quartal
            periodStart: 04-10
            groupBy: rad
            values:
              km: { sum: distanz }
              fahrten: count
      tabs:
        - name: T
          rows:
            - - { timeseries: Kilometer, query: km, style: area, stack: true, series: [{ field: km }, { field: fahrten, axis: right, style: line }] }
              - { stat: Fahrten, query: km, field: fahrten, reduce: sum, compare: previous_period, thresholds: [{ value: 10, color: orange }] }
      """,
    ).definition!!

    assertThat(definition.queries.single().aggregate).isEqualTo(
      ReportAggregate(
        values = listOf(ReportAggregateValue("km", ReportAggregateFunction.SUM, "distanz"), ReportAggregateValue("fahrten", ReportAggregateFunction.COUNT, null)),
        bucket = TimeBucket.QUARTAL,
        periodStart = MonthDay.of(4, 10),
        groupBy = "rad",
      ),
    )
    val (chart, stat) = definition.tabs.single().rows.single().panels
    assertThat(chart).isEqualTo(
      ReportChartPanel(
        ReportPanelType.TIMESERIES,
        "Kilometer",
        listOf(ReportSeries("km", "km", style = ReportSeriesStyle.AREA), ReportSeries("km", "fahrten", style = ReportSeriesStyle.LINE, axis = ReportAxis.RIGHT)),
        stack = true,
      ),
    )
    assertThat(stat).isEqualTo(ReportStatPanel("Fahrten", "km", "fahrten", ReportReduce.SUM, true, listOf(ReportThreshold("10", "orange"))))
  }

  @Test
  fun `aggregates are validated`() {
    assertThat(
      errors(
        """
        queries:
          a: { entity: Fahrt, aggregate: { bucket: MONAT, sum: rad } }
          b: { entity: Fahrt, timeField: datum, fields: [datum], aggregate: { bucket: WOCHE, periodStart: "01.04.", avg: distanz } }
          c: { entity: Fahrt, timeField: datum, aggregate: { bucket: JAHR, periodStart: 02-29, values: { bucket: count } } }
          d: { entity: Fahrt, aggregate: { groupBy: nope, values: { x: { sum: distanz, avg: distanz } }, colour: red } }
          e: { entity: Fahrt, aggregate: { groupBy: rad } }
          f: { entity: Fahrt, aggregate: { count: "*" }, sort: [nope] }
        tabs: []
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.TIME_FIELD_MISSING to "queries.a.aggregate.bucket",
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC to "queries.a.aggregate.sum",
      ReportDefinitionErrorCode.PERIOD_START_NOT_APPLICABLE to "queries.b.aggregate.periodStart",
      ReportDefinitionErrorCode.KEY_CONFLICT to "queries.b.fields",
      ReportDefinitionErrorCode.PERIOD_START_INVALID to "queries.c.aggregate.periodStart",
      ReportDefinitionErrorCode.DUPLICATE_RESULT_FIELD to "queries.c.aggregate.values.bucket",
      ReportDefinitionErrorCode.UNKNOWN_KEY to "queries.d.aggregate.colour",
      ReportDefinitionErrorCode.KEY_CONFLICT to "queries.d.aggregate.values.x.avg",
      ReportDefinitionErrorCode.FIELD_NOT_FOUND to "queries.d.aggregate.groupBy",
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "queries.e.aggregate",
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "queries.f.sort[0].field",
    )
  }

  @Test
  fun `chart panels are validated`() {
    assertThat(
      errors(
        """
        queries:
          plain: { entity: Fahrt, fields: [datum, distanz, rad] }
          timed: { entity: Fahrt, timeField: datum, fields: [datum, distanz, gewicht, rad] }
          monat: { entity: Fahrt, timeField: datum, aggregate: { bucket: MONAT, sum: distanz } }
          jahr: { entity: Fahrt, timeField: datum, aggregate: { bucket: JAHR, sum: distanz } }
        tabs:
          - name: T
            rows:
              - - { timeseries: A, query: plain, field: distanz }
                - { timeseries: B, query: timed }
                - { timeseries: C, query: timed, field: rad, style: dots }
                - { timeseries: D, series: [{ query: monat, field: sum }, { query: jahr, field: sum }] }
                - { timeseries: E, query: timed, series: [{ field: distanz }, { field: gewicht }] }
                - { bar: F, query: plain, field: distanz }
                - { timeseries: G, query: missing, field: x }
                - { timeseries: H, query: monat, field: nope }
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.CHART_X_MISSING to "tabs[0].rows[0][0].query",
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "tabs[0].rows[0][1]",
      ReportDefinitionErrorCode.INVALID_VALUE to "tabs[0].rows[0][2].style",
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC to "tabs[0].rows[0][2].field",
      ReportDefinitionErrorCode.SERIES_X_MISMATCH to "tabs[0].rows[0][3].series[1].query",
      ReportDefinitionErrorCode.AXIS_UNIT_MISMATCH to "tabs[0].rows[0][4].series[1]",
      ReportDefinitionErrorCode.CHART_X_MISSING to "tabs[0].rows[0][5].query",
      ReportDefinitionErrorCode.QUERY_NOT_FOUND to "tabs[0].rows[0][6].query",
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "tabs[0].rows[0][7].field",
    )
  }

  @Test
  fun `stat panels are validated`() {
    assertThat(
      errors(
        """
        queries:
          plain: { entity: Fahrt, fields: [datum, distanz, rad] }
          both: { entity: Fahrt, aggregate: { values: { a: count, b: count } } }
        tabs:
          - name: T
            rows:
              - - { stat: A, query: plain }
                - { stat: B, query: both }
                - { stat: C, query: plain, field: rad, reduce: median }
                - { stat: D, query: plain, field: distanz, compare: previous_period }
                - { stat: E, query: plain, field: distanz, thresholds: [{ value: viel, color: red }, { value: 3kg, color: pink }] }
                - { stat: F, query: plain, field: rad, reduce: count, thresholds: [{ value: 3, color: "#123456" }] }
        """,
      ),
    ).containsExactly(
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "tabs[0].rows[0][0]",
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "tabs[0].rows[0][1]",
      ReportDefinitionErrorCode.INVALID_VALUE to "tabs[0].rows[0][2].reduce",
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC to "tabs[0].rows[0][2].field",
      ReportDefinitionErrorCode.COMPARE_NEEDS_RANGE to "tabs[0].rows[0][3].compare",
      ReportDefinitionErrorCode.INVALID_NUMBER to "tabs[0].rows[0][4].thresholds[0].value",
      ReportDefinitionErrorCode.INVALID_NUMBER to "tabs[0].rows[0][4].thresholds[1].value",
      ReportDefinitionErrorCode.INVALID_COLOR to "tabs[0].rows[0][4].thresholds[1].color",
    )
  }

  @Test
  fun `period starts accept MM-DD and DD-MM, not 29 February`() {
    assertThat(ReportDefinitionParser.parsePeriodStart("04-10")).isEqualTo(MonthDay.of(4, 10))
    assertThat(ReportDefinitionParser.parsePeriodStart("10.04.")).isEqualTo(MonthDay.of(4, 10))
    assertThat(ReportDefinitionParser.parsePeriodStart("1.4")).isEqualTo(MonthDay.of(4, 1))
    assertThat(ReportDefinitionParser.parsePeriodStart("02-29")).isNull()
    assertThat(ReportDefinitionParser.parsePeriodStart("13-01")).isNull()
  }

  @Test
  fun `schema-only parsing checks panels against aggregate result fields`() {
    val result = ReportDefinitionParser.parse(
      "queries:\n  q: { entity: Whatever, timeField: t, aggregate: { bucket: MONAT, sum: x } }\ntabs:\n  - name: T\n    rows: [[{ timeseries: A, query: q, field: nope }]]",
      null,
    )

    assertThat(result.errors.map { it.code }).containsExactly(ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY)
  }
}
