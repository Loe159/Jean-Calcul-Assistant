# Rapport de validation de cloture - phase 0

Date : 2026-07-17

Appareil : Samsung Galaxy S26 5G Dual SIM (`SM-S942B`), Android 16, One UI
8.5. Variante testee : `coreDebug` (`fr.loevan.jeancalcul.debug`, version
`0.1.0-debug`).

## Verdict

GO pour cloturer la phase 0 et passer a la phase 1. Les risques Android et
Samsung prevus par les issues #10 a #16 ont ete verifies sur le telephone
cible. Les mesures et limitations sont versionnees dans le depot.

## Validations effectuees

| Sujet | Evidence |
| --- | --- |
| Role assistant (#10) | Le role est detenu avant et apres `adb reboot`; `dumpsys voiceinteraction` lie de nouveau le service et la session. |
| Session transparente (#11) | Test manuel depuis l'accueil et Chrome : contenu sous-jacent visible, fermeture par geste Retour predictif sans artefact observe. Les tests unitaires, lint et build sont verts. |
| Power Samsung (#12) | Appui long physique valide depuis l'accueil, Chrome, ecran verrouille, apres veille et apres reboot. |
| STT/TTS (#13) | Transcription progressive, resultat final, reponse TTS, fermeture et reinvocation verifies. `RECORD_AUDIO` est libere par la fermeture de session; aucune erreur AndroidRuntime relevee. |
| Outil volume (#14) | Commande vocale appliquee puis relue sur le flux musique; les mesures reelles sont dans le rapport de performance. |
| Parcours complet (#16) | "Mets le volume a 30 %" reussi apres invocation physique; reponse vocale confirmee par l'utilisateur. |
| Stabilite et performances (#15) | Six commandes vocales completes mesurees, 30 ouvertures/fermetures `KEYCODE_ASSIST` sans crash, et 2 tests instrumentes reussis. |

## Mesures reelles

Les P50/P95 et les echantillons sont consignes dans
`phase-0-performance-stability.md`. Les six commandes vocales completes ont
couvert le parcours normal, reprise apres 15 minutes de veille, economie
d'energie, mode Optimisee, mode Sans restriction et apres reboot. Aucun log
`AndroidRuntime` ou `FATAL EXCEPTION` n'a ete observe dans les captures
dediees.

## Reglages et restauration

- Economie d'energie activee temporairement pour DM-05, puis restauree a
  desactivee.
- Jean-Calcul teste en mode Optimisee puis Sans restriction pour DM-06, puis
  remis en mode Optimisee.
- La veille a dure 15 minutes, ecran eteint et appareil en Dozing.

## Limitation documentee

Sur l'ecran verrouille, l'appui long Power reveille l'ecran; la commande locale
de volume et sa reponse TTS ont ete realisees, puis l'ecran s'est eteint selon
son delai normal. Aucune donnee personnelle n'a ete affichee. La phase 1 doit
imposer la politique explicite des actions autorisees ecran verrouille avant
d'ajouter des outils ou donnees personnelles.
