package de.chrgroth

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import net.swiftzer.semver.SemVer

val semVerModule: Module = SimpleModule("SemVerModule", Version.unknownVersion())
    .addSerializer(SemVer::class.java, object : JsonSerializer<SemVer>() {
        override fun serialize(value: SemVer?, gen: JsonGenerator?, serializers: SerializerProvider?) {
            if (gen == null) {
                return
            }

            if (value == null) {
                gen.writeNull()
            } else {
                gen.writeString(value.toString())
            }
        }
    })
    .addDeserializer(SemVer::class.java, object : JsonDeserializer<SemVer>() {
        override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?) =
            p?.readValueAs(String::class.java)?.toSemVer()
    })

fun String.toSemVer(): SemVer? =
    try {
        SemVer.parse(this)
    } catch (e: IllegalArgumentException) {
        null
    }
