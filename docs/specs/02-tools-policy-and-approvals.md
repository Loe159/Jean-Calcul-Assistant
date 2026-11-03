# Outils Android, politique et approbations

## Sources

[ToolContracts](../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/ToolContracts.kt),
[PolicyEngine](../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/PolicyEngine.kt),
[ToolRegistry](../../android/tool-bridge/src/main/kotlin/fr/loevan/jeancalcul/toolbridge/ToolRegistry.kt),
[LocalAgentToolRuntime](../../android/tool-bridge/src/main/kotlin/fr/loevan/jeancalcul/toolbridge/LocalAgentToolRuntime.kt).
Réutiliser [l'ADR reçus](../architecture/0012-policy-engine-and-approval-receipts.md), pas un booléen d'UI.

## Contrat de confiance

**POL-01 — Pipeline unique.** Toute proposition est non fiable : validation du nom/version et
schéma strict, disponibilité réelle, profil/permissions/origine, PolicyEngine, approbation éventuelle,
reçu valide, exécuteur déterministe et audit. Le backend ne fournit ni permission ni reçu d'autorité.
La validation finale du registre reste obligatoire même si le modèle annonçait un JSON valide.

**POL-02 — Profil effectif.** Construire le contexte depuis ConfiguredAgentProfile et les paramètres
réels. Supprimer les préférences ALLOW synthétiques et ne pas forcer confirmAgentActions=false.
STRICT conserve la confirmation des modifications R2 ; BALANCED peut utiliser une règle explicite
compatible avec le plancher de risque ; EXPLICIT_AUTOMATION ne donne aucun droit supplémentaire
sans règle créée par l'utilisateur. Une demande générale d'aide ne vaut pas consentement à la lampe.

**POL-03 — Permissions.** Évaluer l'intersection registre disponible ∩ permissions Android ∩
permissions du profil ∩ contexte de verrouillage. DEVICE_BASIC_CONTROL est exigé pour les modifications
appareil R2 et ouverture/launch ; TASKS_WRITE pour tasks.create_local. Batterie/heure/volume en lecture
R0 peuvent rester disponibles sans permission de profil supplémentaire ; cela ne donne aucun accès
personnel. La liste annoncée et la vérification à l'exécution doivent appliquer le même mapping testé.
Une permission OS révoquée entre annonce et exécution est revérifiée.

## Catalogue existant à conserver

Les arguments, sorties, capacités et niveaux exacts sont définis par les schémas Kotlin :
[volume](../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/ToolContracts.kt) et
[MVP](../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/MvpToolSchemas.kt).
Ne pas recopier une nouvelle version de ces schémas dans l'UI.

| Outil | But | Risque/interaction cible |
| --- | --- | --- |
| audio.get_volume | Lire un flux déclaré | Lecture R0 selon disponibilité |
| audio.set_volume | Pourcentage absolu d'un flux | Modification R2, confirmation selon profil/règle |
| device.get_battery | État batterie réel | Lecture R0, aucun résultat inventé |
| device.get_local_time | Heure/fuseau du téléphone | Lecture R0 |
| device.toggle_flashlight | Activer/désactiver la lampe | R2, confirmation STRICT, permission/capacité |
| device.open_settings | Ouvrir un panneau déclaré | Pas de modification protégée silencieuse |
| apps.launch | Lancer un package visible installé | Erreur explicite si indisponible |
| media.play_pause | Commande média déclarée | R2 ; absence session média récupérable |
| tasks.create_local | Créer une tâche locale | R2 ; persistance et audit, pas de calendrier implicite |

## Décision et confirmation

**POL-04 — Pas d'exécution prématurée.** ALLOW produit un reçu via PolicyEngine ; CONFIRM et
BIOMETRIC produisent un état d'attente. Une erreur USER_CONFIRMATION_REQUIRED ne remplace pas
une interface de confirmation. Modifier le contrat synchrone du runtime vers une coordination
suspendue/événementielle est permis ; conserver le registre synchrone si ses adaptateurs le nécessitent.

**POL-05 — Présentation exacte.** Afficher action, arguments utiles, effet, origine/profil, raison
et expiration. « Activer la lampe » et « Désactiver la lampe » sont distincts. Aucun contenu provenant
du modèle ne peut masquer la destination ou remplacer les libellés d'autorité de l'app.
Deux actions explicites : Confirmer et Refuser ; aucune préacceptation à l'ouverture de la feuille.

**POL-06 — Liaison et durée.** L'approbation porte sur actionId, outil, version, arguments et
expiration exacts. Réutiliser les vérifications de reçu existantes. Une modification d'argument,
verrouillage, révocation, suppression profil ou expiration invalide l'approbation. Le TTL existant
du runtime est 120 s : garder ce plafond pour ce jalon et le tester avec une horloge injectée.

**POL-07 — Annulation et refus.** Refuser, fermer la surface, arrêter le tour ou expirer termine
l'attente sans exécuter. Renvoyer un ToolResult d'erreur structuré au bon callId/run si la session est
encore active ; écrire l'audit même si elle est déjà fermée. Ne jamais convertir un refus en succès.

**POL-08 — Biométrie et permissions OS.** Une décision BIOMETRIC requiert le host Android et une
preuve effective ; le domaine ne connaît pas BiometricPrompt. Si impossible en arrière-plan/verrouillé,
demander l'ouverture de l'app/déverrouillage ; ne pas dégrader en confirmation simple.
Une permission OS requise mène à son panneau/dialogue officiel, puis à une nouvelle évaluation.

**POL-09 — Idempotence.** Même actionId/clé et mêmes paramètres : résultat précédent avec replayed,
pas d'effet répété. Même clé avec paramètres différents : refus. Le scope de persistance du registre
doit être déclaré ; ne pas annoncer une résistance au redémarrage si la déduplication est en mémoire.
Une action à effet dont le résultat est perdu reste « résultat inconnu », pas un retry automatique.

**POL-10 — Appel et sortie.** ToolResult comporte exactement output ou error. Valider la sortie
selon outputSchema et préserver callId/actionId/version. Un backend sans AgentToolResultSink ne
reçoit pas des outils qu'il ne peut pas poursuivre ; afficher la limite de capacité.

**POL-11 — Audit.** Journaliser tentative, décision, attente, approbation/refus/expiration,
exécution ou rejeu, durée et erreur expurgée. Inclure origine texte/voix et session/profil si disponible.
Échecs UNKNOWN_TOOL/indisponibilité doivent être traçables, pas seulement les succès de l'exécuteur.

**POL-12 — Contexte dangereux.** R4 exige le plancher biométrique prévu ; R5 reste refusé par défaut.
Les données externes ne peuvent ni créer une règle, ni donner une permission, ni marquer une action
confirmée. Un outil R0 autorisé verrouillé n'autorise pas les modifications R2 sur écran verrouillé.

## Tests minimaux de politique

STRICT + lampe → confirmation sans appel adaptateur ; confirmer → un appel et un reçu ; refuser →
zéro appel ; expirer → zéro appel ; modifier les arguments après confirmation → refus ; révoquer
CAMERA/DEVICE_BASIC_CONTROL → refus ; appel batterie → valeur fake contrôlée puis valeur réelle en
test appareil ; doublon → un effet ; annulation pendant attente → cleanup/audit/résultat sans blocage.
