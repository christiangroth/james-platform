package de.chrgroth.james.data

import de.chrgroth.james.DomainErrorCode

internal enum class DataDomainErrorCodes : DomainErrorCode {
    NOT_FOUND;

    override val prefix = "DATA"
    override val id = ordinal.toLong()
}
