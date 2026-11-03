# Instructions pour la documentation

Les instructions racine s'appliquent. Ne pas marquer un comportement « validé » par simple lecture
du code. Chaque nouvel état réel mentionne date, commit et niveau de preuve. Les rapports anciens
restent historiques ; ajouter un nouveau rapport plutôt que réécrire leurs résultats.

Les specs normatives sont dans specs ; l'ordre et les chemins des missions sont dans
agents/tasks/manifest.json. Les statuts de réalisation restent dans les issues GitHub.
Une spec modifiée exige mise à jour de la fiche et des critères/tests correspondants.

Ne pas inventer source officielle, endpoint, scope, mesure ou capture. Les pages de phase future
doivent indiquer DIFFÉRÉ et décisions restantes. Ne pas créer de modules pour matérialiser la doc.

Liens locaux relatifs au fichier, noms de sources exacts et pas de dépendance à un chemin personnel.
Exécuter `python3 scripts/check_agent_docs.py` et les tests docs avant livraison documentaire.
