package de.chrgroth.restcrud

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class GradleTests {

    @TempDir
    lateinit var testProjectDir: Path

    private lateinit var settingsFile: File
    private lateinit var buildFile: File
    private lateinit var gradle: GradleRunner

    @BeforeEach
    fun setup() {
        settingsFile = testProjectDir.resolve("settings.gradle.kts").toFile().apply {
            createNewFile()
            writeText("""rootProject.name = "restCrud-test"""".trimIndent())
        }

        buildFile = testProjectDir.resolve("build.gradle.kts").toFile().apply {
            createNewFile()
        }

        gradle = GradleRunner.create().withProjectDir(testProjectDir.toFile())
    }

    @Test
    fun `test helloWorld task`() {
        buildFile.writeText("""
            tasks.register("helloWorld") {
                doLast {
                    println("Hello world!")
                }
            }
        """.trimIndent())

        val result = gradle.withArguments("helloWorld").build()
        assertTrue(result.output.contains("Hello world!"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":helloWorld")?.outcome)
    }
}