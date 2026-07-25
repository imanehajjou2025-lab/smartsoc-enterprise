# ADR-012 — SOAR : playbooks documentés et suivi d'exécution guidée

- **Statut** : Accepté
- **Date** : 2026-07-26
- **Décideurs** : Équipe SmartSOC

## Contexte

`soar` est l'un des 8 bounded contexts déclarés dès ADR-002, décrit dans
`ARCHITECTURE.md` comme *« Playbooks, workflow engine, exécutions,
versioning »* — un vrai moteur interne, distinct de `connectors`
(intégrations outils SOC, jamais construit).

**Fait déterminant, comme ADR-004 pour Hunting** : `SOC-ARCHITECTURE.md`
documente que **Shuffle**, opéré par un membre de l'équipe hors de ce
dépôt, **est** le moteur d'automatisation réel du SOC. Il reçoit les
alertes Wazuh, interroge MISP, exécute ses propres playbooks (actions :
email, blocage IP), puis **pousse déjà** ses résultats vers SmartSOC via
le webhook d'ingestion existant (`source=shuffle`, observé dans les
données de démonstration dès le jalon Alertes). Le module SOAR de la
plateforme ne doit donc pas réinventer un orchestrateur qui appelle des
outils externes — ce rôle appartient à `connectors`, différé exactement
comme le mode `live` de Hunting (ADR-011) faute de schéma d'intégration
réel à cette étape du projet.

## Décision

### 1. Le module documente et suit, il n'automatise pas

Un `Playbook` est une **procédure de réponse documentée** (étapes
ordonnées) ; une `PlaybookExecution` est le **suivi guidé** de cette
procédure par un analyste contre un incident — cocher, noter, terminer.
Aucune étape n'exécute d'action externe automatiquement en V1. La porte
reste ouverte pour qu'une étape future déclenche réellement une action
(via `connectors`, un jour), sans que la forme actuelle n'ait à changer.

### 2. Cible limitée à l'incident (pas l'alerte) en V1

La réponse structurée a lieu là où le travail d'analyste se fait déjà
(timeline, assignation, ADR existantes des Incidents). Une alerte isolée
s'escalade d'abord en incident (fonctionnalité déjà livrée), puis un
playbook s'exécute contre l'incident. Évite un modèle de cible
polymorphe (`ALERT`/`INCIDENT`) pour une V1 qui n'en a pas l'usage.

### 3. Versioning léger, pas d'historique séparé

`Playbook.version` est un simple compteur incrémenté à chaque édition —
aucune table `playbook_versions`. L'auditabilité réelle est assurée
autrement : chaque `PlaybookExecution` **fige sa propre copie**
(`playbookVersion`, `playbookName`, et le titre de chaque étape) au
démarrage. Éditer le gabarit après coup ne change donc **jamais**
silencieusement une exécution déjà démarrée ou terminée — vérifié par un
test E2E dédié. Une table d'historique complète (consultable même sans
exécution associée) reste une évolution possible si le besoin apparaît.

### 4. Étapes de gabarit en JSONB, étapes d'exécution en table dédiée

`Playbook.steps` (le gabarit, réécrit en bloc à chaque édition) est un
`List<PlaybookStepTemplate>` — un **record plat**, sans hiérarchie
scellée, sérialisé nativement par Jackson en JSONB sans codec dédié
(contraste avec l'arbre de critères de Hunting, qui en nécessitait un :
la différence est la présence ou non de polymorphisme). `order` est
**toujours dérivé de la position dans la liste**, jamais fait confiance à
une valeur fournie par l'appelant — aucun trou ni doublon possible par
construction.

`PlaybookExecutionStep` (le suivi, chaque étape ayant son propre cycle de
vie — statut, note, date de complétion) est en revanche une **table
dédiée**, exactement le patron déjà éprouvé de `CaseTask` (Investigations) :
transitions volontairement libres (« une checklist se coche et se
décoche »), avec `SKIPPED` en plus de `TODO`/`IN_PROGRESS`/`DONE` — une
procédure générale ne s'applique pas forcément intégralement à chaque
incident précis. Pas de gate de complétion sur les étapes : terminer une
exécution reste un jugement d'analyste.

### 5. Pas de suppression de playbook

Un playbook est une connaissance institutionnelle (une procédure de
réponse), pas un gabarit de recherche personnel comme une requête de
chasse (Hunting) : il s'archive (`POST /playbooks/{id}/archive`), il ne
se supprime jamais.

## Conséquences

**Positives.** Le module apporte une valeur réelle et livrable
immédiatement (documenter et suivre des procédures de réponse) sans
dépendre de l'infrastructure SOC réelle. Le contact avec `Shuffle` reste
celui déjà établi (webhook d'ingestion), aucune nouvelle surface
d'intégration. La réutilisation du patron `CaseTask` évite toute
redécouverte de conception.

**Négatives.** Aucune automatisation réelle en V1 — un analyste doit
suivre chaque étape manuellement, y compris celles qui pourraient un jour
être automatisées via `connectors`.

**Limitations actées.** Pas d'imbrication conditionnelle entre étapes
(liste plate ordonnée, pas de branchement si/sinon). Pas d'historique de
version consultable en dehors des exécutions qui l'ont utilisée.

## Alternatives écartées

| Alternative | Raison du rejet |
| --- | --- |
| Appeler l'API Shuffle depuis une étape (mode live dès la V1) | Schéma d'intégration Shuffle deviné sans accès réel = mapping fictif à refaire (même raisonnement qu'OpenSearch, ADR-011) |
| Cible polymorphe alerte/incident | Complexité de résolution de cible non justifiée par un besoin réel en V1 |
| Table `playbook_versions` séparée | L'auditabilité réelle (une exécution ne change jamais) est déjà garantie par le snapshot ; l'historique complet resterait sans utilisateur concret |
| Suppression de playbook autorisée | Un playbook est une procédure institutionnelle, pas un gabarit personnel — la doctrine no-delete de la plateforme s'applique |
| Étapes de gabarit en table dédiée (comme les exécutions) | Un gabarit n'a pas de cycle de vie par étape ; le JSONB réécrit en bloc à chaque édition est plus simple et suffisant |

## Références

- ADR-002 — Clean Architecture / DDD (bounded context `soar`, `connectors`)
- ADR-006 — SOC sur Azure multi-comptes (Shuffle, compte CTI/SOAR/Offensif)
- ADR-011 — Threat Hunting (même doctrine : moteur externe réel, capacité interne différée jusqu'à un schéma réel)
- `docs/architecture/soc/SOC-ARCHITECTURE.md` — position de Shuffle dans le SOC
- `docs/rapport-pfe/journal-de-bord.md` — mesures et incidents du jalon
