package io.github.mangi.eta.data.update

import io.github.mangi.eta.BuildConfig
import org.junit.Assert.assertThrows
import org.junit.Test

class ApkVerificationTest {
    private val installed = ApkIdentity(BuildConfig.APPLICATION_ID, 2099000000, setOf("example-cert"))

    @Test fun normalUpdatesRequireTheExpectedNewerVersion() {
        val newerVersion = installed.version + 1
        ApkVerification.verifyIdentity(installed.copy(version = newerVersion), installed, newerVersion, allowSameVersion = false)
        assertThrows(IllegalArgumentException::class.java) {
            ApkVerification.verifyIdentity(installed, installed, installed.version, allowSameVersion = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApkVerification.verifyIdentity(installed.copy(version = newerVersion), installed, newerVersion + 1, allowSameVersion = false)
        }
    }

    @Test fun devPreviewCanReplaceItsOwnFixedVersionButCannotDowngrade() {
        ApkVerification.verifyIdentity(installed, installed, installed.version, allowSameVersion = true)
        val olderVersion = installed.version - 1
        assertThrows(IllegalArgumentException::class.java) {
            ApkVerification.verifyIdentity(installed.copy(version = olderVersion), installed, olderVersion, allowSameVersion = true)
        }
    }

    @Test fun devPreviewRejectsOtherPackagesAndSigners() {
        for (archive in listOf(installed.copy(packageName = "example.other"), installed.copy(signers = setOf("other-cert")),
            installed.copy(signers = emptySet()), installed.copy(signers = setOf("example-cert", "other-cert")))) {
            assertThrows(IllegalArgumentException::class.java) {
                ApkVerification.verifyIdentity(archive, installed, installed.version, allowSameVersion = true)
            }
        }
    }
}
