# ADR-008 — Architecture d'intégration des services IA

- **Statut** : Accepté
- **Date** : 2026-07-17
- **Décideurs** : Équipe SmartSOC

## Contexte

Les deux services IA du projet — le **classifieur True Positive / False
Positive** et l'**agent conversationnel SOC** — sont développés en dehors de
ce dépôt (ADR-005) et intégrés ultérieurement. La plateforme doit donc être
construite dès maintenant de façon que le branchement final soit une pure
opération de configuration, sans modification de code ni d'architecture.

L'ADR-003 fixe le style d'intégration (REST versionné, IA en lecture seule,
résilience obligatoire) et l'ADR-005 le principe des contrats. Le présent
ADR fixe l'architecture concrète côté plateforme.

## Décision

### 1. Contract-first

Les deux contrats OpenAPI, publiés dans `docs/integration/`, sont la seule
référence commune entre les équipes :

| Contrat | Fichier | Fournisseur | Client |
| --- | --- | --- | --- |
| Classifieur TP/FP | `ai-classifier-api.yaml` | service IA externe | backend SmartSOC |
| Agent conversationnel | `ai-assistant-api.yaml` | service IA externe | backend SmartSOC |

Toute évolution passe par une PR sur le fichier de contrat avec bump de
version sémantique, négociée entre les deux équipes avant implémentation.

### 2. Ports côté application, deux adaptateurs par port

Conformément à la Clean Architecture (ADR-002), la couche application
définit un **port** (interface Java) par service IA :

- `AlertClassifier` — demande un verdict TP/FP pour une alerte ;
- `SocAssistant` — poursuit une conversation d'assistance.

Chaque port a exactement deux adaptateurs dans la couche infrastructure,
sélectionnés par configuration (`smartsoc.ai.mode`) :

- **`simulation`** (défaut) : stub déterministe embarqué. La plateforme se
  démarre, se teste et se démontre de bout en bout sans aucun service
  externe (exigence ADR-005). Les réponses simulées sont marquées comme
  telles (ex. `modelVersion: simulation`).
- **`live`** : client **OpenFeign** pointant l'URL du vrai service,
  fournie par variable d'environnement, avec la clé d'API en en-tête.

### 3. Résilience et dégradation gracieuse

Chaque appel sortant applique :

| Règle | Classifieur | Assistant |
| --- | --- | --- |
| Timeout | 5 s | 120 s (génération LLM — révisé, un modèle auto-hébergé plus lourd en CPU peut prendre 60-90 s+ sur une réponse détaillée) |
| Circuit breaker | oui (Resilience4j) | oui (Resilience4j) |
| Comportement dégradé | l'alerte reste sans score/verdict, traitable normalement | la console affiche « assistant indisponible » |

Aucun appel IA n'est jamais sur le chemin critique : l'ingestion d'une
alerte répond au webhook **avant** la classification, qui est asynchrone
(événement `AlertIngestedEvent`) ; l'échec de classification est journalisé
et re-tentable manuellement, jamais bloquant.

### 4. Flux d'intégration

- **Classifieur** : à l'ingestion d'une alerte, la plateforme appelle le
  classifieur en asynchrone puis applique `Alert.applyAiAssessment(score,
  verdict)` (déjà présent dans le domaine) et pousse la mise à jour en
  temps réel (STOMP). Un endpoint autorise la (re)classification manuelle.
- **Assistant** : la plateforme expose son propre endpoint de chat,
  authentifié JWT + RBAC ; le service IA n'est **jamais** exposé
  directement au frontend ni à Internet. La plateforme reste propriétaire
  de l'historique des conversations ; le service IA est sans état.

## Conséquences

- Les équipes plateforme et IA avancent en parallèle ; l'intégration finale
  = renseigner `smartsoc.ai.mode=live` + deux URLs + deux clés d'API.
- Les tests d'intégration de la plateforme couvrent les deux adaptateurs :
  stubs en direct, clients Feign contre WireMock — jamais les services réels.
- Le Docker Compose du dépôt reste inchangé (plateforme seule, ADR-005).
- Le seuil de décision TP/FP appartient au modèle : la plateforme stocke ce
  que le service répond (score + verdict explicites) sans l'interpréter.
