# Réseau et authentification

Lire spec backends/auth et skill jc-backend. Les tests MockWebServer prouvent mécanismes/parsing,
pas l'éligibilité externe d'un abonnement. Vérifier les affirmations de support auprès des sources
officielles actuelles avant de les écrire comme garanties.

Préserver séparation des credentials, hôte autorisé, redaction et annulation des Call. Ni corps
HTTP brut ni token dans exception/UI/log. Client injecté pour tests, jamais endpoint de contournement
public pour envoyer des tokens ChatGPT à un autre hôte.

Le protocole compagnon est v2 ; synchroniser DTO, Python et tests pour une évolution wire.
Les capacités de reprise, outils, approvals/skills/jobs sont celles réellement implémentées.
