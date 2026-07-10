# ADR-003 — Microservice IA indépendant (Python/FastAPI)

- **Statut** : Accepté
- **Date** : 2026-07-10
- **Décideurs** : Équipe SmartSOC

## Contexte

La plateforme embarque deux capacités IA : (1) un classifieur ML True
Positive / False Positive entraîné sur un dataset fourni, et (2) un agent
conversationnel LLM (Ollama en local, avec RAG et tool calling). L'écosystème
IA/ML est nativement Python (scikit-learn, pandas, transformers, bibliothèques
LLM) ; le backend est en Java.

## Décision

Toute l'IA vit dans un **microservice indépendant** `ai-service` en
**Python 3.12 + FastAPI**, consommé par le backend via REST (OpenFeign).
Le backend ne contient **aucune logique IA** — uniquement l'orchestration
métier (quand appeler l'IA, que faire du résultat).

L'accès aux LLM passe par une abstraction **Provider (Strategy pattern)** :
`OllamaProvider` par défaut, remplaçable par OpenAI/Claude/Gemini/Mistral par
simple configuration, sans toucher au reste du code.

## Justification

- **Bon outil pour le bon travail** : l'entraînement, l'inférence ML et
  l'outillage LLM/RAG sont incomparablement plus matures en Python.
- **Cycle de vie découplé** : on réentraîne/redéploie le modèle TP/FP sans
  redéployer le backend ; on change de LLM sans toucher à la plateforme.
- **Isolation des ressources** : l'inférence (CPU/RAM intensive) ne dégrade
  pas les temps de réponse de l'API métier ; scalable indépendamment.
- **Frontière d'API claire** : le contrat REST (OpenAPI) entre backend et
  AI Service documente exactement ce que l'IA fournit.

## Conséquences

- Un conteneur supplémentaire dans Docker Compose (+ Ollama).
- Le contrat REST backend ↔ AI Service doit être versionné (`/api/v1`).
- Résilience obligatoire côté backend : timeouts, Spring Retry, comportement
  dégradé si l'AI Service est indisponible (les alertes restent traitables
  sans score IA).
- L'AI Service accède en **lecture seule** aux données (RAG, tools) ; toute
  écriture repasse par l'API backend.
