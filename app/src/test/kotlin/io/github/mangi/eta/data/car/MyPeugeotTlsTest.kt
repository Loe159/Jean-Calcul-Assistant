package io.github.mangi.eta.data.car

import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MyPeugeotTlsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun extractsClientIdentityFromSyntheticPkcs12InsideAnApk() {
        val pfx = folder.root.resolve("synthetic.pfx")
        val password = "fixture-password"
        val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
        val process = ProcessBuilder(
            keytool, "-genkeypair", "-alias", "fixture", "-dname", "CN=Synthetic client",
            "-keyalg", "RSA", "-validity", "1", "-storetype", "PKCS12",
            "-keystore", pfx.absolutePath, "-storepass", password,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.waitFor())

        val apk = folder.newFile("synthetic.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("assets/MWPMYMA1.pfx"))
            pfx.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }

        val keys = readMyPeugeotKeyStore(apk.absolutePath, password.toCharArray())
        assertTrue(keys.isKeyEntry("fixture"))
        assertEquals(1, keys.getCertificateChain("fixture").size)
        assertNull(runCatching { myPeugeotHttp(apk.absolutePath, password.toCharArray()) }.exceptionOrNull())
        assertTrue(runCatching { readMyPeugeotKeyStore(apk.absolutePath, "wrong-password".toCharArray()) }
            .exceptionOrNull() is IOException)
    }

    @Test(expected = IOException::class)
    fun rejectsAnApkWithoutTheClientIdentity() {
        val apk = folder.newFile("without-pfx.apk")
        ZipOutputStream(apk.outputStream()).use { }
        readMyPeugeotKeyStore(apk.absolutePath, "fixture-password".toCharArray())
    }
}
