package fr.loevan.jeancalcul.feature.tasks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesLocalTaskStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun clearStore() {
        context.getSharedPreferences("local_tasks", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun taskSurvivesStoreRecreationAndActionReplay() {
        val first = SharedPreferencesLocalTaskStore(context)
        val created = first.create("action-1", "Acheter du lait", null, null, 1_000L)
        val replayed =
            SharedPreferencesLocalTaskStore(context)
                .create("action-1", "Titre ignore lors du rejeu", null, null, 2_000L)

        assertEquals(created, replayed)
        assertEquals(listOf(created), SharedPreferencesLocalTaskStore(context).list())
    }
}
