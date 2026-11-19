package io.github.mangi.eta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android compiles regular expressions with ICU, not java.util.regex. ICU reads `[:` as the start
 * of a POSIX class such as `[:alpha:]`, so a class like `[:!]` that the JVM accepts throws
 * `PatternSyntaxException: Incorrect Unicode property` on a device. JVM tests cannot see that, so
 * forbid the construct in string literals of the app sources; write `[!:]` instead.
 */
class AndroidRegexPortabilityTest {
    @Test fun noCharacterClassStartsWithAColon() {
        val root = File("src/main/kotlin")
        assertTrue("Run from the app module", root.isDirectory)
        val literal = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")
        val offenders = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> literal.findAll(line).any { "[:" in it.value } }
                .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
        }.toList()
        assertTrue("ICU rejects '[:' character classes:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
