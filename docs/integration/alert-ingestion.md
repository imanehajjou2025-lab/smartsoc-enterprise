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
| `observables` | objet[] | — | Ce que l'événement **cite explicitement** : IP, domaine, URL, hash, e-mail. Voir ci-dessous. |
| `rawPayload` | objet JSON | — | **L'événement brut intégral**, tel quel — conservé en JSONB pour l'investigation. |

## Observables — enrichissement par le renseignement (CTI)

Champ **entièrement optionnel**. Absent, `null` ou `[]`, l'ingestion se
comporte exactement comme avant son introduction : aucun producteur
existant n'a à changer quoi que ce soit.

Déclarer des observables permet à SmartSOC de rapprocher automatiquement
l'alerte du référentiel IOC (`docs/integration/cti-ioc-ingestion.md`) et
de dire à l'analyste « cette adresse est un C2 connu ».

> **Pourquoi les déclarer plutôt que nous laisser les deviner ?**
> Extraire les observables de `rawPayload` par expressions régulières
> attraperait aussi bien la version d'un agent qu'un identifiant de
> règle. En SOC, un faux rattachement coûte plus cher qu'une absence :
> il consomme du temps d'analyste et érode la confiance dans l'outil.
> Vous seul connaissez le format de votre outil (ADR-009).

### Un élément d'`observables`

| Champ | Type | Obligatoire | Description |
| --- | --- | --- | --- |
| `type` | enum | ✅ | `IPV4` \| `IPV6` \| `DOMAIN` \| `URL` \| `MD5` \| `SHA1` \| `SHA256` \| `EMAIL` |
| `value` | string ≤ 2048 | ✅ | La valeur brute. **Défangée ou en majuscules : accepté** — la normalisation appartient au serveur. |

Jusqu'à **100 observables** sont retenus par alerte ; au-delà le surplus
est écarté et signalé. Un tableau de plus de **1000 entrées** est refusé
en `400` (garde anti-abus).

### Tolérance : un observable invalide ne coûte jamais l'alerte

Une valeur qui n'est pas du type annoncé est **écartée individuellement**
et l'alerte est créée quand même — perdre une détection à cause d'un
champ annexe serait un très mauvais échange. Le statut reste `201`/`200`.

La réponse contient donc deux ajouts :

- **`observables`** : ce qui a été **retenu**, sous sa forme
  **normalisée** — vous voyez exactement ce qui servira à la corrélation ;
- **`observableReport`** : le compte rendu de lecture.

```json
{
  "id": "…", "source": "wazuh", "severity": "HIGH", "…": "…",
  "observables": [
    { "type": "DOMAIN", "value": "evil-c2.com" },
    { "type": "IPV4",   "value": "45.83.12.7" }
  ],
  "observableReport": {
    "accepted": 2,
    "rejected": 1,
    "errors": [
      {
        "index": 2,
        "type": "SHA256",
        "value": "pas-un-hash",
        "code": "INVALID_SHA256",
        "message": "Value 'pas-un-hash' is not a 64-character hexadecimal SHA256 hash (indicator type SHA256)"
      }
    ]
  }
}
```

- **`code` est la valeur contractuelle et stable** — `INVALID_SHA256`,
  `INVALID_DOMAIN`, `INVALID_IPV4`… ou `TOO_MANY_OBSERVABLES`. C'est sur
  elle que vous branchez un traitement.
- **`message` est informatif et peut évoluer.** Ne l'analysez pas : vous
  vous lieriez à une formulation, pas à une règle.
- `index` est la position dans **le tableau que vous avez envoyé**, pour
  retrouver l'entrée fautive dans votre mapping.
- `observableReport` n'apparaît **que** dans la réponse d'ingestion : en
  consultation d'alerte, il n'y a rien à rendre compte, le champ est
  absent.
- Un **rejeu** du même événement renvoie le même compte rendu : il décrit
  ce que la plateforme a compris de votre payload, pas ce qu'elle a écrit
  en base — une erreur de mapping ne disparaît pas au second envoi.

### Normalisation appliquée

Identique à celle du référentiel IOC — c'est ce qui rend la corrélation
possible : **refangage** (`1.2.3[.]4`, `hxxp://`, `evil(dot)com`),
minuscules, canonicalisation IPv6, point final de FQDN retiré. Seule
exception : le **chemin d'une URL conserve sa casse** (`/Login` et
`/login` sont deux ressources).

### Consultation

`GET /api/v1/alerts/{id}/threat-intel` renvoie les observables de
l'alerte **et** les indicateurs actifs correspondants. Les observables
sans correspondance y figurent aussi : « 3 observables, 1 connu » est une
information utile. Un IOC déclaré **après** l'alerte y apparaît dès sa
création, sans traitement de rattrapage.

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
