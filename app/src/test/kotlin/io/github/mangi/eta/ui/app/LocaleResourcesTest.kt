package io.github.mangi.eta.ui.app

import android.content.Context
import android.content.res.Configuration
import io.github.mangi.eta.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocaleResourcesTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun everySystemLocaleUsesEnglishProductResources() {
        for (locale in listOf("en-US", "zh-CN", "zh-SG", "zh-TW", "zh-HK", "fr-FR")) {
            assertEquals("Settings", localizedString(locale, R.string.route_settings))
            assertEquals("Yesterday", localizedString(locale, R.string.time_yesterday))
            assertEquals("Appearance & Theme", localizedString(locale, R.string.appearance_title))
            assertEquals("Current device", localizedString(locale, R.string.capability_current_device))
            assertEquals("Jean-Calcul is running", localizedString(locale, R.string.execution_title))
            assertEquals("Environment configuration", localizedString(locale, R.string.linux_environment_configuration))
            assertEquals("Skills", localizedString(locale, R.string.route_skills))
            assertEquals("API Key", localizedString(locale, R.string.speech_api_key))
            assertEquals("2 models", localizedQuantity(locale, R.plurals.provider_models_count, 2))
        }
    }

    @Suppress("DEPRECATION")
    private fun localizedString(languageTag: String, resourceId: Int): String {
        val resources = context.resources
        val configuration = Configuration(resources.configuration).apply {
            setLocale(Locale.forLanguageTag(languageTag))
        }
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return resources.getString(resourceId)
    }

    @Suppress("DEPRECATION")
    private fun localizedQuantity(languageTag: String, resourceId: Int, quantity: Int): String {
        val resources = context.resources
        val configuration = Configuration(resources.configuration).apply {
            setLocale(Locale.forLanguageTag(languageTag))
        }
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return resources.getQuantityString(resourceId, quantity, quantity)
    }
}
