package de.chrgroth.james

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class JsonSchemaGenerationTests {

    @Test
    fun `schema id for SNAPSHOT version`() {
        val appId = UUID.fromString("c5ded581-413d-4b3c-a712-0b178452ae66")
        val version = null
        val datatypeName = "Foo"
        assertThat(jsonSchemaIdFor(appId, version, datatypeName))
            .isEqualTo("/apps/c5ded581-413d-4b3c-a712-0b178452ae66/versions/SNAPSHOT/datatypes/Foo.schema.json")
    }

    @Test
    fun `schema id for specific version`() {
        val appId = UUID.fromString("191c7c65-1cde-4220-b695-9d814bf7b4e8")
        val version = 724.toString()
        val datatypeName = "Bar"
        assertThat(jsonSchemaIdFor(appId, version, datatypeName))
            .isEqualTo("/apps/191c7c65-1cde-4220-b695-9d814bf7b4e8/versions/724/datatypes/Bar.schema.json")
    }
}
