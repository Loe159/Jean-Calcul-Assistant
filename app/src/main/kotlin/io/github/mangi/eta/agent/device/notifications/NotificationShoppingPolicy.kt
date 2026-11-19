package io.github.mangi.eta.agent.device.notifications


/** Decisions validated for purchases, delivery tracking and marketplace alerts. */
internal enum class ShoppingNotificationKind {
    DIRECT_MESSAGE, PAYMENT_OR_DISPUTE, DELIVERY_ACTION,
    SEARCH_MATCH, ORDER_TRACKING,
    ADVERTISING, OFF_TOPIC_RECOMMENDATION, SPONSORED_OFFER, COMMERCIAL_REMINDER,
    UNKNOWN,
}

internal object NotificationShoppingPolicy {
    private val marketplaceApps = setOf(
        "fr.leboncoin", "fr.vinted", "com.ebay.mobile", "com.amazon.mShop.android.shopping",
        "com.cdiscount.app", "com.alibaba.aliexpresshd", "com.wallapop", "com.etsy.android",
    )
    private val deliveryApps = setOf(
        "fr.mondialrelay", "com.laposte.laposte", "com.chronopost.chronopost",
        "com.dhl.exp.dhlmobile", "com.ups.mobile.android", "com.aftership.AfterShip",
    )
    fun isShoppingApp(pkg: String) = pkg in marketplaceApps || pkg in deliveryApps

    private val direct = Regex(
        "(?iu)(nouveau message (?:de |d['’]|d’un|d’une|d'un)|" +
            "(?:vous|t['’]|te) a (?:envoy[ée] (?:un )?message|[ée]crit|contact[ée])|" +
            "(?:un |une )?(?:acheteur|vendeur|vendeuse) (?:vous |t['’])?(?:contacte|[ée]crit|a envoy[ée] un message)|" +
            "(?:acheteur|vendeur|vendeuse).{0,50}(?:vous pose une question|a r[ée]pondu)|" +
            "new (?:direct )?message (?:from|by)|" +
            "(?:buyer|seller) (?:sent you a message|messaged you|contacted you)|" +
            "vous avez (?:un )?nouveau message|vous a r[ée]pondu|replied to your message)"
    )
    private val incident = Regex(
        "(?iu)(litige|dispute (?:opened|reported|with)|" +
            "(?:paiement|pr[ée]l[èe]vement|transaction|payment|charge).{0,55}(?:refus[ée]|[ée]chou[ée]|bloqu[ée]|probl[èe]me|failed|declined|rejected|disputed)|" +
            "(?:[ée]chec|probl[èe]me|refus|blocage).{0,45}(?:paiement|transaction)|" +
            "(?:remboursement|refund).{0,45}(?:litige|contest[ée]|refus[ée]|dispute|failed|action requise)|" +
            "(?:fraude|arnaque|escroquerie|fraudulent|scam))"
    )
    private val deliveryAction = Regex(
        "(?iu)(livraison (?:impossible|[ée]chou[ée]e|suspendue|bloqu[ée]e)|" +
            "(?:colis|commande|delivery|shipment).{0,65}(?:action (?:requise|n[ée]cessaire)|adresse (?:incorrecte|incompl[èe]te)|" +
            "lost|perdu|missing|[àa] reprogrammer|failed|[ée]chou[ée])|" +
            "(?:adresse (?:incorrecte|incompl[èe]te)|point relais indisponible|douane [àa] r[ée]gler|customs (?:payment|action) required)|" +
            "(?:action required|please confirm (?:your )?(?:address|delivery)|delivery (?:failed|exception|attempt failed)))"
    )
    private val sponsored = Regex(
        "(?iu)(sponsoris[ée]e?|sponsored|publicit[ée]|advertisement|promoted (?:listing|item|offer))"
    )
    private val advertising = Regex(
        "(?iu)(code promo|offre sp[ée]ciale|vente flash|r[ée]duction|" +
            "(?:promo(?:tion)?|soldes|black friday|offres exclusives|" +
            "coupon|limited.time offer|discount|flash sale|save [0-9]+%|livraison gratuite aujourd'hui))"
    )
    private val reminders = Regex(
        "(?iu)((?:n['’]oubliez pas|vous avez oubli[ée]|encore temps de|last chance|don't forget).{0,65}(?:panier|acheter|commande|achat|offre|promotion)|" +
            "(?:panier abandonn[ée]|abandoned cart|finalisez votre achat|complete your purchase|" +
            "revenez voir nos offres|dernier rappel commercial|votre panier vous attend))"
    )
    private val recommendations = Regex(
        "(?iu)((?:annonces?|articles?|produits?|offres?) (?:recommand[ée]e?s?|sugg[ée]r[ée]e?s?|pour vous|qui pourraient vous plaire)|" +
            "(?:vous pourriez aimer|vous aimerez aussi|s[ée]lection pour vous|" +
            "recommended for you|you might (?:also )?like|suggested (?:items|listings|products)|" +
            "d[ée]couvrez ces annonces|hors (?:de vos )?crit[èe]res))"
    )
    private val matchedSearch = Regex(
        "(?iu)(nouvelle?s? annonces? (?:correspond(?:ent|ant)|pour (?:votre|ton) (?:recherche|alerte))|" +
            "(?:annonces?|r[ée]sultats?) correspondent [àa] (?:votre|ta) recherche|" +
            "(?:votre|ton) alerte (?:de )?recherche.{0,60}(?:annonce|nouveau|r[ée]sultat)|" +
            "(?:saved search|search alert|your search).{0,65}(?:new listing|new match|new result)|" +
            "new (?:listings?|matches?) for your (?:saved )?search|" +
            "(?:annonce|article) correspondant [àa] (?:vos|tes) crit[èe]res)"
    )
    private val tracking = Regex(
        "(?iu)(?:\\b(?:commande|colis|livraison|exp[ée]dition|suivi|order|parcel|package|shipment|delivery)\\b.{0,75}" +
            "(?:en pr[ée]paration|en cours d['’]acheminement|en route|exp[ée]di[ée]|livr[ée]|" +
            "pris en charge|en transit|en cours de livraison|en livraison|arriv[ée] au (?:d[ée]p[ôo]t|point relais)|" +
            "shipped|dispatched|out for delivery|in transit|delivered|ready for pickup|" +
            "picked up|tracking updated|status updated)|" +
            "(?:votre commande a [ée]t[ée] exp[ée]di[ée]e|order shipped|package delivered|" +
            "suivi de commande disponible))"
    )

