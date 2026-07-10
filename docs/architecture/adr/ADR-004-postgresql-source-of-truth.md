# ADR-004 — PostgreSQL, source de vérité unique de la plateforme

- **Statut** : Accepté
- **Date** : 2026-07-10
- **Décideurs** : Équipe SmartSOC

## Contexte

Les données de la plateforme (alertes normalisées, incidents, cas, actifs,
IOC, playbooks, utilisateurs, audit) doivent être persistées de façon fiable.
En parallèle, l'écosystème SOC produit d'énormes volumes d'événements bruts
stockés dans OpenSearch/Elasticsearch (géré hors plateforme). Faut-il une
seconde base (Mongo, Elastic dédié) côté plateforme ?

## Décision

**PostgreSQL 18 est l'unique base de données de la plateforme** et sa source
de vérité. Migrations gérées par **Flyway** (versionnées, jamais modifiées
après merge). Le **backend est le seul écrivain** ; l'AI Service dispose d'un
rôle en lecture seule.

Les événements bruts massifs restent dans OpenSearch (côté outils SOC) : la
plateforme n'en stocke que les alertes **normalisées** et leurs
enrichissements, et interroge OpenSearch à la demande via les connecteurs.

## Justification

- **Le bon modèle de données** : les entités de la plateforme sont fortement
  relationnelles (alerte → incident → cas → tâches ; alerte ↔ IOC ↔ technique
  MITRE) et exigent l'intégrité transactionnelle (ACID) — le terrain naturel
  du relationnel.
- **Une seule base à administrer** : sauvegardes, migrations et supervision
  simples pour une équipe de 3 ; déjà installée dans l'environnement de dev.
- **JSONB** couvre les besoins semi-structurés (payload brut d'une alerte,
  définition d'un playbook) sans seconde technologie.
- **Extensible** : `pgvector` pourra servir de store d'embeddings pour le RAG
  si nécessaire, en restant dans PostgreSQL.

## Conséquences

- La volumétrie « big data » (logs bruts) est explicitement hors périmètre de
  PostgreSQL — c'est le rôle d'OpenSearch, côté outils SOC.
- Conventions obligatoires dès la première migration : clés UUID, colonnes
  d'audit (`created_at`, `created_by`, `updated_at`, `updated_by`), soft
  delete quand pertinent, index sur toutes les clés étrangères.
- Un schéma dédié et des rôles PostgreSQL distincts (backend lecture/écriture,
  ai-service lecture seule).
