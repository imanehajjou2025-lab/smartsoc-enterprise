# ADR-006 — SOC sur Azure multi-comptes relié par overlay WireGuard

- **Statut** : Accepté
- **Date** : 2026-07-11
- **Décideurs** : Équipe SmartSOC (3 étudiants)

## Contexte

Le projet doit déployer un vrai SOC sur Microsoft Azure, connecté à la
plateforme SmartSOC. Contraintes : 3 étudiants, chacun avec un abonnement
**Azure for Students** distinct (tenants séparés, crédit ~100 $, quotas vCPU
limités). L'architecture doit être crédible devant un jury tout en restant
réalisable avec ces ressources.

## Décision

Déployer le SOC sur **trois comptes Azure distincts** organisés par domaine
fonctionnel (Cœur SIEM / Détection / CTI-SOAR-Offensif) et les relier en un
**réseau privé logique unique** via un **overlay VPN WireGuard**
(hub-and-spoke), plutôt qu'avec un Azure VPN Gateway.

L'intégration avec SmartSOC se fait exclusivement par le **webhook
d'ingestion** (`POST /api/v1/ingest/alerts`, `X-API-Key`), conformément à
l'[ADR-005](ADR-005-standalone-platform-integration-contracts.md).

## Justification

- **Multi-comptes = plus de quota** : chaque abonnement apporte son propre
  quota vCPU ; répartir SIEM / endpoints / CTI évite d'en saturer un seul.
- **WireGuard plutôt que VPN Gateway** : le VPN Gateway (~27 $/mois) épuise
  le crédit étudiant ; WireGuard sur une B1s est chiffré, léger, gratuit et
  parfaitement défendable techniquement. Il fonctionne entre tenants Azure
  différents, ce que le peering natif ne permet pas simplement.
- **Découpage par chaîne de valeur SOC** : détecter → corréler → répondre.
  Chaque étudiant possède un domaine cohérent et peut travailler en parallèle
  dès la mise en place du réseau.
- **Un seul point de vérité** : tous les agents pointent vers un unique Wazuh
  Manager ; une seule plateforme SmartSOC reçoit les alertes.

## Conséquences

- Un plan d'adressage disjoint est obligatoire (10.10/10.20/10.30 + overlay
  10.100.0.0/24) pour permettre le routage WireGuard.
- Le hub WireGuard (compte 1) est un point de dépendance : à documenter et
  sauvegarder (clés).
- Discipline de coût : alertes de budget et **deallocation** des VM
  inutilisées sont des exigences d'architecture, pas des options.
- Le couplage SOC ↔ SmartSOC reste un simple contrat REST : le SOC peut
  évoluer sans impacter la plateforme.
- Détail complet : [`docs/architecture/soc/SOC-ARCHITECTURE.md`](../soc/SOC-ARCHITECTURE.md).
