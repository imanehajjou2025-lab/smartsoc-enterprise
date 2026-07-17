# ai-service

Les deux services IA de SmartSOC — **classifieur TP/FP** et **agent
conversationnel SOC** — sont développés et opérés **en dehors de ce dépôt**
(ADR-005). Ce répertoire ne contiendra jamais d'implémentation IA.

L'intégration avec la plateforme est régie par les contrats OpenAPI publiés
dans [`docs/integration/`](../docs/integration/README.md) :

- [`ai-classifier-api.yaml`](../docs/integration/ai-classifier-api.yaml) —
  classification True Positive / False Positive des alertes ;
- [`ai-assistant-api.yaml`](../docs/integration/ai-assistant-api.yaml) —
  assistant conversationnel des analystes.

Architecture d'intégration côté plateforme (ports, mode simulation,
résilience) : [ADR-008](../docs/architecture/adr/ADR-008-ai-integration-architecture.md).
