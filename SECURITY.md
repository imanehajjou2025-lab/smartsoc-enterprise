# Politique de sécurité

## Versions supportées

| Version | Supportée |
| ------- | --------- |
| develop | ✅        |
| main    | ✅        |

## Signaler une vulnérabilité

**Ne créez jamais d'issue publique pour une vulnérabilité.**

1. Utilisez l'onglet **Security → Report a vulnerability** du dépôt GitHub
   (signalement privé), ou
2. Contactez directement l'équipe par e-mail : `jadchebihi20@gmail.com`.

Merci d'inclure :

- Une description de la vulnérabilité et son impact potentiel ;
- Les étapes de reproduction ;
- La version ou le commit concerné.

Nous accusons réception sous **72 heures** et nous nous engageons à publier un
correctif dans les meilleurs délais selon la sévérité.

## Bonnes pratiques du projet

- Aucun secret n'est commité : les fichiers `.env` sont ignorés par Git et un
  scan **Gitleaks** s'exécute en CI sur chaque push.
- Les dépendances sont surveillées par **Dependabot** et **OWASP Dependency Check**.
- Les images Docker sont scannées par **Trivy**.
- L'analyse statique est assurée par **SonarQube** et **CodeQL**.
