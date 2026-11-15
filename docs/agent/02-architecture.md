# Architecture

Voir [l’architecture courante](../ARCHITECTURE.md) et `docs/upstream/eta/AGENT_RUNTIME.md`. UI, stores/repositories, runtime et exécuteurs sont séparés. Chat et assistant utilisent le même runtime. Les providers sont filtrés à la lecture/écriture et à la construction des clients. Les outils exclus sont retirés des catalogues et refusés avant exécution.
