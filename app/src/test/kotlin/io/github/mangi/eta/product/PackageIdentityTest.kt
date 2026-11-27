package io.github.mangi.eta.product

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageIdentityTest {
    @Test fun appSourcesUseTheVariantPackageIdentity() {
        val root = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val packageId = "fr.loevan.jeancalcul.v2".toByteArray()
        val offenders = root.walkTopDown().filter { it.isFile }.filter { file ->
            val bytes = file.readBytes()
            (0..bytes.size - packageId.size).any { offset ->
                packageId.indices.all { bytes[offset + it] == packageId[it] }
            }
        }.map { it.relativeTo(root).invariantSeparatorsPath }.sorted().toList()
        assertTrue("Derive the package identity from the variant: ${offenders.joinToString()}", offenders.isEmpty())
    }
}
