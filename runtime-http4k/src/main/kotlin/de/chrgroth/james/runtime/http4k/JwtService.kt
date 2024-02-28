package de.chrgroth.james.runtime.http4k

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTCreationException
import com.auth0.jwt.exceptions.JWTVerificationException
import mu.KLogging
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.TemporalUnit
import java.util.Date

interface JwtService {
    fun create(subject: String, expiresIn: Long, expiresInUnit: TemporalUnit): String?
    fun verify(token: String): String?
}

class JwtServiceServiceImpl(secret: String, private val issuer: String) : JwtService {

    private val algorithm = Algorithm.HMAC256(secret)

    private val verifier by lazy {
        JWT.require(algorithm).acceptExpiresAt(60).build()
    }

    override fun create(subject: String, expiresIn: Long, expiresInUnit: TemporalUnit): String? =
        try {
            JWT.create()
                .withIssuer(issuer)
                .withSubject(subject)
                .withExpiresAt(Date.from(LocalDateTime.now().plus(expiresIn, expiresInUnit).toInstant(ZoneOffset.UTC)))
                .sign(algorithm)
        } catch (e: JWTCreationException) {
            logger.error(e) { "Unable to create JWT token" }
            null
        }

    override fun verify(token: String): String? =
        try {
            verifier.verify(token).subject
        } catch (e: JWTVerificationException) {
            logger.info(e) { "Unable to verify JWT token" }
            null
        }

    companion object : KLogging()
}