    fun classify(pkg: String, title: String, body: String,
                 category: String? = null): ShoppingNotificationKind {
        if (!isShoppingApp(pkg)) return ShoppingNotificationKind.UNKNOWN
        val text = "$title $body"
        // An unambiguous direct message or problem is never hidden behind advertising words.
        if (direct.containsMatchIn(text)) return ShoppingNotificationKind.DIRECT_MESSAGE
        if (incident.containsMatchIn(text)) return ShoppingNotificationKind.PAYMENT_OR_DISPUTE
        if (deliveryAction.containsMatchIn(text)) return ShoppingNotificationKind.DELIVERY_ACTION
        // Some marketplaces incorrectly use CATEGORY_MESSAGE for recommendations.
        if (sponsored.containsMatchIn(text)) return ShoppingNotificationKind.SPONSORED_OFFER
        if (advertising.containsMatchIn(text)) return ShoppingNotificationKind.ADVERTISING
        if (reminders.containsMatchIn(text)) return ShoppingNotificationKind.COMMERCIAL_REMINDER
        if (matchedSearch.containsMatchIn(text)) return ShoppingNotificationKind.SEARCH_MATCH
        if (tracking.containsMatchIn(text)) return ShoppingNotificationKind.ORDER_TRACKING
        if (recommendations.containsMatchIn(text)) return ShoppingNotificationKind.OFF_TOPIC_RECOMMENDATION
        // An unknown CATEGORY_MESSAGE is treated as a real conversation by the global guard.
        return ShoppingNotificationKind.UNKNOWN
    }

    fun priority(kind: ShoppingNotificationKind): NotificationPriority? = when (kind) {
        ShoppingNotificationKind.DIRECT_MESSAGE, ShoppingNotificationKind.PAYMENT_OR_DISPUTE,
        ShoppingNotificationKind.DELIVERY_ACTION -> NotificationPriority.NOW
        ShoppingNotificationKind.SEARCH_MATCH, ShoppingNotificationKind.ORDER_TRACKING ->
            NotificationPriority.TODAY
        ShoppingNotificationKind.ADVERTISING, ShoppingNotificationKind.OFF_TOPIC_RECOMMENDATION,
        ShoppingNotificationKind.SPONSORED_OFFER, ShoppingNotificationKind.COMMERCIAL_REMINDER ->
            NotificationPriority.LOW
        ShoppingNotificationKind.UNKNOWN -> null
    }

    fun allowMessageCategoryDismiss(kind: ShoppingNotificationKind): Boolean = kind in setOf(
        ShoppingNotificationKind.SEARCH_MATCH, ShoppingNotificationKind.ORDER_TRACKING,
        ShoppingNotificationKind.ADVERTISING, ShoppingNotificationKind.OFF_TOPIC_RECOMMENDATION,
        ShoppingNotificationKind.SPONSORED_OFFER, ShoppingNotificationKind.COMMERCIAL_REMINDER)
}
