# Jean-Calcul preview updates

The PR 62 test channel is `preview-pr-62`. Each push to its branch builds an ARM64 APK with a monotonic version code, runs JVM tests and lint, then publishes a versioned APK and `update.json` to the GitHub prerelease. The manifest is replaced only after its APK is uploaded; rerunning an older build cannot downgrade the channel.

The app checks on foreground launch (at most once per minute). Settings → Check for updates checks immediately. New builds download automatically in the background, then offer installation. Downloading, SHA-256/size validation and Android package/signing-certificate checks happen inside the app. Android then asks for installation confirmation; allow installation from Jean-Calcul once when requested. Normal updates keep conversations, providers and settings.

The old manually distributed APK used a runner-generated debug certificate that was not retained. The first switch may therefore require uninstalling that old APK before installing the new preview, which removes its local data. Android cannot update across mismatched certificates. Subsequent previews use the same checked-in **public development-only** key `.github/preview-signing.jks` (password `android`). Never use that key for production releases. The application ID stays `fr.loevan.jeancalcul.v2`.

No GitHub token is embedded in the app. Release builds have this preview update channel disabled and retain separate private release signing.

English resources are the only packaged app language. Upstream legal attribution, internal Kotlin/JNI names, migration strings, external data recognizers and vendor protocol IDs remain where compatibility requires them. Their presence in source does not brand the app or select Chinese UI resources.

Les branches de fondation et `self-improve/**` publient aussi sur ce canal pour conserver la compatibilité des APK installés. La publication attend désormais le succès du build, des tests JVM/lint et du job Android. Le numéro de version provient de la sortie du build, y compris quand seul le job de publication est relancé. Voir [SELF-IMPROVEMENT.md](SELF-IMPROVEMENT.md).
