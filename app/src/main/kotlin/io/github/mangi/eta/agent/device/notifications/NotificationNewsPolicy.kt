package io.github.mangi.eta.agent.device.notifications

import java.util.Locale

/** User-approved local editorial news triage. No online lookup or second model pass. */
internal enum class NewsNotificationKind {
    MAJOR_BREAKING, AI_TECH_CYBER, ECONOMY_POLITICS, LOCAL_USEFUL,
    FOLLOWED_MEDIA, UNSOLICITED_RECOMMENDATION, CLICKBAIT, REPEATED_HEADLINE, UNKNOWN,
}

internal object NotificationNewsPolicy {
    private val newsApps = setOf(
        "com.google.android.apps.magazines", "com.feedly", "com.noinnion.android.greader.reader",
        "com.inoreader", "fr.lemonde.android", "com.lemonde.androidapp",
        "com.lefigaro.android", "com.francetv.info", "fr.francetv.info",
        "com.radiofrance.franceinfo", "com.euronews", "com.bfmtv.android",
        "com.reuters", "com.guardian", "com.nytimes.android",
        "com.microsoft.amp.apps.bingnews", "com.google.android.apps.magazines",
        "com.squidapp.squid", "fr.lesechos",
    )
    private val newsPackage = Regex(
        "(?i)(?:^|\\.)(?:news|actualites|actualit[ée]s|feedly|inoreader|franceinfo|reuters|" +
            "euronews|lemonde|lefigaro|lesechos|liberation|guardian|bfmtv)(?:\\.|$)"
    )
    private val editorialContext = Regex(
        "(?iu)(actualit[ée]s?|derni[èe]re minute|flash info|breaking news|" +
            "(?:nouvel|dernier) article|article (?:publi[ée]|disponible|du jour)|" +
            "(?:veille|revue de presse|journal|m[ée]dia|presse|news|headline))"
    )
    private val urgentLead = Regex(
        "(?iu)(alerte info|alerte actualit[ée]|flash sp[ée]cial|" +
            "(?:flash|info) (?:urgent|sp[ée]cial)|derni[èe]re minute|breaking news|" +
            "urgent\\s*[:!]|en direct\\s*[:]|breaking\\s*[:!])"
    )
    private val majorEvent = Regex(
        "(?iu)(catastrophe|s[ée]isme|tremblement de terre|tsunami|" +
            "attentat|attaque terroriste|attaque majeure|incendie majeur|inondations? majeures?|" +
            "[ée]vacuation|accident grave|[ée]tat d['’]urgence|" +
            "r[ée]sultats? (?:(?:d['’]|des |de l['’]))?[ée]lections?|d[ée]mission du (?:pr[ée]sident|premier ministre)|" +
            "cyberattaque (?:massive|majeure)|panne (?:nationale|g[ée]n[ée]ralis[ée]e)|" +
            "major earthquake|terrorist attack|massive outage|election results|evacuation order)"
    )
    private val aiTechCyber = Regex(
        "(?iu)(intelligence artificielle|(?:\\bIA\\b)|\\bAI\\b|" +
            "(?:LLM|GPT|Qwen|Gemini|Claude|OpenAI|DeepMind|Nvidia|GPU)\\b|" +
            "cybers[ée]curit[ée]|cybersecurity|cyberattaque|ran[çc]ongiciel|ransomware|" +
            "vuln[ée]rabilit[ée]|zero.day|CVE-[0-9]{4}-[0-9]+|" +
            "cloud|informatique|open.source|semi.conducteurs?|processeurs?|" +
            "tech(?:nologie)?s?|logiciels?|satellites?|data centers?|robotique)"
    )
    private val economyPolitics = Regex(
        "(?iu)([ée]conomie|[ée]conomique|inflation|taux d['’]int[ée]r[êe]t|" +
            "bourse|investissement|entreprises?|march[ée]s? financiers?|" +
            "politique|[ée]lections?|parlement|assembl[ée]e nationale|gouvernement|" +
            "pr[ée]sident|premier ministre|budget de l['’][ée]tat|" +
            "economy|economic|stock market|federal reserve|interest rate|" +
            "government|parliament|election|finance|central bank)"
    )
    private val usefulLocal = Regex(
        "(?iu)(circulation (?:perturb[ée]e|coup[ée]e)|route (?:ferm[ée]e|barr[ée]e)|" +
            "travaux (?:de voirie|routiers)|coupure (?:d['’])?(?:eau|[ée]lectricit[ée])|" +
            "alerte m[ée]t[ée]o locale|transports? (?:interrompus?|perturb[ée]s?)|" +
            "perturbations? (?:de )?(?:transports?|trains?|bus)|" +
            "local (?:traffic|transport|service) (?:disruption|closure)|" +
            "road closure|power outage|water outage)"
    )
    private val recommendation = Regex(
        "(?iu)(articles? (?:recommand[ée]s?|sugg[ée]r[ée]s?|pour vous)|" +
            "(?:s[ée]lection|lecture|contenu) recommand[ée]e? pour vous|" +
            "vous pourriez (?:aussi )?aimer|vous aimerez aussi|" +
            "recommended (?:articles?|stories|for you)|" +
            "(?:suggested|personalized|personalised) (?:news|articles?|stories)|" +
            "d[ée]couvrez nos suggestions|you might (?:also )?like|" +
            "pourrait vous int[ée]resser|(?:votre|ton) fil personnalis[ée])"
    )
    private val clickbait = Regex(
        "(?iu)(vous ne (?:devinerez|croirez) jamais|" +
            "ce que personne ne vous dit|incroyable mais vrai|" +
            "la (?:v[ée]rit[ée]|raison) va vous (?:choquer|surprendre)|" +
            "(?:d[ée]couvrez|cliquez) avant qu['’]il ne soit trop tard|" +
            "you won['’]t believe|what happens next|the shocking truth|" +
            "(?:this|it) will (?:shock|blow your mind)|" +
            "click (?:here|now) to find out)"
    )
    private val editorialArticle = Regex(
        "(?iu)(nouvel? article|article publi[ée]|article du jour|" +
            "(?:dernier|nouveau) (?:reportage|dossier|article)|" +
            "new (?:article|story|report)|latest (?:article|report)|" +
            "(?:a publi[ée] (?:un |une )?(?:article|reportage|enqu[êe]te))|" +
            "(?:journal|m[ée]dia|r[ée]daction) (?:vient de publier|publie))"
    )

