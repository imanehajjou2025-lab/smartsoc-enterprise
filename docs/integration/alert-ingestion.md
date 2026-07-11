# Contrat d'intégration — Ingestion des alertes

> **Public :** l'équipe qui administre les outils SOC (Wazuh, Suricata,
> Shuffle, connecteurs…). Ce document est LE contrat à respecter pour
> pousser des alertes dans SmartSOC (ADR-005 : intégration par
> configuration, sans modification de la plateforme).

## Endpoint

```
POST /api/v1/ingest/alerts
Content-Type: application/json
X-API-Key: <SMARTSOC_INGEST_API_KEY>
```

- **Authentification** : header `X-API-Key`, valeur configurée côté
  plateforme par la variable d'environnement `SMARTSOC_INGEST_API_KEY`.
  Clé absente/incorrecte → `401` (RFC 9457). Variable vide côté
  plateforme = ingestion désactivée.
- **Idempotence** : le couple `(source, externalId)` identifie l'événement.
  Première ingestion → `201 Created`. Replay du même événement →
  `200 OK` avec l'alerte existante (jamais de doublon, jamais d'erreur —
  vos retries sont sûrs).

## Schéma du payload

| Champ | Type | Obligatoire | Description |
| --- | --- | --- | --- |
| `source` | string ≤ 50 | ✅ | Outil émetteur (`wazuh`, `suricata`, `shuffle`…). Normalisé en minuscules. |
| `externalId` | string ≤ 255 | ✅ | Identifiant de l'événement chez l'émetteur (clé d'idempotence). |
| `title` | string ≤ 500 | ✅ | Titre lisible de l'alerte. |
| `description` | string | — | Détail lisible. |
| `severity` | enum | ✅ | `CRITICAL` \| `HIGH` \| `MEDIUM` \| `LOW` \| `INFO` — mappez votre échelle vers ces 5 niveaux. |
| `detectedAt` | ISO-8601 UTC | ✅ | Horodatage de détection chez l'émetteur (≠ réception). |
| `hostname` | string ≤ 255 | — | Actif concerné. |
| `ruleId` | string ≤ 100 | — | Règle de détection (ex. règle Wazuh `5710`). |
| `mitreTechniques` | string[] | — | Techniques ATT&CK (ex. `["T1110"]`). |
| `rawPayload` | objet JSON | — | **L'événement brut intégral**, tel quel — conservé en JSONB pour l'investigation. |

## Guide de mapping des sévérités

| Outil | Échelle source | → SmartSOC |
| --- | --- | --- |
| Wazuh | level 12–15 / 9–11 / 6–8 / 3–5 / 0–2 | CRITICAL / HIGH / MEDIUM / LOW / INFO |
| Suricata | severity 1 / 2 / 3 | HIGH / MEDIUM / LOW |

## Exemple

```bash
curl -X POST "$SMARTSOC_URL/api/v1/ingest/alerts" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $SMARTSOC_INGEST_API_KEY" \
  -d '{
    "source": "wazuh",
    "externalId": "1720684800.123456",
    "title": "sshd: brute force trying to get access to the system",
    "description": "Multiple authentication failures followed by a success",
    "severity": "HIGH",
    "detectedAt": "2026-07-11T08:15:30Z",
    "hostname": "srv-web-01",
    "ruleId": "5712",
    "mitreTechniques": ["T1110"],
    "rawPayload": { "rule": { "id": "5712", "level": 10 }, "agent": { "name": "srv-web-01" } }
  }'
```

## Réponses

- `201` / `200` : corps = l'alerte normalisée (avec son `id` SmartSOC et son `status`).
- `400` : payload invalide — le corps RFC 9457 liste les champs en erreur (`errors`).
- `401` : clé d'API absente ou incorrecte.

## Simulation (démo sans SOC)

Le script [`scripts/simulate-alerts.sh`](../../scripts/simulate-alerts.sh)
(ou `.ps1` sous Windows) injecte un jeu d'alertes réalistes multi-sources.
La spécification exécutable complète est sur Swagger : `/swagger-ui.html`.
