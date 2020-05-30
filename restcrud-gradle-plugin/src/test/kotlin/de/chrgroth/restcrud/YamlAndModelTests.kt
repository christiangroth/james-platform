package de.chrgroth.restcrud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.IllegalStateException

class YamlAndModelTests {

    @Test
    fun invalidYaml() {
        assertThrows(IllegalStateException::class.java) {
            println(YamlUtils.load(File("src/test/resources/invalid-yaml.yaml")))
        }
    }

    @Test
    fun noPackageName() {
        assertThrows(IllegalStateException::class.java) {
            YamlUtils.load(File("src/test/resources/no-packageName.yaml"))
        }
    }

    @Test
    fun noDatamodel() {
        assertThrows(IllegalStateException::class.java) {
            YamlUtils.load(File("src/test/resources/no-datamodel.yaml"))
        }
    }

    @Test
    fun emptyDatamodel() {
        val parseResult = YamlUtils.load(File("src/test/resources/empty-datamodel.yaml"))
        assertEquals("de.foo.bar", parseResult.packageName)
        assertEquals(0, parseResult.datamodel.size)
    }

    @Test
    fun nullDatamodel() {
        val parseResult = YamlUtils.load(File("src/test/resources/null-datamodel.yaml"))
        assertEquals("de.foo.bar", parseResult.packageName)
        assertEquals(1, parseResult.datamodel.size)
        assertNull(parseResult.datamodel[0])
    }

    @Test
    fun duplicateDatamodel() {
        val parseResult = YamlUtils.load(File("src/test/resources/duplicate-datamodel.yaml"))
        assertEquals("de.foo.bar", parseResult.packageName)
        assertEquals(2, parseResult.datamodel.size)
        assertEquals("foo", parseResult.datamodel[0].name)
        assertEquals("foos", parseResult.datamodel[0].endpoint)
        assertEquals(listOf("id"), parseResult.datamodel[0].key)
        assertEquals(2, parseResult.datamodel[0].attributes.size)
        assertEquals("id Long", parseResult.datamodel[0].attributes[0])
        assertEquals("description String?", parseResult.datamodel[0].attributes[1])
        assertEquals("foo", parseResult.datamodel[1].name)
        assertEquals("foos", parseResult.datamodel[1].endpoint)
        assertEquals(listOf("id"), parseResult.datamodel[1].key)
        assertEquals(2, parseResult.datamodel[1].attributes.size)
        assertEquals("id Long", parseResult.datamodel[1].attributes[0])
        assertEquals("description String?", parseResult.datamodel[1].attributes[1])
    }

    @Test
    fun noName() {
        assertThrows(IllegalStateException::class.java) {
            YamlUtils.load(File("src/test/resources/no-name.yaml"))
        }
    }

    @Test
    fun noKey() {
        assertThrows(IllegalStateException::class.java) {
            YamlUtils.load(File("src/test/resources/no-key.yaml"))
        }
    }

    @Test
    fun emptyKeys() {
        val parseResult = YamlUtils.load(File("src/test/resources/empty-key.yaml"))
        assertEquals("de.foo.bar", parseResult.packageName)
        assertEquals(1, parseResult.datamodel.size)
        assertEquals("foo", parseResult.datamodel[0].name)
        assertNull(parseResult.datamodel[0].endpoint)
        assertEquals(emptyList<String>(), parseResult.datamodel[0].key)
        assertEquals(1, parseResult.datamodel[0].attributes.size)
        assertEquals("id Long", parseResult.datamodel[0].attributes[0])
    }

    @Test
    fun noEndpoint() {
        val parseResult = YamlUtils.load(File("src/test/resources/no-endpoint.yaml"))
        assertEquals("de.foo.bar", parseResult.packageName)
        assertEquals(1, parseResult.datamodel.size)
        assertEquals("foo", parseResult.datamodel[0].name)
        assertNull(parseResult.datamodel[0].endpoint)
        assertEquals(listOf("id"), parseResult.datamodel[0].key)
        assertEquals(1, parseResult.datamodel[0].attributes.size)
        assertEquals("id Long", parseResult.datamodel[0].attributes[0])
    }

    @Test
    fun noAttributes() {
        assertThrows(IllegalStateException::class.java) {
            YamlUtils.load(File("src/test/resources/no-attributes.yaml"))
        }
    }