    fun isNewsApp(pkg: String): Boolean = pkg in newsApps || newsPackage.containsMatchIn(pkg)

    fun classify(pkg: String, title: String, body: String,
                 topic: String = "other", repeatedHeadline: Boolean = false): NewsNotificationKind {
        val text = "$title $body".lowercase(Locale.ROOT)
        // News-only contexts: ordinary personal messages, banking alerts, or
        // marketplace offers mentioning AI or elections must not be swept away.
        val newsContext = isNewsApp(pkg) || topic == "news"
        if (!newsContext) return NewsNotificationKind.UNKNOWN
        // A publisher may also deliver account messages or individual replies;
        // those must not be treated as an editorial digest.
        if (Regex("(?iu)(vous a envoy[ée] (?:un |une )?(?:message|chat)|" +
                "sent you (?:a )?(?:message|chat)|nouveau message de|new direct message from|" +
                "vous a [ée]crit|replied to your message)").containsMatchIn(text))
            return NewsNotificationKind.UNKNOWN
        // A major headline must contain BOTH explicit breaking framing and a
        // concrete major event. Sensationalist urgency alone is not enough.
        if (urgentLead.containsMatchIn(text) && majorEvent.containsMatchIn(text))
            return NewsNotificationKind.MAJOR_BREAKING
        if (repeatedHeadline) return NewsNotificationKind.REPEATED_HEADLINE
        if (recommendation.containsMatchIn(text)) return NewsNotificationKind.UNSOLICITED_RECOMMENDATION
        if (clickbait.containsMatchIn(text)) return NewsNotificationKind.CLICKBAIT
        if (usefulLocal.containsMatchIn(text)) return NewsNotificationKind.LOCAL_USEFUL
        if (aiTechCyber.containsMatchIn(text)) return NewsNotificationKind.AI_TECH_CYBER
        if (economyPolitics.containsMatchIn(text)) return NewsNotificationKind.ECONOMY_POLITICS
        if (editorialArticle.containsMatchIn(text) ||
            isNewsApp(pkg) && (text.length >= 35 || editorialContext.containsMatchIn(text)))
            return NewsNotificationKind.FOLLOWED_MEDIA
        return NewsNotificationKind.UNKNOWN
    }

    fun priority(kind: NewsNotificationKind): NotificationPriority? = when (kind) {
        NewsNotificationKind.MAJOR_BREAKING -> NotificationPriority.NOW
        NewsNotificationKind.AI_TECH_CYBER, NewsNotificationKind.ECONOMY_POLITICS,
        NewsNotificationKind.LOCAL_USEFUL, NewsNotificationKind.FOLLOWED_MEDIA -> NotificationPriority.TODAY
        NewsNotificationKind.UNSOLICITED_RECOMMENDATION, NewsNotificationKind.CLICKBAIT,
        NewsNotificationKind.REPEATED_HEADLINE -> NotificationPriority.LOW
        NewsNotificationKind.UNKNOWN -> null
    }
}
