package de.chrgroth.james.runtime.quarkus

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.event.Observes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import mu.KLogging

private const val PATH = "/releasenotes.yaml"

@Path("/api/releasenotes")
@Suppress("unused")
class ReleasenotesEndpoint {

    private lateinit var releasenotes: YamlNode

    fun parseReleasenotes(@Observes startupEvent: StartupEvent?) {
        logger.info { "parsing releasenotes at $PATH..." }
        val stream = javaClass.getResourceAsStream(PATH)
        if (stream == null) {
            error("Unable to parse releasenotes, stream is null!")
        } else {
            releasenotes = Yaml.default.parseToYamlNode(stream)
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun get(): String {
        return releasenotes.contentToString()
    }

    companion object : KLogging()
}
