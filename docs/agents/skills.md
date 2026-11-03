# Skills de développement du dépôt

Ces skills sont versionnés dans `.agents/skills` pour voyager avec le projet. Ils ne demandent
aucun plugin, clé ou installation globale. Un agent qui ne découvre pas automatiquement ce format
peut lire directement le SKILL.md lié dans sa fiche. Ne pas copier les skills hors du dépôt :
leurs références sont relatives à cette arborescence.

| Skill | Usage | Missions habituelles |
| --- | --- | --- |
| [jc-runtime](../../.agents/skills/jc-runtime/SKILL.md) | Sélection, cycle de tour, raccordement texte | JC-01/02/03 |
| [jc-tools-policy](../../.agents/skills/jc-tools-policy/SKILL.md) | Permissions, reçus, attente, idempotence | JC-04/05 |
| [jc-data](../../.agents/skills/jc-data/SKILL.md) | Room/DataStore, reprise, export, processus | JC-09 |
| [jc-voice](../../.agents/skills/jc-voice/SKILL.md) | Runtime vocal et ressources Android | JC-06 |
| [jc-compose-ui](../../.agents/skills/jc-compose-ui/SKILL.md) | Écrans réels, états, insets/accessibilité | JC-07/08 |
| [jc-backend](../../.agents/skills/jc-backend/SKILL.md) | Auth, adaptateurs et protocole v2 | JC-10 |
| [jc-verify](../../.agents/skills/jc-verify/SKILL.md) | Qualification et preuves appareil | JC-11/12 |

Exemple d'invocation portable : « Lis `.agents/skills/jc-runtime/SKILL.md` et réalise JC-01 ».
La syntaxe `$jc-runtime` peut servir dans un client qui prend en charge cette découverte ; le
chemin direct reste la référence portable. Ces skills ne donnent aucune autorisation de fusion,
publication, accès à un compte ou effet sur un appareil personnel.

Ils sont distincts du format de skills **produit** prévu en phase 4, qui aura son propre manifeste
de permissions et sandbox. Un fichier d'instructions pour coder ne devient jamais un droit Android.

Pour modifier un skill : garder description de déclenchement précise, instructions courtes,
références vers la spec canonique et chemins relatifs vérifiables. Ne pas recopier toutes les
specs dans chaque skill. Lancer `python3 scripts/check_agent_docs.py` après édition.
