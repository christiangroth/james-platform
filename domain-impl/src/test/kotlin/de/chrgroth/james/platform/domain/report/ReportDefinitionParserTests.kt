package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.RelativePeriod
import de.chrgroth.james.platform.domain.model.app.ReportColumn
import de.chrgroth.james.platform.domain.model.app.ReportDefinition
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.ReportSort
import de.chrgroth.james.platform.domain.model.app.ReportTablePanel
import de.chrgroth.james.platform.domain.model.app.ReportTextPanel
import de.chrgroth.james.platform.domain.model.app.ReportTimeRange
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.imports.FilterOperator
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ReportDefinitionParserTests {

  private val entities = ReportFixtures.entities

  private fun parse(yaml: String) = ReportDefinitionParser.parse(yaml.trimIndent(), entities)

  private fun errorCodes(yaml: String) = parse(yaml).errors.map { it.code }

  @Test
  fun `blank and comment-only definitions are the empty report`() {
    assertThat(parse("").definition).isEqualTo(ReportDefinition.EMPTY)
    assertThat(parse("# nothing yet").definition).isEqualTo(ReportDefinition.EMPTY)
  }

  @Test
  fun `parses queries, tabs, rows and panels`() {
    val result = parse(
      """
      queries:
        konzerte:
          entity: Konzert
          timeField: datum
          range: last_12_months
          filter:
            - { field: ort, op: equals, value: Hamburg }
            - { field: band.name, op: MATCHES, value: "^Die", mode: EXCLUDE, includeAbsent: true }
          fields: [datum, band.name, ort, preis, jahr]
          sort:
            - { field: datum, direction: DESC }
            - ort
          limit: 50
      tabs:
        - name: Übersicht
          rows:
            - - text: Hinweis
                markdown: "**fett**"
              - table: Konzerte
                query: konzerte
                columns: [datum, { field: band.name, label: Band }]
                limit: 10
            - panels:
                - table:
                  query: konzerte
      """,
    )

    assertThat(result.errors).isEmpty()
    val definition = result.definition!!
    val query = definition.queries.single()
    assertThat(query.name).isEqualTo("konzerte")
    assertThat(query.entity).isEqualTo("Konzert")
    assertThat(query.range).isEqualTo(ReportTimeRange.Relative(RelativePeriod.LAST_12_MONTHS))
    assertThat(query.filter).containsExactly(
      FilterRule(FilterMode.INCLUDE, "ort", FilterOperator.EQUALS, "Hamburg"),
      FilterRule(FilterMode.EXCLUDE, "band.name", FilterOperator.MATCHES, "^Die", includeAbsent = true),
    )
    assertThat(query.fields).containsExactly("datum", "band.name", "ort", "preis", "jahr")
    assertThat(query.sort).containsExactly(ReportSort("datum", SortDirection.DESC), ReportSort("ort", SortDirection.ASC))
    assertThat(query.limit).isEqualTo(50)

    val rows = definition.tabs.single().rows
    assertThat(rows).hasSize(2)
    assertThat(rows[0].panels).containsExactly(
      ReportTextPanel("Hinweis", "**fett**"),
      ReportTablePanel("Konzerte", "konzerte", listOf(ReportColumn("datum"), ReportColumn("band.name", "Band")), 10),
    )
    assertThat(rows[1].panels).containsExactly(ReportTablePanel("", "konzerte"))
  }

  @Test
  fun `parses a fixed time range`() {
    val result = parse(
      """
      queries:
        q: { entity: Konzert, timeField: datum, range: { from: 2024-01-01, to: 2024-12-31 } }
      tabs: []
      """,
    )

    assertThat(result.definition!!.queries.single().range).isEqualTo(ReportTimeRange.Fixed(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
  }

  @Test
  fun `reports yaml syntax errors with their line`() {
    val errors = parse("tabs:\n  - name: [unclosed\n").errors

    assertThat(errors.single().code).isEqualTo(ReportDefinitionErrorCode.YAML_SYNTAX)
    assertThat(errors.single().line).isNotNull()
  }

  @Test
  fun `duplicate query names are rejected`() {
    val errors = parse("queries:\n  q: { entity: Konzert }\n  q: { entity: Band }\ntabs: []\n").errors

    assertThat(errors.single().code).isEqualTo(ReportDefinitionErrorCode.DUPLICATE_KEY)
    assertThat(errors.single().args).containsExactly("q")
  }

  @Test
  fun `unknown keys are errors with path and line, reserved keys are not supported yet`() {
    val errors = parse(
      """
      checks: []
      queries:
        q:
          entity: Konzert
          colour: red
      tabs: []
      """,
    ).errors

    assertThat(errors.map { it.code }).containsExactly(ReportDefinitionErrorCode.NOT_SUPPORTED_YET, ReportDefinitionErrorCode.UNKNOWN_KEY)
    assertThat(errors[1].path).isEqualTo("queries.q.colour")
    assertThat(errors[1].line).isEqualTo(5)
  }

  @Test
  fun `tabs are required in a non-blank definition`() {
    assertThat(errorCodes("queries: {}")).containsExactly(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING)
  }

  @Test
  fun `wrong node types are reported`() {
    assertThat(errorCodes("tabs: nope")).containsExactly(ReportDefinitionErrorCode.WRONG_TYPE)
    assertThat(errorCodes("- just a list")).containsExactly(ReportDefinitionErrorCode.WRONG_TYPE)
  }

  @Test
  fun `references to entities and fields are validated`() {
    val errors = parse(
      """
      queries:
        a: { entity: Unknown }
        b: { entity: Konzert, fields: [datum, nope, band.nope, ort.name, band.name.x], timeField: ort, range: last_30_days }
      tabs: []
      """,
    ).errors

    assertThat(errors.map { it.code to it.path }).containsExactly(
      ReportDefinitionErrorCode.ENTITY_NOT_FOUND to "queries.a.entity",
      ReportDefinitionErrorCode.TIME_FIELD_INVALID to "queries.b.timeField",
      ReportDefinitionErrorCode.FIELD_NOT_FOUND to "queries.b.fields[1]",
      ReportDefinitionErrorCode.FIELD_NOT_FOUND to "queries.b.fields[2]",
      ReportDefinitionErrorCode.REF_HOP_INVALID to "queries.b.fields[3]",
      ReportDefinitionErrorCode.REF_HOP_INVALID to "queries.b.fields[4]",
    )
  }

  @Test
  fun `filter rules are validated`() {
    val errors = parse(
      """
      queries:
        q:
          entity: Konzert
          filter:
            - { field: ort, op: SOUNDS_LIKE, value: x }
            - { field: ort, op: EQUALS }
            - { field: ort, op: MATCHES, value: "[" }
            - { field: ort, op: IS_NULL }
      tabs: []
      """,
    ).errors

    assertThat(errors.map { it.code }).containsExactly(
      ReportDefinitionErrorCode.INVALID_VALUE,
      ReportDefinitionErrorCode.FILTER_VALUE_MISSING,
      ReportDefinitionErrorCode.INVALID_REGEX,
    )
  }

  @Test
  fun `time ranges are validated`() {
    assertThat(errorCodes("queries:\n  q: { entity: Konzert, timeField: datum, range: someday }\ntabs: []"))
      .containsExactly(ReportDefinitionErrorCode.INVALID_VALUE)
    assertThat(errorCodes("queries:\n  q: { entity: Konzert, range: last_year }\ntabs: []"))
      .containsExactly(ReportDefinitionErrorCode.TIME_FIELD_MISSING)
    assertThat(errorCodes("queries:\n  q: { entity: Konzert, timeField: datum, range: { from: 2025-01-01, to: 2024-01-01 } }\ntabs: []"))
      .containsExactly(ReportDefinitionErrorCode.INVALID_RANGE)
    assertThat(errorCodes("queries:\n  q: { entity: Konzert, timeField: datum, range: { from: 01.01.2024 } }\ntabs: []"))
      .containsExactly(ReportDefinitionErrorCode.INVALID_DATE)
    assertThat(errorCodes("queries:\n  q: { entity: Konzert, timeField: datum, range: \$zeitraum }\ntabs: []"))
      .containsExactly(ReportDefinitionErrorCode.VARIABLE_NOT_FOUND)
  }

  @Test
  fun `limits must be positive and bounded`() {
    assertThat(errorCodes("queries:\n  q: { entity: Konzert, limit: 0 }\ntabs: []")).containsExactly(ReportDefinitionErrorCode.NUMBER_OUT_OF_RANGE)
    assertThat(errorCodes("queries:\n  q: { entity: Konzert, limit: ten }\ntabs: []")).containsExactly(ReportDefinitionErrorCode.WRONG_TYPE)
  }

  @Test
  fun `panels must have exactly one supported type and reference existing queries and query fields`() {
    val errors = parse(
      """
      queries:
        q: { entity: Konzert, fields: [datum, ort] }
      tabs:
        - name: Tab
          rows:
            - - { query: q }
              - { table: A, text: B, markdown: x, query: q }
              - { pie: Kilometer }
              - { table: C, query: missing }
              - { table: D, query: q, columns: [datum, preis] }
              - { text: E }
      """,
    ).errors

    assertThat(errors.map { it.code to it.path }).containsExactly(
      ReportDefinitionErrorCode.PANEL_TYPE_MISSING to "tabs[0].rows[0][0]",
      ReportDefinitionErrorCode.PANEL_TYPE_AMBIGUOUS to "tabs[0].rows[0][1]",
      ReportDefinitionErrorCode.NOT_SUPPORTED_YET to "tabs[0].rows[0][2].pie",
      ReportDefinitionErrorCode.QUERY_NOT_FOUND to "tabs[0].rows[0][3].query",
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY to "tabs[0].rows[0][4].columns[1]",
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING to "tabs[0].rows[0][5]",
    )
    assertThat(errors.map { it.line }).containsExactly(6, 7, 8, 9, 10, 11)
  }

  @Test
  fun `columns default to all non-object fields and computed properties of the entity`() {
    assertThat(parse("queries:\n  q: { entity: Konzert }\ntabs:\n  - name: T\n    rows: [[{ table: X, query: q, columns: [jahr, besucher] }]]").errors).isEmpty()
  }

  @Test
  fun `schema-only parsing skips entity checks`() {
    val result = ReportDefinitionParser.parse("queries:\n  q: { entity: Whatever, fields: [a] }\ntabs: []", null)

    assertThat(result.errors).isEmpty()
  }
}
