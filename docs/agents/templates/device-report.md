# Rapport de validation appareil — à remplir lors d'un essai réel

Ce modèle ne constitue pas un rapport exécuté. Ne pas remplacer ses champs par des valeurs estimées.

## Environnement

Date/heure/fuseau ; opérateur ; commit et variante ; APK/version ; modèle appareil ; Android/One UI ;
backend et version ; réseau/USB ; rôle assistant ; permissions ; réglages batterie ; état initial à restaurer.
Compte autorisé utilisé sans identifiant privé dans ce rapport.

## Série

| Cas / index | Texte ou voix | Précondition synthétique | Résultat attendu | Observé | PASS/FAIL/NON EXÉCUTÉ | Preuve expurgée |
| --- | --- | --- | --- | --- | --- | --- |

Numéroter séparément les vingt tours et les vingt outils. Un refus attendu et effectivement sans
effet est un succès du scénario de refus, pas un outil exécuté avec succès. Noter durée/échec/crash.

## Cas physiques

Power réel ; appel entrant ; biométrie ; lockscreen ; veille/batterie ; perte réseau ; révocation ;
rotation/mort processus ; TalkBack ; captures des états/thèmes/polices ; restauration des réglages.

## Mesures

Jalons, taille d'échantillon, P50/P95/max si pertinents, traces expurgées et conditions.
Les mesures émulateur, KEYCODE_ASSIST et Power physique sont identifiées séparément.

## Conclusion au périmètre

Exigences validées, non exécutées, défauts et procédure de reproduction. Statut restant de #46/#17.
Aucune clé, token, code OAuth, adresse privée ou transcription personnelle dans les pièces jointes.
