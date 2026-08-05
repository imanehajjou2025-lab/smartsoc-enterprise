# Documentation d'architecture — SmartSOC Enterprise

Documentation de référence de la plateforme et de son intégration à
l'infrastructure SOC.

**Convention de lecture, appliquée dans tous les documents :**
✅ existe et fonctionne · 🔨 prévu, non implémenté · ⚠ effet réel sur des
machines de production.

> Ces documents décrivent l'état **réel** du système, mesuré dans le code, pas
> un état souhaité. Ce qui n'existe pas est marqué comme tel.

---

## Par où commencer

| Vous cherchez… | Document |
| --- | --- |
| Une vue d'ensemble du système | [`C4-MODEL.md`](C4-MODEL.md) — niveau 1 |
| Les décisions d'intégration SOC | [`soc-integration-plan.md`](soc-integration-plan.md) *(baseline figée)* |
| Le calendrier et la charge | [`PROJECT-PLANNING.md`](PROJECT-PLANNING.md) *(document vivant)* |
| Pourquoi telle décision | [`adr/`](adr/) |

---

## Vues statiques — la structure

| Document | Contenu |
| --- | --- |
| [`C4-MODEL.md`](C4-MODEL.md) | Les 4 niveaux C4 : contexte, conteneurs, composants, **déploiement Azure** |
| [`CONTEXT-MAP.md`](CONTEXT-MAP.md) | Carte DDD des 12 bounded contexts, relations **mesurées** dans le code |
| [`CONNECTORS-REFERENCE.md`](CONNECTORS-REFERENCE.md) | Anatomie d'un connecteur, fiche par outil, 8 dimensions de supervision |

## Vues dynamiques — le comportement

| Document | Contenu |
| --- | --- |
| [`SEQUENCE-DIAGRAMS.md`](SEQUENCE-DIAGRAMS.md) | 6 scénarios : ingestion d'alertes, synchronisation d'agents, MISP, VirusTotal, chasse OpenSearch, déclenchement Shuffle |
| [`DOMAIN-EVENTS.md`](DOMAIN-EVENTS.md) | Événements internes — 2 implémentés, 7 proposés |

## Chapitres spécialisés

| Document | Contenu |
| --- | --- |
| [`AI-ARCHITECTURE.md`](AI-ARCHITECTURE.md) | Classifieur TP/FP et assistant conversationnel : FastAPI, Ollama, récupération de contexte, embeddings, intégration |
| [`SOAR-ARCHITECTURE.md`](SOAR-ARCHITECTURE.md) | Playbooks, déclenchement, suivi d'exécution, callback, erreurs, reprise |
| [`soc/SOC-ARCHITECTURE.md`](soc/SOC-ARCHITECTURE.md) | Infrastructure SOC (hors périmètre de ce dépôt) |

## Pilotage

| Document | Contenu |
| --- | --- |
| [`PROJECT-PLANNING.md`](PROJECT-PLANNING.md) | Charge calibrée sur les PR réelles, dépendances, découpage en PR, critères de fin, suivi |

---

## Décisions d'architecture (ADR)

| ADR | Sujet |
| --- | --- |
| [001](adr/ADR-001-monorepo.md) | Monorepo |
| [002](adr/ADR-002-clean-architecture-ddd.md) | Clean Architecture + DDD, vérifiée par ArchUnit |
| [003](adr/ADR-003-ai-microservice.md) | Microservice IA derrière un contrat REST |
| [004](adr/ADR-004-postgresql-source-of-truth.md) | PostgreSQL source de vérité unique |
| [005](adr/ADR-005-standalone-platform-integration-contracts.md) | Plateforme autonome, intégrations par contrats |
| [006](adr/ADR-006-soc-on-azure-multi-account.md) | SOC sur Azure multi-comptes |
| [007](adr/ADR-007-backend-serves-spa.md) | Spring Boot sert la SPA (sans Nginx) |
| [008](adr/ADR-008-ai-integration-architecture.md) | Architecture d'intégration IA |
| [009](adr/ADR-009-cti-indicator-model-and-feed-ingestion.md) | Modèle d'indicateur CTI |
| [010](adr/ADR-010-mitre-attack-catalog.md) | Catalogue MITRE ATT&CK |
| [011](adr/ADR-011-threat-hunting.md) | Threat Hunting |
| [012](adr/ADR-012-soar-playbooks.md) | Playbooks SOAR |
| [013](adr/ADR-013-reporting.md) | Reporting |
| [**014**](adr/ADR-014-soc-integration-architecture.md) | **Architecture d'intégration SOC** — baseline v1.1 |
| 015 | *Topologie réseau retenue — attendu en fin de phase 0* |

---

## Règles de gouvernance documentaire

1. **La baseline d'architecture est figée.** Toute décision découverte pendant
   l'implémentation fait l'objet d'un **ADR distinct**, jamais d'une réécriture.
2. **Séparation stricte** entre architecture (figée) et planification (vivante).
3. **Aucune donnée inventée.** Une capacité absente est annoncée comme telle,
   jamais simulée ni passée sous silence.
4. **Les mesures priment sur les estimations.** Les valeurs de délai, de charge
   et de performance proviennent de mesures réelles, et sont révisées dès qu'un
   fait les contredit.
