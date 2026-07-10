# ADR-001 — Monorepo pour l'ensemble de la plateforme

- **Statut** : Accepté
- **Date** : 2026-07-10
- **Décideurs** : Équipe SmartSOC

## Contexte

La plateforme comprend trois services développés en parallèle (backend Spring
Boot, frontend React, microservice IA FastAPI), plus la base de données, le
Docker Compose, la CI/CD et la documentation. L'équipe compte trois étudiants.
Faut-il un dépôt par service (multi-repo) ou un dépôt unique (monorepo) ?

## Décision

Un **monorepo unique** (`smartsoc-enterprise`) contenant tous les services,
chacun dans son répertoire de premier niveau, avec des workflows CI filtrés
par chemin (`paths:`) pour ne construire que ce qui change.

## Justification

- **Cohérence des versions** : une fonctionnalité traverse souvent les trois
  services (ex. score TP/FP : modèle → API → UI). Un monorepo permet une PR
  unique, atomique et revuable.
- **Simplicité opérationnelle** : un seul clone, un seul Docker Compose, une
  seule configuration CI, un seul CHANGELOG — décisif pour une équipe de 3.
- **Documentation centralisée** : architecture et ADR vivent à côté du code
  qu'ils décrivent.
- Les inconvénients classiques du monorepo (temps de CI, conflits d'équipes
  nombreuses, taille du dépôt) n'apparaissent qu'à une échelle très supérieure.

## Conséquences

- Les workflows GitHub Actions utilisent des filtres `paths:` pour éviter de
  reconstruire tout le monorepo à chaque push.
- Les scopes Conventional Commits (`backend`, `frontend`, `ai`…) identifient
  la zone touchée.
- Une éventuelle extraction future d'un service (ex. ai-service) reste
  possible : les services ne partagent aucun code source, uniquement des
  contrats d'API.
