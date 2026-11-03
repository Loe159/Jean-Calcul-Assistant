# Rôles de travail

Ces consignes sont portables et utilisables dans une session unique. Elles ne déclarent ni
ne démarrent des subagents propres à un outil.

| Rôle | Entrée | Livrable | Limite essentielle |
| --- | --- | --- | --- |
| Cadrage | Demande, issue, état réel, sources | Mission bornée, critères, décisions, preuves | Ne pas transformer une cible en fait livré |
| Implémentation | Une fiche JC et dépendances livrées | Code, tests utiles, doc, résultat | Pas de refonte globale ni fusion implicite |
| UI | Mission UI, comportements, tokens | Écran complet, captures et limites | Ne pas changer politique/capacités pour simplifier le rendu |
| Revue | Diff, mission, specs, checks | Défauts avec gravité/scénario/fichier/critère | Ne pas approuver un service réel à partir d'un mock |
| Validation appareil | Build identifié, appareil autorisé | Rapport daté avec cas/preuves expurgées | Ne pas simuler une validation physique absente |
| Documentation | Diff final, contrats, preuves | Specs, état réel et liens cohérents | Préserver et dater les historiques |

L'implémenteur maintient exigence → comportement → test. Le relecteur inspecte aussi le chemin de
production : annulation, concurrence, profil actif, autorisation, secrets et migration. Il conclut
« critères couverts », « corrections nécessaires » ou « preuve manquante », au périmètre réel.
Une revue en lecture seule ne modifie pas le code sans demande.

La validation appareil demande les interventions indispensables : biométrie, consentement et
appel entrant. Elle note « non exécuté » si elles manquent et restaure les réglages de test.
Le documentaliste ne résout pas une contradiction en déclarant arbitrairement un service compatible.
