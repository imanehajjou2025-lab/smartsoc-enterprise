# ADR-011 — Threat Hunting : requêtes structurées sur les alertes ingérées

- **Statut** : Accepté
- **Date** : 2026-07-25
- **Décideurs** : Équipe SmartSOC

## Contexte

Le Threat Hunting est la chasse proactive : l'analyste formule une requête
structurée au lieu d'attendre qu'une alerte se déclenche. La plateforme
doit permettre de sauvegarder, exécuter et rejouer de telles requêtes.

**ADR-004 (déjà acceptée) a tranché le rôle de fond de ce module** : *« Les
événements bruts massifs restent dans OpenSearch (côté outils SOC) : la
plateforme n'en stocke que les alertes normalisées [...] et interroge
OpenSearch à la demande via les connecteurs. »* `connectors` est l'un des 8
bounded contexts déclarés par ADR-002, jamais construit.

Un second fait, antérieur, oriente la portée immédiate : le jalon Alertes
A1 a choisi `raw_payload` en JSONB **explicitement pour être requêtable
plus tard en threat hunting**, sans jamais construire cette capacité. Ce
jalon lui donne enfin son usage.

## Décision

### 1. Portée V1 : simulation PostgreSQL, `live` OpenSearch différé

Un port `HuntExecutionPort` (même doctrine que le classifieur IA, ADR-008,
et le semis/import MITRE) avec deux adaptateurs prévus :

- **`simulation`** (PostgreSQL, sur les alertes déjà ingérées) — **seul
  livré ici**. Démontrable seule, aucune dépendance à une infra SOC.
- **`live`** (OpenSearch, via `connectors`) — chemin déjà décidé par
  ADR-004, **explicitement différé**. Deviner le schéma d'index Wazuh réel
  (noms de champs comme `rule.id`, `agent.name`) sans l'infra réelle
  produirait un mapping fictif à refaire — le coût d'un raccourci, pas
  d'une simulation honnête.

Le port ne connaît pas l'origine des critères (sauvegardée ou ad hoc) :
c'est la couche application qui attribue l'identité du hunt au résultat.

### 2. Arbre de critères extensible dès la V1, sans risque de rupture d'API

`HuntNode` (interface scellée) = `HuntCondition` (feuille) |
`HuntGroup` (`AND`/`OR`/`NOT`, enfants récursifs). La structure complète
existe dès maintenant, y compris l'imbrication et `OR`/`NOT` — c'est
`HuntQuery`, et seulement lui, qui restreint pour l'instant la **racine** à
un `AND` de conditions plates (`HUNT_LOGICAL_OPERATOR_UNSUPPORTED`,
`HUNT_NESTED_GROUPS_UNSUPPORTED`). Le schéma JSONB et le contrat API sont
donc **déjà** la forme finale ; débloquer l'imbrication plus tard change
une validation applicative, jamais une migration ni un DTO.

Chaque `HuntField` déclare ses opérateurs compatibles et sa propre
normalisation de valeur (même doctrine que `IndicatorType`) —
`MITRE_TECHNIQUE` réutilise directement `MitreTechniqueId.normalize()` :
une chasse sur une technique compare exactement la même clé que le
catalogue ATT&CK, jamais une forme dérivée.

### 3. Objets de résultat réutilisables par un futur SOAR

`HuntExecutionResult` compose trois pièces indépendantes :
`HuntExecutionSummary` (métadonnées seules : `huntId`, `executedAt`,
`tookMillis`, `matchedCount`, `truncated` — consommable par un futur moteur
SOAR sans charger la page de résultats), `HuntStatistics` (répartition du
jeu de résultats, miroir de `AlertStatistics` scopé à la chasse), et
`PageResult<Alert>` (réutilisation totale du type `Alert` existant, aucun
DTO de résultat dupliqué).

