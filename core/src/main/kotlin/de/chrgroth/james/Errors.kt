package de.chrgroth.james

data class DomainError(
    val code: DomainErrorCode,
    // TODO #12 Really need details?? Won't be usable in frontend as String. Use Map instead?
    val details: String? = null,
)

interface DomainErrorCode {
    val prefix: String
    val id: Long

    fun toGlobalRepresentation(): String {
        return "${prefix}_${id.toString().padStart(length = 3, padChar = '0')}_$this"
    }
}
