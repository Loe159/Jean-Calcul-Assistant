package io.github.mangi.eta.data.car

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile

internal const val myPeugeotPackage = "com.psa.mym.mypeugeot"
internal const val peugeotLocale = "fr-FR"

@Serializable
internal class MyPeugeotParameters(val cvsClientId: String? = null, val cvsSecret: String? = null) {
    fun isUsable(): Boolean = !cvsClientId.isNullOrBlank() && !cvsSecret.isNullOrBlank()
}

internal fun readMyPeugeotParameters(context: Context, appInfo: ApplicationInfo): MyPeugeotParameters? {
    val manager = context.packageManager
    val localized = runCatching {
        val base = manager.getResourcesForApplication(appInfo)
        val configuration = Configuration(base.configuration).apply { setLocale(Locale.FRANCE) }
        val resources = context.createPackageContext(myPeugeotPackage, 0)
            .createConfigurationContext(configuration).resources
        val id = resources.getIdentifier("parameters", "raw", myPeugeotPackage)
        if (id == 0) null else resources.openRawResource(id).use(::parseParameters)
    }.getOrNull()
    return localized ?: appInfo.sourceDir?.let(::readParametersZip)
}

internal fun readParametersZip(path: String): MyPeugeotParameters? = runCatching {
    ZipFile(path).use { zip ->
        val entry = zip.getEntry("res/raw-fr-rFR/parameters.json") ?: return null
        zip.getInputStream(entry).use(::parseParameters)
    }
}.getOrNull()

private fun parseParameters(input: InputStream): MyPeugeotParameters? {
    val bytes = input.readNBytes(65_537)
    if (bytes.size > 65_536) return null
    return carJson.decodeFromString<MyPeugeotParameters>(bytes.toString(Charsets.UTF_8))
        .takeIf(MyPeugeotParameters::isUsable)
}