`truncated` vaut toujours `false` en mode simulation (comptage exact
PostgreSQL) : le champ existe dès maintenant parce qu'OpenSearch, lui,
tronque réellement le comptage au-delà d'un seuil (`track_total_hits`) — la
sémantique deviendra vraie sans changement de contrat au jour du mode
`live`.

### 4. Visibilité déclarée mais non appliquée

`HuntVisibility{PRIVATE,TEAM}` sur `HuntQuery`, défaut `PRIVATE`. **Non
enforcée en V1** : aucune requête sauvegardée n'est aujourd'hui filtrée par
visibilité, comme rien d'autre sur la plateforme ne cloisonne les données
par utilisateur. Le champ existe pour qu'une future restriction (`PRIVATE`
visible seulement par son auteur, via `createdBy` déjà porté par toute
entité) n'exige aucune migration de schéma. Documenté explicitement en
Javadoc et en commentaire de colonne pour ne jamais laisser croire qu'une
chasse « privée » l'est réellement aujourd'hui.

### 5. Aucun état d'exécution persisté

Pas de table `hunt_executions`. Chaque exécution est un calcul à la
lecture : `HuntQuery.lastExecutedAt` n'est qu'un repère d'usage récent (mis
à jour comme effet de bord), pas une trace d'audit. Une historisation des
exécutions reste une évolution possible, pas un manque — le besoin réel
(déclencher SOAR sur un volume de correspondances) est déjà couvert par
`HuntExecutionSummary`, consommable sans historisation.

### 6. Suppression réelle autorisée

Une requête de chasse sauvegardée n'est pas une pièce d'évidence SOC — un
gabarit de recherche, pas une alerte ni un cas. `DELETE
/api/v1/hunts/{id}` la supprime réellement, à la différence de la doctrine
« jamais de suppression » qui régit alertes, IOC, techniques ATT&CK et
actifs.

## Conséquences

**Positives.** `raw_payload` trouve enfin l'usage pour lequel il avait été
choisi en JSONB. La plateforme reste démontrable seule. Le port
d'exécution rend le futur mode `live` un ajout, pas une réécriture — zéro
refactoring, promesse déjà tenue deux fois sur cette plateforme.

**Négatives.** La chasse ne porte, en V1, que sur les alertes déjà
ingérées et normalisées — pas sur le volume brut complet que Wazuh/OpenSearch
conservent. `RAW_PAYLOAD_TEXT` est un `ILIKE` non indexé (Seq Scan accepté
à l'échelle actuelle, limitation documentée comme celle du FQDN des actifs).

**Limitations actées.** Pas d'imbrication `AND`/`OR`/`NOT` en V1 (le
domaine la refuse explicitement, nommément). Pas de visibilité appliquée.
Pas d'historique d'exécutions.

## Alternatives écartées

| Alternative | Raison du rejet |
| --- | --- |
| Client OpenSearch live dès la V1 | Schéma d'index Wazuh deviné sans infra réelle = mapping fictif à refaire |
| Racine de critères à plat (liste, sans arbre) | Casserait l'API/le schéma le jour de l'imbrication ; l'arbre coûte peu et évite la rupture |
| Table `hunt_executions` persistée | Aucun besoin réel non couvert par le calcul à la lecture + `HuntExecutionSummary` |
| Pas de champ `visibility` en V1 | Ajouter la colonne plus tard exigerait une migration ; l'inclure maintenant, non appliquée et documentée, ne coûte rien |
| Suppression interdite (doctrine no-delete) | Une requête sauvegardée n'est pas une preuve SOC, contrairement à tout le reste de la plateforme |

## Références

- ADR-002 — Clean Architecture / DDD (bounded context `connectors`, jamais construit)
- ADR-004 — PostgreSQL source de vérité (rôle d'OpenSearch, chemin `live` déjà décidé)
- ADR-008 — intégration IA (patron port/adaptateur simulation-live)
- ADR-010 — référentiel MITRE ATT&CK (réutilisation directe de `MitreTechniqueId`)
- `docs/rapport-pfe/journal-de-bord.md` — mesures et incidents du jalon
