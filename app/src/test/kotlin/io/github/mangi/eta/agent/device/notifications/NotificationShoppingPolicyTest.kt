package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationShoppingPolicyTest {
    private val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
        action = NotificationFilterAction.DISMISS)
    private val base = NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "NLI non concluant")

    @Test fun approvedShoppingCategoriesMapToAllThreePriorities() {
        data class Fixture(val pkg: String, val title: String, val body: String,
            val category: String?, val kind: ShoppingNotificationKind, val tier: NotificationPriority)
        val cases = listOf(
            Fixture("fr.leboncoin", "Pierre", "Pierre vous a envoyé un message sur votre annonce",
                Notification.CATEGORY_MESSAGE, ShoppingNotificationKind.DIRECT_MESSAGE, NotificationPriority.NOW),
            Fixture("fr.vinted", "Vinted", "Votre paiement a été refusé",
                null, ShoppingNotificationKind.PAYMENT_OR_DISPUTE, NotificationPriority.NOW),
            Fixture("fr.leboncoin", "Leboncoin", "Un litige a été ouvert par l'acheteur",
                null, ShoppingNotificationKind.PAYMENT_OR_DISPUTE, NotificationPriority.NOW),
            Fixture("com.laposte.laposte", "Livraison", "Votre colis : adresse incorrecte, action requise",
                null, ShoppingNotificationKind.DELIVERY_ACTION, NotificationPriority.NOW),
            Fixture("fr.leboncoin", "Mes recherches", "3 nouvelles annonces correspondent à votre recherche",
                Notification.CATEGORY_MESSAGE, ShoppingNotificationKind.SEARCH_MATCH, NotificationPriority.TODAY),
            Fixture("fr.leboncoin", "Alerte", "Votre alerte de recherche : une nouvelle annonce",
                null, ShoppingNotificationKind.SEARCH_MATCH, NotificationPriority.TODAY),
            Fixture("com.amazon.mShop.android.shopping", "Amazon", "Votre commande a été expédiée",
                null, ShoppingNotificationKind.ORDER_TRACKING, NotificationPriority.TODAY),
            Fixture("com.laposte.laposte", "La Poste", "Votre colis est en cours de livraison",
                null, ShoppingNotificationKind.ORDER_TRACKING, NotificationPriority.TODAY),
            Fixture("fr.leboncoin", "Publicité", "Profitez d'une offre spéciale !",
                Notification.CATEGORY_MESSAGE, ShoppingNotificationKind.ADVERTISING, NotificationPriority.LOW),
            Fixture("fr.leboncoin", "À découvrir", "Annonces recommandées pour vous",
                null, ShoppingNotificationKind.OFF_TOPIC_RECOMMENDATION, NotificationPriority.LOW),
            Fixture("fr.leboncoin", "Leboncoin", "Annonce sponsorisée : découvrez ce vélo",
                null, ShoppingNotificationKind.SPONSORED_OFFER, NotificationPriority.LOW),
            Fixture("fr.vinted", "Vinted", "Votre panier vous attend",
                null, ShoppingNotificationKind.COMMERCIAL_REMINDER, NotificationPriority.LOW),
        )
        cases.forEach { f ->
            val kind = NotificationShoppingPolicy.classify(f.pkg, f.title, f.body, f.category)
            assertEquals(f.body, f.kind, kind)
            val input = NotificationPriorityInput(f.pkg, f.title, f.body,
                androidCategory = f.category, topic = "shopping")
            assertEquals(f.body, f.tier, NotificationPriorityPolicy.classify(input))
            assertEquals(f.body, if (f.tier == NotificationPriority.NOW)
                NotificationFilterDecision.KEEP else NotificationFilterDecision.DISMISS,
                NotificationPriorityPolicy.automaticAction(input, config, base, 14).decision)
        }
    }

    @Test fun buyerMessageOrPaymentProblemBeatsAdKeywords() {
        val message = "L'acheteur vous a envoyé un message concernant la promotion"
        assertEquals(ShoppingNotificationKind.DIRECT_MESSAGE,
            NotificationShoppingPolicy.classify("fr.leboncoin", "Pierre", message))
        val payment = "Litige ouvert pour votre commande sponsorisée"
        assertEquals(ShoppingNotificationKind.PAYMENT_OR_DISPUTE,
            NotificationShoppingPolicy.classify("fr.leboncoin", "Transaction", payment))
        assertFalse(NotificationShoppingPolicy.allowMessageCategoryDismiss(ShoppingNotificationKind.DIRECT_MESSAGE))
        assertTrue(NotificationShoppingPolicy.allowMessageCategoryDismiss(ShoppingNotificationKind.SEARCH_MATCH))
    }

    @Test fun unknownNotificationAndForeignAppsAreNeverMistakenForKnownMarketplacePromos() {
        assertEquals(ShoppingNotificationKind.UNKNOWN,
            NotificationShoppingPolicy.classify("fr.leboncoin", "Leboncoin", "Votre compte est prêt"))
        assertEquals(ShoppingNotificationKind.UNKNOWN,
            NotificationShoppingPolicy.classify("com.google.android.gm", "Leboncoin", "Offre spéciale sponsorisée"))
        assertEquals(NotificationPriority.NOW,
            NotificationPriorityPolicy.classify(NotificationPriorityInput("fr.leboncoin", "Alice",
                "Bonjour, est-ce toujours disponible ?", androidCategory = Notification.CATEGORY_MESSAGE,
                topic = "messages")))
    }

    @Test fun filteringOffQuietHoursAndLearnedPositiveSignalsArePreserved() {
        val input = NotificationPriorityInput("fr.leboncoin", "Alerte",
            "Nouvelle annonce correspondant à votre recherche", topic = "shopping")
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input,
                config.copy(mode = NotificationFilterMode.OFF), base, 14).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input,
                config.copy(onlyQuietHours = true, quietFrom = 22, quietUntil = 7), base, 14).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input.copy(personalInterest =
                NotificationPersonalization.Interest.WANTED), config, base, 14).decision)
    }
}
