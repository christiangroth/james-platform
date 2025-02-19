package de.chrgroth.james

data class DomainError(
  val code: DomainErrorCode,
  val errorMessage: String? = null,
) {

  fun toLogString() =
    "${code.toGlobalRepresentation()}.${errorMessage.trimToNull() ?: ""}"
}

interface DomainErrorCode {

  enum class LogLevel {
    INFO, WARN, ERROR
  }

  val prefix: String
  val id: Long

  fun logLevel(): LogLevel =
    LogLevel.INFO

  fun toGlobalRepresentation(): String {
    return "${prefix}_${id.toString().padStart(length = 3, padChar = '0')}_$this"
  }
}
