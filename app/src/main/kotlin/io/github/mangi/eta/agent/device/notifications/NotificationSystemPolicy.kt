package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import java.util.Locale

/** Deterministic, local rules for the seven approved system-notification cases. */
internal enum class SystemNotificationKind {
    CRITICAL, BACKUP_FAILED, UPDATE_AVAILABLE, BACKUP_OR_SYNC_SUCCEEDED,
    APP_UPDATE_COMPLETE, TIPS_AND_SUGGESTIONS, ROUTINE_TECHNICAL, UNKNOWN,
}

internal object NotificationSystemPolicy {
    private val knownSystemPackages = setOf(
        "android", "com.google.android.gms", "com.android.settings",
        "com.google.android.apps.docs", "com.microsoft.skydrive",
        "com.dropbox.android", "com.samsung.android.scloud",
    )
    private val systemPackagePattern = Regex(
        "(?i)(^com\\.android\\.|^com\\.samsung\\.android\\.|^com\\.sec\\.android\\.|" +
            "(?:^|\\.)(?:backup|backups|updater|sync|system|storage|battery)(?:\\.|$))"
    )
    private val backupFailed = Regex(
        "(?iu)(sauvegarde.{0,35}(?:[ée]chou[ée]e?|[ée]chec|impossible|interrompue)|" +
            "(?:[ée]chec|[ée]chou[ée]e|impossible).{0,35}de (?:la )?sauvegarde|" +
            "backup.{0,35}(?:failed|failure|unsuccessful|error|could not|interrupted)|" +
            "(?:failed|failure|unable to).{0,35}backup)"
    )
    private val critical = Regex(
        "(?iu)(batterie (?:critique|tr[èe]s faible|dangereusement faible)|" +
            "(?:critical|critically low) battery|battery (?:critically low|critical)|" +
            "(?:stockage|m[ée]moire|disque) (?:satur[ée]|plein|critique)|" +
            "(?:plus d['’]espace|espace de stockage insuffisant|stockage insuffisant)|" +
            "(?:storage|disk) (?:full|critically low|exhausted)|no storage space|" +
            "(?:surchauffe|overheat(?:ing)?|temperature critique)|" +
            "(?:panne (?:critique|majeure|importante|du syst[èe]me)|critical system failure|" +
            "system (?:failure|unavailable)|device malfunction))"
    )
    private val updateAvailable = Regex(
        "(?iu)(mise [àa] jour (?:du )?(?:syst[èe]me|logicielle|android|one ui|coloros).{0,50}(?:disponible|[àa] installer|pr[êe]te)|" +
            "(?:nouvelle|une) mise [àa] jour (?:logicielle|syst[èe]me|android|de s[ée]curit[ée]) (?:est )?disponible|" +
            "mise [àa] jour de s[ée]curit[ée] (?:est )?disponible|" +
            "(?:system|software|android|security) update (?:is )?(?:available|ready)|" +
            "update (?:available|ready) for (?:android|your device|the system))"
    )
    private val backupSucceeded = Regex(
        "(?iu)(sauvegarde.{0,45}(?:termin[ée]e?|effectu[ée]e?|r[ée]ussie?|achev[ée]e?)|" +
            "(?:synchronisation|synchroniser).{0,45}(?:termin[ée]e?|effectu[ée]e?|r[ée]ussie?|achev[ée]e?)|" +
            "(?:backup|sync(?:hronization)?).{0,45}(?:completed|succeeded|successful|finished)|" +
            "(?:successfully backed up|sync complete|backup complete|all files synced))"
    )
    private val appUpdated = Regex(
        "(?iu)(applications? (?:mises? [àa] jour|actualis[ée]es?)|" +
            "(?:mise [àa] jour de l['’]application|mise [àa] jour des applications).{0,40}(?:termin[ée]e?|effectu[ée]e?|r[ée]ussie?)|" +
            "(?:app|application)s? (?:updated|successfully updated|update complete)|" +
            "(?:app|application) updates? (?:complete|completed|finished)|" +
            "play store.{0,40}mises? [àa] jour termin[ée]es?)"
    )
    private val tips = Regex(
        "(?iu)(conseils? (?:android|pour votre t[ée]l[ée]phone|du syst[èe]me)|" +
            "astuces? (?:android|du syst[èe]me|pour votre t[ée]l[ée]phone)|" +
            "(?:fonctionnalit[ée]s? |options? )(?:sugg[ée]r[ée]es?|recommand[ée]es?)|" +
            "(?:tips? and tricks|android tips?|suggested (?:features?|settings)|" +
            "discover new features|try this feature|helpful tips))"
    )
    private val routine = Regex(
        "(?iu)(v[ée]rification (?:p[ée]riodique|automatique|de routine).{0,30}(?:termin[ée]e?|effectu[ée]e?)|" +
            "(?:t[âa]che planifi[ée]e|maintenance automatique).{0,35}(?:termin[ée]e?|effectu[ée]e?)|" +
            "(?:routine|periodic|scheduled) (?:system )?(?:check|maintenance).{0,35}(?:completed|finished)|" +
            "nettoyage (?:automatique|en arri[èe]re.plan) (?:termin[ée]|effectu[ée]))"
    )

