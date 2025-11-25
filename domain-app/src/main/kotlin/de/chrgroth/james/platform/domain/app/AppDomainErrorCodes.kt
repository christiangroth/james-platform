package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.DomainErrorCode
import de.chrgroth.james.DomainErrorCode.LogLevel
import de.chrgroth.james.DomainErrorCode.LogLevel.ERROR

enum class AppDomainErrorCodes : DomainErrorCode {
  APP_EXISTS,
  APP_UNKNOWN,

  APP_QUERY_FAILED {
    override fun logLevel(): LogLevel = ERROR
  };

  override val prefix = "APP"
  override val id = ordinal.toLong()
}
