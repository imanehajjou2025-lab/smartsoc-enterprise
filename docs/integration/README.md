# Contrats d'intégration SmartSOC

Ce répertoire contient les **contrats OpenAPI** entre la plateforme SmartSOC
et les systèmes externes développés hors de ce dépôt (ADR-005, ADR-008).
Chaque fichier est la référence commune : le fournisseur implémente le
contrat, la plateforme le consomme — aucune des deux équipes ne dépend du
code de l'autre.

| Fichier | Système externe | Fournisseur | Statut |
| --- | --- | --- | --- |
| [`ai-classifier-api.yaml`](ai-classifier-api.yaml) | Classifieur TP/FP (ML) | équipe IA | v1.0.0 |
| [`ai-assistant-api.yaml`](ai-assistant-api.yaml) | Agent conversationnel SOC (LLM) | équipe IA | v1.0.0 |

## Règles du jeu

1. **Le contrat est la seule interface.** Toute évolution passe par une PR
   sur le fichier YAML avec bump de `info.version` (semver), discutée entre
   les deux équipes avant d'écrire du code.
2. **URLs et clés d'API = configuration pure.** Côté plateforme :
   variables d'environnement (`SMARTSOC_AI_*`) ; rien dans le code.
3. **Dégradation gracieuse.** La plateforme fonctionne intégralement sans
   les services IA (mode `simulation` par défaut) ; l'indisponibilité d'un
   service ne bloque jamais un flux métier.
4. **Lecture seule.** Un service IA ne lit ni n'écrit jamais directement en
   base : tout passe par l'API backend (ADR-003, ADR-004).

## Pour l'équipe IA : implémenter un contrat

- Visualiser un contrat : <https://editor.swagger.io> (coller le YAML) ou
  `npx @redocly/cli preview-docs docs/integration/ai-classifier-api.yaml`.
- Générer un squelette FastAPI :
  `npx @openapitools/openapi-generator-cli generate -i docs/integration/ai-classifier-api.yaml -g python-fastapi -o /tmp/skeleton`.
- Valider une implémentation : `npx @redocly/cli lint <fichier>.yaml` puis
  tests de conformité contre le serveur (ex. `schemathesis run --base-url
  http://localhost:8000 docs/integration/ai-classifier-api.yaml`).
- L'authentification est une clé partagée dans l'en-tête `X-API-Key`,
  fournie par variable d'environnement des deux côtés.

## Pour l'équipe plateforme : consommer un contrat

Chaque contrat a un port dans `smartsoc-application` et deux adaptateurs
dans `smartsoc-infrastructure` (ADR-008) :

- `smartsoc.ai.mode=simulation` (défaut) → stub embarqué, démo autonome ;
- `smartsoc.ai.mode=live` → client OpenFeign résilient (timeout, circuit
  breaker) pointant l'URL configurée.
