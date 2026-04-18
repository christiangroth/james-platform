package de.chrgroth.james.platform.domain.model.app

@JvmInline
value class ReportId(val value: String)

data class Report(
  val id: ReportId,
  val name: String,
  val html: String = "",
  val script: String = "",
)
