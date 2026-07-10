# SmartSOC Enterprise

[![CI Sanity](https://github.com/imanehajjou2025-lab/smartsoc-enterprise/actions/workflows/ci-sanity.yml/badge.svg)](https://github.com/imanehajjou2025-lab/smartsoc-enterprise/actions/workflows/ci-sanity.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB.svg)](https://react.dev/)
[![Python](https://img.shields.io/badge/Python-FastAPI-3776AB.svg)](https://fastapi.tiangolo.com/)

> **Plateforme intelligente unifiée** intégrant DevSecOps, SOC intelligent, SOAR et Intelligence Artificielle pour la détection des menaces, la réduction des faux positifs et l'amélioration continue de la posture de sécurité des infrastructures numériques.

## 🎯 Vision

SmartSOC Enterprise centralise les composants essentiels d'un écosystème de cybersécurité moderne dans une plateforme web unique, inspirée des architectures de Microsoft Sentinel, Elastic Security, Cortex XSIAM et Splunk Enterprise Security :

- **Centralisation** des alertes et événements provenant des outils SOC (Wazuh, Suricata, Zeek, TheHive, MISP…)
- **Gestion des incidents** et des cas d'investigation
- **Enrichissement automatique** des alertes (CTI, VirusTotal, MITRE ATT&CK)
- **Réduction des faux positifs** par Machine Learning (classification TP/FP)
- **Assistant IA conversationnel** pour les analystes SOC (Ollama, RAG, Tool Calling)
- **Moteur SOAR** avec playbooks graphiques et réponses automatisées
- **Tableaux de bord temps réel** (WebSocket)
- **Pipeline DevSecOps** complet (SonarQube, Trivy, Gitleaks, OWASP Dependency Check, CodeQL)

## 🏗️ Architecture

```
┌─────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│  Frontend React │──▶│ Backend Spring   │──▶│  AI Service      │
│  TypeScript/MUI │◀──│ Boot 3 / Java 21 │◀──│  Python/FastAPI  │
└─────────────────┘   └────────┬─────────┘   └────────┬─────────┘
        WebSocket              │ JPA/Flyway           │ Ollama
                      ┌────────▼─────────┐   ┌────────▼─────────┐
                      │   PostgreSQL     │   │  ML TP/FP + RAG  │
                      └──────────────────┘   └──────────────────┘
                               ▲
                    Connecteurs SOC (REST / Syslog / Webhooks)
              Wazuh · Suricata · OpenSearch · TheHive · MISP · Shuffle
```

## 📁 Structure du dépôt

| Répertoire    | Description                                              |
| ------------- | -------------------------------------------------------- |
| `backend/`    | API REST Spring Boot 3 (Java 21, Clean Architecture, DDD) |
| `frontend/`   | SPA React + TypeScript + Vite + Material UI              |
| `ai-service/` | Microservice IA Python/FastAPI (ML TP/FP, Agent IA, RAG) |
| `database/`   | Migrations Flyway, scripts SQL                           |
| `docker/`     | Dockerfiles et Docker Compose                            |
| `deployment/` | Déploiement Azure / Kubernetes                           |
| `docs/`       | Architecture, ADR, diagrammes UML                        |
| `monitoring/` | Observabilité (Actuator, Micrometer, Prometheus)         |
| `scripts/`    | Scripts utilitaires                                      |
| `tests/`      | Tests E2E inter-services                                 |

## 🚀 Démarrage rapide

> ⚠️ Le projet est en cours de construction. Les instructions de démarrage seront complétées au fur et à mesure des jalons.

```bash
git clone https://github.com/imanehajjou2025-lab/smartsoc-enterprise.git
cd smartsoc-enterprise
# docker compose up -d   (disponible prochainement)
```

### Prérequis

- JDK 21 (LTS) · Maven 3.9+ · Node.js 24+ · Docker Engine 29+ · Docker Compose v5+ · PostgreSQL 18

## 🔀 Workflow Git

Le projet suit **Git Flow** avec **Conventional Commits** et **Semantic Versioning** :

- `main` — versions stables (releases uniquement)
- `develop` — branche d'intégration (par défaut)
- `feature/*` · `release/*` · `hotfix/*`

Voir [CONTRIBUTING.md](CONTRIBUTING.md) pour les détails.

## 📄 Licence

Distribué sous licence [MIT](LICENSE).

## 👥 Équipe

Projet de fin d'études — plateforme SmartSOC Enterprise.