    @Test
    fun emptyAttributes() {
        val parseResult = YamlUtils.load(File("src/test/resources/empty-attributes.yaml"))
        assertEquals("de.foo.bar", parseResult.packageName)
        assertEquals(1, parseResult.datamodel.size)
        assertEquals("foo", parseResult.datamodel[0].name)
        assertEquals("foos", parseResult.datamodel[0].endpoint)
        assertEquals(listOf("id"), parseResult.datamodel[0].key)
        assertEquals(0, parseResult.datamodel[0].attributes.size)
    }

    @Test
    fun invalidAttributeFormat() {
        val parseResult = YamlUtils.load(File("src/test/resources/invalid-attribute-format.yaml"))
        assertEquals("de.foo.bar", parseResult.packageName)
        assertEquals(1, parseResult.datamodel.size)
        assertEquals("foo", parseResult.datamodel[0].name)
        assertEquals("foos", parseResult.datamodel[0].endpoint)
        assertEquals(listOf("id"), parseResult.datamodel[0].key)
        assertEquals(1, parseResult.datamodel[0].attributes.size)
        assertEquals("id Long Key", parseResult.datamodel[0].attributes[0])
    }

    @Test
    fun invalidAttributeName() {
        val parseResult = YamlUtils.load(File("src/test/resources/invalid-attribute-name.yaml"))
        assertEquals("de.foo.bar", parseResult.packageName)
        assertEquals(1, parseResult.datamodel.size)
        assertEquals("foo", parseResult.datamodel[0].name)
        assertEquals("foos", parseResult.datamodel[0].endpoint)
        assertEquals(listOf("id"), parseResult.datamodel[0].key)
        assertEquals(1, parseResult.datamodel[0].attributes.size)
        assertEquals("some id Long", parseResult.datamodel[0].attributes[0])
    }

    @Test
    fun invalidAttributeTypePostfix() {
        val parseResult = YamlUtils.load(File("src/test/resources/invalid-attribute-type-postfix.yaml"))
        assertEquals("de.foo.bar", parseResult.packageName)
        assertEquals(1, parseResult.datamodel.size)
        assertEquals("foo", parseResult.datamodel[0].name)
        assertEquals("foos", parseResult.datamodel[0].endpoint)
        assertEquals(listOf("id"), parseResult.datamodel[0].key)
        assertEquals(1, parseResult.datamodel[0].attributes.size)
        assertEquals("id Long:", parseResult.datamodel[0].attributes[0])
    }

    @Test
    fun duplicateAttribute() {
        val parseResult = YamlUtils.load(File("src/test/resources/duplicate-attribute.yaml"))
        assertEquals("de.foo.bar", parseResult.packageName)
        assertEquals(1, parseResult.datamodel.size)
        assertEquals("foo", parseResult.datamodel[0].name)
        assertEquals("foos", parseResult.datamodel[0].endpoint)
        assertEquals(listOf("id"), parseResult.datamodel[0].key)
        assertEquals(2, parseResult.datamodel[0].attributes.size)
        assertEquals("id Long", parseResult.datamodel[0].attributes[0])
        assertEquals("id Long", parseResult.datamodel[0].attributes[1])
    }

    @Test
    fun fullModel() {
        val parseResult = YamlUtils.load(File("src/test/resources/full.yaml"))
        assertEquals("de.foo.bar", parseResult.packageName)
        assertEquals(2, parseResult.datamodel.size)
        assertEquals("foo", parseResult.datamodel[0].name)
        assertEquals("foos", parseResult.datamodel[0].endpoint)
        assertEquals(listOf("id"), parseResult.datamodel[0].key)
        assertEquals(2, parseResult.datamodel[0].attributes.size)
        assertEquals("id Long", parseResult.datamodel[0].attributes[0])
        assertEquals("description String?", parseResult.datamodel[0].attributes[1])
        assertEquals("bar", parseResult.datamodel[1].name)
        assertEquals("bars", parseResult.datamodel[1].endpoint)
        assertEquals(listOf("id", "version"), parseResult.datamodel[1].key)
        assertEquals(2, parseResult.datamodel[1].attributes.size)
        assertEquals("id Long", parseResult.datamodel[1].attributes[0])
        assertEquals("version Long", parseResult.datamodel[1].attributes[1])
    }
}
