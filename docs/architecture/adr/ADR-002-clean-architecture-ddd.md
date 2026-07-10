# ADR-002 — Clean Architecture + DDD pour le backend

- **Statut** : Accepté
- **Date** : 2026-07-10
- **Décideurs** : Équipe SmartSOC

## Contexte

Le backend Spring Boot est le cœur de la plateforme : logique métier SOC,
moteur SOAR, connecteurs, sécurité. Il évoluera pendant toute la durée du
projet et devra intégrer des sources externes variées (outils SOC, AI Service).
Un backend « couches techniques » classique (controller → service → repository
fourre-tout) devient vite un monolithe spaghetti sur ce type de domaine.

## Décision

Backend structuré en **Clean Architecture** (4 modules Maven : `domain`,
`application`, `infrastructure`, `api`) avec un découpage **DDD** en bounded
contexts (`alerts`, `incidents`, `assets`, `intelligence`, `soar`,
`connectors`, `identity`, `reporting`).

Règle de dépendance : `api → application → domain ← infrastructure`.
Le module `domain` est du Java pur, sans aucune dépendance Spring/JPA.

## Justification

- **Testabilité** : les règles métier (cycle de vie d'une alerte, escalade
  d'incident, exécution de playbook) se testent sans Spring ni base de données.
- **Substituabilité** : les outils SOC et le fournisseur LLM sont des « ports »
  (interfaces du domaine) avec des adaptateurs interchangeables — exigence
  explicite du cahier des charges (connecteurs à interface commune, provider
  LLM remplaçable).
- **Vocabulaire partagé** : les bounded contexts DDD correspondent exactement
  aux modules fonctionnels de l'UI et aux concepts SOC standards (alerte,
  incident, IOC, playbook), ce qui aligne code, interface et documentation.
- Les design patterns imposés (Repository, Strategy, Adapter, Factory,
  Builder, Facade) trouvent chacun leur place naturelle dans cette structure.

## Conséquences

- Multi-module Maven dès le départ (léger surcoût initial de configuration).
- Mapping systématique entité ↔ DTO (MapStruct) entre les couches.
- Discipline de revue : refuser toute dépendance framework dans `domain`.
- ArchUnit sera utilisé en test pour verrouiller automatiquement le sens des
  dépendances.
