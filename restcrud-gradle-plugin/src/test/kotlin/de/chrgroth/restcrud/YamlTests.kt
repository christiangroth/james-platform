package de.chrgroth.restcrud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class YamlTests {

    // TODO testcases
    // - happy path: full fledged model
    // - no/empty packageName
    // - no/empty datamodel
    // - duplicate datamodels
    // - datamodel no/empty name
    // - datamodel no/empty keys
    // - datamodel keys with undefined attributes
    // - datamodel empty/invalid endpoint
    // - datamodel no/empty attributes
    // - datamodel invalid attributes
    // - datamodel duplicate attributes

    @Test
    fun foo() {
        val data = File("src/test/resources/datamodel-full.yaml")
        val parseResult = YamlUtils.parse(data)
        assertNotNull(parseResult)
        println(parseResult)
    }
}
