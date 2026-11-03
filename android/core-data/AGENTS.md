# Données locales

Lire spec données et skill jc-data. Base v2 à la base documentaire : vérifier VERSION au moment
de travailler. Préserver anciens schémas, ajouter migration explicite et tests depuis versions
supportées. Ne jamais activer fallback destructif.

Allocation de séquences et écritures logiques doivent être atomiques ; cascade et callbacks tardifs
doivent rester cohérents. Un fake repository ne prouve pas la transaction Room. Tester sur base réelle.
DataStore/Singleton Hilt ne sont pas supposés partagés entre processus : inspecter et prouver le mécanisme.

Les secrets ne vont pas dans Room/DataStore. Export snapshot et redaction avant partage. Une suppression
locale n'est pas une promesse de suppression fournisseur ; documenter la portée effectivement exécutée.
