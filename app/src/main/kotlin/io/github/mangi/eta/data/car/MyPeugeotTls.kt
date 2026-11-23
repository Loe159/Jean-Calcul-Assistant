package io.github.mangi.eta.data.car

import java.io.IOException
import java.security.KeyStore
import java.util.zip.ZipFile
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal const val myPeugeotPfxAsset = "assets/MWPMYMA1.pfx"
// Mot de passe public du PFX MyPeugeot, pas un secret personnel.
private const val myPeugeotPfxPassword = "y5Y2my5B"

internal fun readMyPeugeotKeyStore(apkPath: String, password: CharArray): KeyStore =
    ZipFile(apkPath).use { zip ->
        val entry = zip.getEntry(myPeugeotPfxAsset) ?: throw IOException("Missing MyPeugeot PFX")
        zip.getInputStream(entry).use { input ->
            KeyStore.getInstance("PKCS12").apply {
                load(input, password)
                if (aliases().asSequence().none { isKeyEntry(it) && getCertificateChain(it)?.isNotEmpty() == true }) {
                    throw IOException("Missing MyPeugeot client identity")
                }
            }
        }
    }

internal fun myPeugeotHttp(apkPath: String, password: CharArray = myPeugeotPfxPassword.toCharArray()): CarHttp {
    val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
        init(readMyPeugeotKeyStore(apkPath, password), password)
    }
    val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(null as KeyStore?)
    }.trustManagers.filterIsInstance<X509TrustManager>().single()
    val tls = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, arrayOf(trust), null) }
    val client = defaultCarHttpClient.newBuilder()
        .sslSocketFactory(tls.socketFactory, trust)
        .build()
    return CarHttp(client, logDetails = false)
}
