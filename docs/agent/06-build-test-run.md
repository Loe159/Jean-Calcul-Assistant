# Build et tests

À la racine : `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` avec JDK25 et SDK37. `-Pjeancalcul.emulator=true` produit une variante x86_64 réservée aux tests UI ; l’APK distribué reste ARM64. Tests produit : `:app:testDebugUnitTest --tests io.github.mangi.eta.product.*`. Parcours visuel : `python3 e2e/run.py --run-id eta-selection --attempt 1`, option `--software-emulator` sans KVM. Les résultats réels doivent être consignés avant livraison.
