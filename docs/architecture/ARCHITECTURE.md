# Architecture — SmartSOC Enterprise

> Document fondateur. Toute évolution structurante doit être tracée par un
> [ADR](adr/) et référencée ici. Modèle utilisé : [C4](https://c4model.com/)
> (niveaux 1 à 3).

## 1. Vue d'ensemble et périmètre

SmartSOC Enterprise est une **plateforme web unifiée** qui centralise la
supervision de sécurité : alertes, incidents, investigations, threat
intelligence, réponse automatisée (SOAR) et assistance par IA.

### Frontière de responsabilité (décision structurante)

| Périmètre | Responsable | Exemples |
| --- | --- | --- |
| **Plateforme SmartSOC** (ce dépôt) | Équipe développement | Backend, frontend, base de données, microservice IA, moteur SOAR, connecteurs |
| **Outils SOC** (hors dépôt) | Administration SOC | Wazuh, Suricata, Zeek, OpenSearch, TheHive, Cortex, MISP, FleetDM, Shuffle, règles Sigma/YARA |

La plateforme **consomme** les outils SOC via des connecteurs ; elle ne les
déploie pas et ne les configure pas. Cette frontière est non négociable : elle
garde le dépôt centré sur le produit et rend la plateforme testable sans
infrastructure SOC réelle (connecteurs simulables).

## 2. Niveau C4-1 — Contexte

```
                         ┌──────────────────────────┐
   SOC Analyst ─────────▶│                          │◀───────── SOC Manager
   (investigation,       │    SmartSOC Enterprise   │           (dashboards, KPIs)
    triage, chat IA)     │                          │
   Administrateur ──────▶│   Plateforme web unifiée │
   (RBAC, paramètres)    └─────┬──────────┬─────────┘
                               │          │
              ┌────────────────┘          └──────────────────┐
              ▼                                              ▼
   ┌─────────────────────┐                        ┌──────────────────────┐
   │  Outils SOC         │                        │  Services externes   │
   │  Wazuh · Suricata   │                        │  Ollama (LLM local)  │
   │  OpenSearch · MISP  │                        │  VirusTotal API      │
   │  TheHive · Shuffle  │                        │  SMTP · Teams · Jira │
   └─────────────────────┘                        └──────────────────────┘
```

## 3. Niveau C4-2 — Conteneurs

| Conteneur | Technologie | Rôle | Port (dev) |
| --- | --- | --- | --- |
| **Frontend** | React 18 + TypeScript + Vite + MUI | SPA : dashboards, alertes, incidents, playbooks, assistant IA | 5173 |
| **Backend** | Spring Boot 3 / Java 21 | Cœur métier : API REST, WebSocket, RBAC/JWT, moteur SOAR, connecteurs SOC, orchestration | 8080 |
| **AI Service** | Python 3.12 + FastAPI | Classification TP/FP (ML) + Agent IA (LLM, RAG, tool calling) | 8000 |
| **PostgreSQL** | PostgreSQL 18 + Flyway | Source de vérité de la plateforme (voir ADR-004) | 5432 |
| **Ollama** | Ollama (externe) | Exécution locale des LLM — remplaçable par OpenAI/Claude/Gemini/Mistral | 11434 |

### Flux principaux

```
Frontend ──HTTP/REST + WebSocket (STOMP)──▶ Backend
Backend  ──REST (OpenFeign)──▶ AI Service ──REST──▶ Ollama
Backend  ──REST / Syslog / Webhooks──▶ Outils SOC (connecteurs)
Backend  ──JPA/Flyway──▶ PostgreSQL
AI Service ──lecture seule (RAG/outils)──▶ PostgreSQL, OpenSearch, Wazuh
```

Règles de communication :

1. **Le frontend ne parle qu'au backend** — jamais directement à l'AI Service,
   aux outils SOC ou à la base. Un seul point d'entrée, une seule politique
   d'authentification.
2. **Le backend est le seul écrivain** dans PostgreSQL. L'AI Service peut lire
   (RAG, tool calling) mais toute écriture passe par l'API backend.
3. **Temps réel** : le backend pousse alertes/notifications au frontend via
   WebSocket (STOMP sur SockJS) ; pas de polling.

## 4. Niveau C4-3 — Composants du backend (Clean Architecture)

Organisation en modules Maven alignés sur les couches (voir ADR-002) :

```
backend/
├── smartsoc-domain/          # Entités métier, value objects, ports (interfaces),
│                             #   règles métier pures — AUCUNE dépendance framework
├── smartsoc-application/     # Cas d'utilisation (use cases), orchestration,
│                             #   transactions, événements applicatifs
├── smartsoc-infrastructure/  # Adaptateurs sortants : JPA/PostgreSQL, clients
│                             #   Feign (AI Service, outils SOC), SMTP, WebSocket
└── smartsoc-api/             # Adaptateurs entrants : contrôleurs REST, DTO,
                              #   MapStruct, sécurité (JWT/RBAC), Swagger — module exécutable
```

Sens des dépendances : `api → application → domain ← infrastructure`
(le domaine ne dépend de rien ; l'infrastructure implémente ses ports).

### Domaines métier (bounded contexts DDD)

| Contexte | Responsabilité |
| --- | --- |
| `alerts` | Ingestion, normalisation, cycle de vie des alertes, score IA TP/FP |
| `incidents` | Incidents, cas d'investigation, timeline, escalade |
| `assets` | Inventaire des actifs supervisés, criticité |
| `intelligence` | IOC, CTI, mapping MITRE ATT&CK, enrichissement |
| `soar` | Playbooks, workflow engine, exécutions, versioning |
| `connectors` | Intégrations outils SOC (interface commune `SocConnector`) |
| `identity` | Utilisateurs, rôles (RBAC), authentification JWT, audit |
| `reporting` | Rapports, KPIs, notifications |

### Composants de l'AI Service

```
ai-service/
├── classifier/    # Modèle ML TP/FP : préprocessing, prédiction, métriques
├── agent/         # Agent conversationnel : providers LLM (Strategy pattern :
│                  #   Ollama | OpenAI | Claude | Gemini | Mistral), RAG, tools
├── rag/           # Ingestion documentaire, embeddings, recherche vectorielle
└── api/           # Endpoints FastAPI versionnés (/api/v1)
```

## 5. Sécurité

- **AuthN** : JWT access token (courte durée) + refresh token (rotation).
- **AuthZ** : RBAC — rôles `ADMIN`, `SOC_MANAGER`, `SOC_ANALYST`, `VIEWER`.
- **Secrets** : variables d'environnement uniquement (`.env` local, GitHub
  Secrets en CI, Azure Key Vault en production). Jamais dans le code.
- **Communication inter-services** : réseau Docker privé ; seuls frontend
  (via reverse proxy) et backend sont exposés.
- **Supply chain** : SonarQube, CodeQL, Trivy, Gitleaks, OWASP Dependency
  Check et Dependabot en CI.

## 6. Déploiement

| Étape | Cible | Mécanisme |
| --- | --- | --- |
| Développement | Poste local | `docker compose up` (profil `dev` : hot reload, ports exposés) |
| Démo / production | VM Azure (Ubuntu + Docker) | `docker compose` (profil `prod` : reverse proxy, TLS) |
| Évolution future | Kubernetes / AKS | Images 12-factor déjà compatibles ; non requis au départ |

## 7. Décisions d'architecture (ADR)

| ADR | Titre | Statut |
| --- | --- | --- |
| [ADR-001](adr/ADR-001-monorepo.md) | Monorepo pour l'ensemble de la plateforme | ✅ Accepté |
| [ADR-002](adr/ADR-002-clean-architecture-ddd.md) | Clean Architecture + DDD pour le backend | ✅ Accepté |
| [ADR-003](adr/ADR-003-ai-microservice.md) | Microservice IA indépendant (FastAPI) | ✅ Accepté |
| [ADR-004](adr/ADR-004-postgresql-source-of-truth.md) | PostgreSQL, source de vérité unique de la plateforme | ✅ Accepté |
| [ADR-005](adr/ADR-005-standalone-platform-integration-contracts.md) | Plateforme autonome, intégrations externes par contrats | ✅ Accepté |
| [ADR-006](adr/ADR-006-soc-on-azure-multi-account.md) | SOC sur Azure multi-comptes relié par overlay WireGuard | ✅ Accepté |

Le SOC déployé sur Azure fait l'objet d'une conception détaillée dédiée :
[`docs/architecture/soc/SOC-ARCHITECTURE.md`](soc/SOC-ARCHITECTURE.md).
