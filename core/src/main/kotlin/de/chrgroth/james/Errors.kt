package de.chrgroth.james

data class DomainError(
    val code: DomainErrorCode,
    val errorMessage: String? = null,
)

interface DomainErrorCode {
    val prefix: String
    val id: Long

    fun toGlobalRepresentation(): String {
        return "${prefix}_${id.toString().padStart(length = 3, padChar = '0')}_$this"
    }
}
