package de.chrgroth.james

// TODO #29 refactor to return all errors and not error by error
// TODO #29 really need details?? won't be usable for the frontend, maybe just return the code??
// TODO #29 replace all fq references with proper imports after other Error class was deleted
data class Error(val code: ErrorCode, val details: String? = null)

interface ErrorCode {
    val prefix: String
    val id: Long

    fun toGlobalRepresentation(): String {
        return "${prefix}_${id.toString().padStart(length = 3, padChar = '0')}_$this"
    }
}
