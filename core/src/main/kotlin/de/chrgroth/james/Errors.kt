package de.chrgroth.james

interface ErrorCodeProvider {
    val prefix: String
    val id: Long

    fun toGlobalRepresentation() = "${prefix}_${id.toString().padStart(length = 4, padChar = '0')}"
}

sealed class Maybe<A> {
    class Result<A>(val result: A) : Maybe<A>()
    class Error<A>(val errorCodeProvider: ErrorCodeProvider) : Maybe<A>()
}