    fun classify(pkg: String, title: String, body: String,
                 androidCategory: String? = null, topic: String = "other"): SystemNotificationKind {
        // Don't allow arbitrary messages and promotions to become dismissible system notices.
        val isSystemSource = pkg in knownSystemPackages || systemPackagePattern.containsMatchIn(pkg) ||
            androidCategory in setOf(Notification.CATEGORY_SYSTEM, Notification.CATEGORY_SERVICE) ||
            topic == "system"
        if (!isSystemSource) return SystemNotificationKind.UNKNOWN
        val text = "$title $body".lowercase(Locale.ROOT)
        return when {
            critical.containsMatchIn(text) -> SystemNotificationKind.CRITICAL
            backupFailed.containsMatchIn(text) -> SystemNotificationKind.BACKUP_FAILED
            updateAvailable.containsMatchIn(text) -> SystemNotificationKind.UPDATE_AVAILABLE
            backupSucceeded.containsMatchIn(text) -> SystemNotificationKind.BACKUP_OR_SYNC_SUCCEEDED
            appUpdated.containsMatchIn(text) -> SystemNotificationKind.APP_UPDATE_COMPLETE
            tips.containsMatchIn(text) -> SystemNotificationKind.TIPS_AND_SUGGESTIONS
            routine.containsMatchIn(text) -> SystemNotificationKind.ROUTINE_TECHNICAL
            else -> SystemNotificationKind.UNKNOWN
        }
    }

    fun priority(kind: SystemNotificationKind): NotificationPriority? = when (kind) {
        SystemNotificationKind.CRITICAL, SystemNotificationKind.BACKUP_FAILED -> NotificationPriority.NOW
        SystemNotificationKind.UPDATE_AVAILABLE,
        SystemNotificationKind.BACKUP_OR_SYNC_SUCCEEDED -> NotificationPriority.TODAY
        SystemNotificationKind.APP_UPDATE_COMPLETE, SystemNotificationKind.TIPS_AND_SUGGESTIONS,
        SystemNotificationKind.ROUTINE_TECHNICAL -> NotificationPriority.LOW
        SystemNotificationKind.UNKNOWN -> null
    }

    fun safeToClear(kind: SystemNotificationKind): Boolean = kind in setOf(
        SystemNotificationKind.UPDATE_AVAILABLE, SystemNotificationKind.BACKUP_OR_SYNC_SUCCEEDED,
        SystemNotificationKind.APP_UPDATE_COMPLETE, SystemNotificationKind.TIPS_AND_SUGGESTIONS,
        SystemNotificationKind.ROUTINE_TECHNICAL)
}
