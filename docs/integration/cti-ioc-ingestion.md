# Contrat d'intégration — Ingestion des indicateurs CTI

> **Public :** l'équipe qui administre les outils de renseignement
> (MISP, Shuffle, OpenCTI, flux ouverts…). Ce document est LE contrat à
> respecter pour alimenter le référentiel IOC de SmartSOC (ADR-005 :
> intégration par configuration, sans modification de la plateforme ;
> ADR-009 : flux poussés, jamais interrogés).

## Endpoint

```
POST /api/v1/ingest/iocs
Content-Type: application/json
X-API-Key: <SMARTSOC_INGEST_API_KEY>
```

- **Authentification** : même clé et même mécanisme que l'ingestion
  d'alertes (`X-API-Key`). Clé absente ou incorrecte → `401` (RFC 9457).
  Variable vide côté plateforme = tous les endpoints d'ingestion
  désactivés.
- **Sens du flux** : c'est VOUS qui poussez. SmartSOC ne se connecte à
  aucun MISP, ne détient aucun identifiant vers vos outils, et
  fonctionne parfaitement sans eux.
- **Idempotence** : le couple `(type, value)` **normalisé** identifie
  l'indicateur. Le repousser rafraîchit ses métadonnées au lieu
  d'échouer — vos rejeux et vos synchronisations périodiques sont sûrs.

## Schéma du payload

```json
{
  "feedSource": "misp",
  "indicators": [ { … }, { … } ]
}
```

| Champ | Type | Obligatoire | Description |
| --- | --- | --- | --- |
| `feedSource` | string ≤ 100 | ✅ | Le flux qui pousse (`misp`, `otx`, `abuse-ch`…). Normalisé en minuscules. Vaut pour tout le lot. |
| `indicators` | tableau | ✅ | 1 à **1000** éléments. Au-delà, découpez : un lot doit rester validable et journalisable. |

### Un élément de `indicators`

| Champ | Type | Obligatoire | Description |
| --- | --- | --- | --- |
| `type` | enum | ✅ | `IPV4` \| `IPV6` \| `DOMAIN` \| `URL` \| `MD5` \| `SHA1` \| `SHA256` \| `EMAIL` |
| `value` | string ≤ 2048 | ✅ | La valeur brute. **Défangée ou en majuscules : c'est accepté**, la normalisation appartient au serveur. |
| `confidence` | entier 0-100 | ✅ | Votre niveau de confiance. Mappez votre échelle vers 0-100. |
| `tlp` | enum | — | `CLEAR` \| `GREEN` \| `AMBER` \| `RED`. **Absent ⇒ `AMBER`** : l'absence de marquage n'est jamais lue comme « librement diffusable ». |
| `externalId` | string ≤ 255 | — | Identifiant de l'attribut chez vous (traçabilité). |
| `description` | string | — | Contexte lisible par un analyste. |
| `tags` | string[] | — | Libellés de recherche (`c2`, `ransomware`…). Normalisés en minuscules, dédoublonnés, **cumulés** entre passages. |
| `observedAt` | ISO-8601 UTC | — | Date de l'observation. Défaut : maintenant. Alimente `firstSeen`/`lastSeen`. |
| `validUntil` | ISO-8601 UTC | — | Fin de validité du renseignement. **Absent ⇒ pas de péremption.** La dernière observation fait foi. |

## Normalisation appliquée par le serveur

C'est cette forme normalisée, et elle seule, qui est stockée et
comparée aux observables des alertes.

| Type | Traitement |
| --- | --- |
| tous | **Refangage** : `1.2.3[.]4`, `hxxp://`, `evil(dot)com`, `contact[at]evil[.]com` retrouvent leur forme active |
| `IPV4` | validation en quadruplet pointé |
| `IPV6` | **canonicalisation** — `2001:DB8::1` et sa forme développée donnent la même clé |
| `DOMAIN` | minuscules, point final du FQDN absolu retiré |
| `URL` | schéma et hôte en minuscules, **chemin et requête inchangés** (`/Login` ≠ `/login`) |
| `MD5`/`SHA1`/`SHA256` | minuscules, longueur exacte vérifiée (32/40/64) |
| `EMAIL` | minuscules |

Une valeur qui n'est pas du type annoncé est **rejetée** : un IOC douteux
n'entre pas dans le référentiel.

## Réponses

Toujours `200`, avec un compte rendu. **Les indicateurs valides sont
ingérés même si d'autres sont rejetés** : un flux réel contient des
entrées malformées, et refuser le lot entier vous priverait de tout le
reste (ADR-009).

```json
{
  "received": 3,
  "created": 2,
  "updated": 0,
  "rejected": 1,
  "errors": [
    {
      "index": 1,
      "value": "ceci-n-est-pas-un-hash",
      "code": "INVALID_INDICATOR",
      "message": "Value 'ceci-n-est-pas-un-hash' is not a 64-character hexadecimal SHA256 hash (indicator type SHA256)"
    }
  ]
}
```

- `created` : nouveaux indicateurs — `updated` : identités déjà connues,
  métadonnées rafraîchies — `rejected` : `errors.length`.
- `index` est la **position dans votre tableau**, pour que vous puissiez
  corriger la source.
- `401` : clé d'API absente ou incorrecte.
- `400` : payload structurellement invalide (lot vide, > 1000 éléments,
  champ obligatoire manquant) — là, rien n'est ingéré.

## Exemple

```bash
curl -X POST "$SMARTSOC_URL/api/v1/ingest/iocs" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $SMARTSOC_INGEST_API_KEY" \
  -d '{
    "feedSource": "misp",
    "indicators": [
      {
        "type": "IPV4",
        "value": "45.83.12[.]7",
        "confidence": 85,
        "tlp": "AMBER",
        "externalId": "misp-attr-99812",
        "description": "Serveur C2 Cobalt Strike",
        "tags": ["c2", "cobalt-strike"],
        "observedAt": "2026-07-20T06:00:00Z",
        "validUntil": "2026-10-20T00:00:00Z"
      },
      {
        "type": "SHA256",
        "value": "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
        "confidence": 95,
        "tags": ["ransomware"]
      }
    ]
  }'
```

## Cycle de vie d'un indicateur

- **ACTIVE** — exploité pour l'enrichissement des alertes.
- **EXPIRED** — `validUntil` dépassée. **Déduit à la lecture**, jamais
  stocké : il n'existe aucun batch de péremption qui pourrait prendre du
  retard et laisser des IOC périmés se déclarer actifs.
- **REVOKED** — un analyste a tranché (faux positif). **La révocation
  survit à vos ré-envois** : continuer à pousser l'indicateur met bien à
  jour ses métadonnées mais ne le réactive pas. Pour le réhabiliter,
  passez par la console.

Un indicateur ne se supprime jamais : expiré ou révoqué, il reste
consultable — c'est une pièce d'historique CTI.

## Consultation

Le référentiel se lit sur `/api/v1/iocs` (JWT, tout utilisateur
authentifié) : filtres `type`, `status`, `feedSource`, `tag`,
`minConfidence`, `search`, et pagination. La spécification exécutable
complète est sur Swagger : `/swagger-ui.html`.
