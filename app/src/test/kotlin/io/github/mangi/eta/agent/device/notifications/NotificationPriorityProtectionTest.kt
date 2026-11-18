package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Process
import android.service.notification.StatusBarNotification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationPriorityProtectionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun makeNotification(pkg: String, title: String, message: String,
                                 category: String?, flags: Int = 0): StatusBarNotification {
        val info = PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg }
        }
        shadowOf(context.packageManager).installPackage(info)
        val notification = Notification.Builder(context, "brief-test")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCategory(category)
            .build().apply { this.flags = flags }
        return StatusBarNotification(pkg, pkg, 17, "brief-test", 1000, 0, 0,
            notification, Process.myUserHandle(), System.currentTimeMillis())
    }

    private fun canDismiss(sbn: StatusBarNotification, topic: String): Boolean {
        val title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return NotificationProtection.safeForBrief(context, sbn,
            NotificationPriorityInput(sbn.packageName, title, text,
                androidCategory = sbn.notification.category, topic = topic))
    }

    @Test fun routineSystemUpdateCanBeClearedEvenWhenAndroidAppIsProtected() {
        val sbn = makeNotification("com.android.updater", "Redémarrage de votre téléphone",
            "Mise à jour logicielle disponible", Notification.CATEGORY_SYSTEM)
        assertTrue(canDismiss(sbn, "system"))
    }

    @Test fun workEmailsAndRoutineServiceNotificationsAreNowClearable() {
        assertTrue(canDismiss(makeNotification("com.microsoft.office.outlook", "FOUCAULT Adeline",
            "Agile Laval s'installe demain", Notification.CATEGORY_EMAIL), "work"))
        assertTrue(canDismiss(makeNotification("org.example.service", "Synchronisation terminée",
            "Sauvegarde hebdomadaire terminée", Notification.CATEGORY_SERVICE), "system"))
        assertTrue(canDismiss(makeNotification("org.example.service", "Connexion au serveur",
            "Synchronisation du profil effectuée", Notification.CATEGORY_SERVICE), "system"))
    }

    @Test fun socialNotificationsCanBeClearedButConversationsCannot() {
        assertTrue(canDismiss(makeNotification("com.snapchat.android", "Théo",
            "vous a envoyé une demande d'ami", Notification.CATEGORY_MESSAGE), "social"))
        assertTrue(canDismiss(makeNotification("com.snapchat.android", "Eva",
            "est en train d'écrire", Notification.CATEGORY_MESSAGE), "social"))
        assertTrue(canDismiss(makeNotification("com.snapchat.android", "Snapchat",
            "Découvrez les contenus de Spotlight", Notification.CATEGORY_MESSAGE), "social"))
        assertFalse(canDismiss(makeNotification("com.snapchat.android", "Eva",
            "vous a envoyé un Chat", Notification.CATEGORY_MESSAGE), "messages"))
        assertFalse(canDismiss(makeNotification("com.snapchat.android", "Eva",
            "Salut, tu viens ?", Notification.CATEGORY_MESSAGE), "messages"))
    }

    @Test fun workAndAiRulesRespectAndroidActionTimeProtection() {
        assertTrue(canDismiss(makeNotification("com.openai.chatgpt", "ChatGPT",
            "Votre tâche terminée : résultat disponible", Notification.CATEGORY_MESSAGE), "development"))
        assertTrue(canDismiss(makeNotification("com.github.android", "GitHub Actions",
            "Build successful on PR #72", Notification.CATEGORY_MESSAGE), "development"))
        assertFalse(canDismiss(makeNotification("com.github.android", "GitHub Actions",
            "CI failed on PR #72: tests failed", Notification.CATEGORY_MESSAGE), "development"))
        assertFalse(canDismiss(makeNotification("com.microsoft.office.outlook", "Adeline",
            "Bonjour, pourrais-tu vérifier le rapport ?", Notification.CATEGORY_EMAIL), "work"))
        assertFalse(canDismiss(makeNotification("com.microsoft.teams", "Collègue",
            "Salut, tu peux me répondre ?", Notification.CATEGORY_MESSAGE), "work"))
    }

    @Test fun futureCalendarEventsCanBeDismissedButTodayAndChangesCannot() {
        assertTrue(canDismiss(makeNotification("com.google.android.calendar", "Agenda",
            "Rendez-vous confirmé demain", Notification.CATEGORY_EVENT), "calendar"))
        assertTrue(canDismiss(makeNotification("com.google.android.calendar", "Agenda",
            "Événements suggérés près de chez vous", Notification.CATEGORY_EVENT), "calendar"))
        assertTrue(canDismiss(makeNotification("com.google.android.calendar", "Agenda",
            "Rappel expiré : consulter les devoirs", Notification.CATEGORY_REMINDER), "calendar"))
        assertFalse(canDismiss(makeNotification("com.google.android.calendar", "Agenda",
            "Rendez-vous aujourd'hui à 16h", Notification.CATEGORY_EVENT), "calendar"))
        assertFalse(canDismiss(makeNotification("com.google.android.calendar", "Agenda",
            "Rendez-vous annulé demain", Notification.CATEGORY_EVENT), "calendar"))
        assertFalse(canDismiss(makeNotification("com.google.android.calendar", "Agenda",
            "Rendez-vous important", Notification.CATEGORY_EVENT), "calendar"))
    }

    @Test fun recognizedRoutineSystemNotificationsCanBeClearedButCriticalCannot() {
        assertTrue(canDismiss(makeNotification("com.android.settings", "Système",
            "Mise à jour du système disponible", Notification.CATEGORY_SYSTEM), "system"))
        assertTrue(canDismiss(makeNotification("com.android.settings", "Système",
            "Mise à jour de sécurité disponible", Notification.CATEGORY_SYSTEM), "system"))
        assertFalse(canDismiss(makeNotification("com.android.settings", "Système",
            "Mise à jour de sécurité disponible : code de connexion 112233", Notification.CATEGORY_SYSTEM), "system"))
        assertTrue(canDismiss(makeNotification("com.android.vending", "Play Store",
            "Mise à jour de l'application terminée", Notification.CATEGORY_SYSTEM), "system"))
        assertTrue(canDismiss(makeNotification("com.samsung.android.scloud", "Cloud",
            "Sauvegarde terminée avec succès", Notification.CATEGORY_SYSTEM), "system"))
        assertTrue(canDismiss(makeNotification("com.android.settings", "Conseils Android",
            "Conseils Android : découvrez les fonctionnalités", Notification.CATEGORY_SYSTEM), "system"))
        assertFalse(canDismiss(makeNotification("com.android.settings", "Système",
            "Batterie critique : 2 %", Notification.CATEGORY_SYSTEM), "system"))
        assertFalse(canDismiss(makeNotification("com.android.settings", "Cloud",
            "Échec de sauvegarde", Notification.CATEGORY_SYSTEM), "system"))
        assertFalse(canDismiss(makeNotification("com.android.settings", "Système",
            "Mise à jour système disponible", Notification.CATEGORY_SYSTEM,
            Notification.FLAG_FOREGROUND_SERVICE), "system"))
        assertFalse(canDismiss(makeNotification("com.android.settings", "Système",
            "Opération en cours", Notification.CATEGORY_SYSTEM), "system"))
    }

    @Test fun shoppingAlertsCanBeClearedButMessagesAndIncidentsCannot() {
        assertTrue(canDismiss(makeNotification("fr.leboncoin", "Recherche",
            "3 nouvelles annonces correspondent à votre recherche", Notification.CATEGORY_MESSAGE), "shopping"))
        assertTrue(canDismiss(makeNotification("fr.leboncoin", "Leboncoin",
            "Annonce sponsorisée : découvrez ce vélo", Notification.CATEGORY_MESSAGE), "shopping"))
        assertTrue(canDismiss(makeNotification("com.amazon.mShop.android.shopping", "Amazon",
            "Votre commande a été expédiée", null), "shopping"))
        assertTrue(canDismiss(makeNotification("com.laposte.laposte", "La Poste",
            "Votre colis est en cours de livraison", null), "shopping"))
        assertFalse(canDismiss(makeNotification("fr.leboncoin", "Pierre",
            "Pierre vous a envoyé un message sur votre annonce", Notification.CATEGORY_MESSAGE), "shopping"))
        assertFalse(canDismiss(makeNotification("fr.leboncoin", "Leboncoin",
            "Paiement refusé : transaction à vérifier", null), "shopping"))
        assertFalse(canDismiss(makeNotification("com.laposte.laposte", "Livraison",
            "Votre colis : adresse incorrecte, action requise", null), "shopping"))
        assertFalse(canDismiss(makeNotification("com.laposte.laposte", "La Poste",
            "Votre colis est en cours de livraison, code 123456", null), "shopping"))
        assertFalse(canDismiss(makeNotification("com.amazon.mShop.android.shopping", "Amazon",
            "Votre commande a été expédiée", null, Notification.FLAG_FOREGROUND_SERVICE), "shopping"))
    }

    @Test fun neverClearSecurityMessagesCallsAndOngoingAlerts() {
        assertFalse(canDismiss(makeNotification("com.google.android.gm", "Google",
            "Alerte de sécurité : connexion inconnue", Notification.CATEGORY_EMAIL), "security"))
        assertFalse(canDismiss(makeNotification("com.snapchat.android", "Eva",
            "vous a envoyé un Chat", Notification.CATEGORY_MESSAGE), "messages"))
        assertFalse(canDismiss(makeNotification("org.example.voip", "Appel en cours",
            "Répondez maintenant", Notification.CATEGORY_CALL), "other"))
        assertFalse(canDismiss(makeNotification("org.example.service", "Synchronisation terminée",
            "Service démarré", Notification.CATEGORY_SERVICE,
            Notification.FLAG_FOREGROUND_SERVICE), "system"))
        assertFalse(canDismiss(makeNotification("com.android.updater", "Alerte batterie",
            "Votre batterie est endommagée", Notification.CATEGORY_SYSTEM), "system"))
    }
}
