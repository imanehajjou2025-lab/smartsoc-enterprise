# ADR-005 — Plateforme autonome, intégrations externes par contrats

- **Statut** : Accepté
- **Date** : 2026-07-10
- **Décideurs** : Équipe SmartSOC

## Contexte

La répartition des responsabilités du projet est confirmée : l'infrastructure
SOC (Wazuh, Suricata, Zeek, TheHive, Cortex, MISP, Shuffle, OpenCTI…) **et**
les deux modules d'IA — le classifieur de faux positifs (entraîné sur un
dataset propriétaire) et l'agent conversationnel basé sur Ollama — sont
développés et opérés **en dehors de ce dépôt**, par un autre membre de
l'équipe. Ce dépôt ne livre que la plateforme SmartSOC.

Ceci amende l'ADR-003 sur un point : le microservice IA n'est plus un
livrable de ce dépôt. Son *architecture d'intégration* (REST, provider
interchangeable, lecture seule) reste inchangée.

## Décision

La plateforme est développée comme un **produit autonome** qui ne dépend
d'aucun système externe pour démarrer, être testée ou être démontrée.
Chaque système externe est intégré exclusivement à travers un **contrat**
défini par la plateforme :

| Système externe | Contrat fourni par la plateforme |
| --- | --- |
| Classifieur TP/FP | Spécification OpenAPI (endpoint de scoring) + client OpenFeign résilient |
| Agent IA (Ollama) | Spécification OpenAPI (chat, analyse, résumé) + client OpenFeign résilient |
| Outils SOC | Interface commune `SocConnector` (REST/Syslog/Webhook) + endpoint d'ingestion d'alertes |
| Notifications | Adaptateurs sortants (e-mail, Teams, Slack) derrière des ports |

Règles d'implémentation :

1. **URLs et credentials externes = configuration pure** (variables
   d'environnement). Brancher le vrai SOC ou les vraies IA ne modifie
   aucune ligne de code.
2. **Dégradation gracieuse obligatoire** : chaque appel externe a un
   timeout, du retry, et un comportement défini en cas d'indisponibilité
   (ex. : alerte traitable sans score IA, badge « IA indisponible »).
3. **Mode simulation** : la plateforme embarque des stubs activables par
   configuration pour démontrer les flux de bout en bout sans les systèmes
   réels.
4. Le Docker Compose du dépôt ne contient **que** les services de la
   plateforme (PostgreSQL, backend, frontend) — jamais les outils SOC ni
   les services IA.

## Conséquences

- Les deux équipes avancent en parallèle sans se bloquer ; l'intégration
  finale est une opération de configuration, pas de développement.
- Les contrats OpenAPI (publiés dans `docs/integration/`) deviennent la
  référence commune : toute évolution est versionnée et négociée.
- Les tests d'intégration de la plateforme utilisent les stubs (WireMock ou
  équivalent), jamais les systèmes réels.
- Le répertoire `ai-service/` du monorepo est réservé aux éventuels stubs et
  aux contrats, pas à une implémentation IA.
