package de.chrgroth.james.platform.domain.model.app

@JvmInline
value class ReportId(val value: String)

/**
 * A declarative report of an App Version (see arc42 "Reports"). [definition] is the YAML source as authored by the Developer; it is
 * parsed into a [ReportDefinition] and validated against the version's entities on save and on publish. A blank definition is valid and
 * describes an empty report (a freshly created report starts like that).
 */
data class Report(
  val id: ReportId,
  val name: String,
  val definition: String = "",
)
