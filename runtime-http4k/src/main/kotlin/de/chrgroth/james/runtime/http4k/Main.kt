package de.chrgroth.james.runtime.http4k

import java.util.UUID

fun main() {
    val config = JamesPlatformServerConfig.createFromEnvWithDefaults(
        port = 8080,
        shutdownTimeoutMillis = 10000,
        jwtSecret = UUID.randomUUID().toString(),
    )
    
    JamesPlatformServer(config).start()
}
