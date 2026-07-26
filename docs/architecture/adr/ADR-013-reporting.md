# ADR-013 — Reporting : instantanés de rapport et notifications

- **Statut** : Accepté
- **Date** : 2026-07-26
- **Décideurs** : Équipe SmartSOC

## Contexte

`reporting` est le huitième et dernier bounded context déclaré dès
ADR-002, décrit dans `ARCHITECTURE.md` comme *« Rapports, KPIs,
notifications »*. Le tableau de bord (`/dashboard`) couvre déjà les KPIs
**live** — toujours « maintenant ». Un rapport est un objet différent : un
**instantané figé** d'indicateurs sur une **période passée**, généré à la
demande, consultable et exportable après coup sans jamais changer — un
artefact d'audit, pas une vue.

ADR-005 documente par ailleurs que les notifications sont des
« adaptateurs sortants (e-mail, Teams, Slack) derrière des ports » —
décision déjà prise, restait à choisir quel canal construire en premier.

## Décision

### 1. Le rapport agrège en lecture seule à travers les contextes existants

`ReportGenerationService` lit, au moment de la génération, à travers
`alerts`, `incidents`, `soar`, `hunting` et `mitre` — même doctrine de
lecture inter-contextes déjà établie par `MitreCorrelationService`
(lit `AlertRepository`). Aucun état n'est modifié ailleurs : générer un
rapport n'a aucun effet de bord sur les autres modules.

### 2. Le jeu de métriques v1, avec ses limitations annoncées en clair

| Brique | Nature | Limitation |
| --- | --- | --- |
| Alerts | Vraie mesure de flux bornée à la période (`detectedAt`) | — |
| Incidents | Vraie mesure de flux (`openedAt`/nouveau `closedAt`) | — |
| SOAR | Vraie mesure de flux bornée à la période (`startedAt`) | — |
| Hunting | **Approximation** : nombre de requêtes sauvegardées dont `lastExecutedAt` tombe dans la période | Aucun historique d'exécution n'est persisté (ADR-011) — une requête réexécutée plusieurs fois ne compte qu'une fois, une requête réexécutée après la période ne compte plus |
| MITRE | **Snapshot cumulatif** au moment de la génération, pas borné à la période | La couverture est un état, pas un flux — refléter le passé exact demanderait de dater chaque contribution technique↔alerte |

Ces deux limitations restent visibles dans le rapport rendu (écran, CSV,
PDF) — jamais glissées entre parenthèses (règle du 2026-07-18).

### 3. `Incident.closedAt`, complément nécessaire de V4

Calculer un temps moyen de résolution exige de savoir QUAND un incident a
été clôturé — aucun module existant n'avait ce besoin. `closed_at`
(nullable, rétrocompatible) est ajouté à `incidents` (V13) et rempli par
`Incident.transitionTo()` à l'entrée dans l'état terminal `CLOSED`, aux
côtés de l'`openedAt` déjà existant.

### 4. Rapport immuable, jamais supprimé

Même doctrine que les incidents et les cas d'investigation : un rapport
généré est un artefact d'audit. `ReportRepository` n'expose ni mise à
jour ni suppression — seulement `save` (création), `findById`, `search`.

### 5. Export CSV et PDF, rendu déterministe sans appel externe

`ReportExporter` (port applicatif) a deux méthodes (`toCsv`, `toPdf`) et
un seul adaptateur : le rendu ne dépend d'aucun système externe (pas de
mode simulation/live nécessaire, contrairement aux notifications). PDF via
**OpenPDF** (fork LGPL maintenu d'iText 4, pur Java, sans dépendance
native) — première dépendance de génération de document du backend.

### 6. Notifications : un seul adaptateur (e-mail), simulation/live

`ReportNotifier` (port applicatif) suit **exactement** le patron déjà
validé pour l'IA (ADR-008) : `smartsoc.notifications.mode` = `simulation`
(défaut, journalise) ou `live` (SMTP réel via `spring-boot-starter-mail`,
propriétés `spring.mail.*`). Teams et Slack, mentionnés en ADR-005,
restent des extensions possibles du même port — non construites faute de
webhook réel à intégrer aujourd'hui, même raisonnement que
`connectors`/Shuffle-live (ADR-011, ADR-012). Une notification manquée
(SMTP indisponible…) ne fait jamais échouer la génération du rapport —
dégradation gracieuse, même doctrine que le classifieur IA en mode live.

### 7. RBAC déjà déterminé par le rôle existant

`Role.SOC_MANAGER` porte déjà, depuis sa définition (jalon Identity),
la responsabilité *« dashboards, reports, incident escalation »*, et
`Role.VIEWER` un accès *« read-only to dashboards and reports »* — la
génération est donc réservée à `ADMIN`/`SOC_MANAGER`, la lecture (liste,
détail, export) ouverte à tout utilisateur authentifié.

## Conséquences

**Positives.** Le module apporte une valeur réelle et livrable
immédiatement (rapport agrégé multi-contextes, export CSV/PDF,
notification e-mail simulable) sans dépendre d'aucune infrastructure SOC
réelle. Le patron simulation/live, déjà éprouvé pour l'IA, se réutilise à
l'identique pour les notifications — aucune redécouverte de conception.

**Négatives.** Les métriques Hunting et MITRE d'un rapport ne sont pas
des mesures de flux aussi fiables que celles d'alerts/incidents/soar —
limitation structurelle, pas un bug, mais qui doit rester lisible pour
l'utilisateur du rapport.

**Limitations actées.** Pas de génération planifiée (cron) en v1 —
génération à la demande uniquement, aucune infrastructure de scheduling
n'existait avant ce module et rien ne la demandait encore. Un seul canal
de notification construit (e-mail).

## Alternatives écartées

| Alternative | Raison du rejet |
| --- | --- |
| Dater chaque contribution technique↔alerte pour borner MITRE à la période | Changement de schéma non justifié par un besoin réel en v1 ; le snapshot cumulatif reste honnête tant qu'il est annoncé comme tel |
| Persister un historique d'exécution Hunting pour une vraie mesure de flux | Reviendrait sur la décision ADR-011 (« calculé à la lecture, aucun état persisté ») pour le seul bénéfice du reporting — disproportionné |
| Génération planifiée (cron) dès la v1 | Aucune infrastructure de scheduling existante, aucun besoin exprimé — ajouté seulement si demandé |
| Notifications Teams/Slack dès la v1 | Pas de webhook réel à intégrer aujourd'hui — même report que Shuffle-live (ADR-012) |
| Suppression de rapport autorisée | Un rapport est un artefact d'audit — la doctrine no-delete de la plateforme s'applique, comme pour les incidents et les cas |

## Références

- ADR-002 — Clean Architecture / DDD (bounded context `reporting`)
- ADR-005 — Contrats d'intégration de la plateforme standalone (notifications derrière des ports)
- ADR-008 — Architecture d'intégration IA (patron simulation/live repris à l'identique)
- ADR-011 — Threat Hunting (aucun historique d'exécution persisté, à l'origine de la limitation Hunting du rapport)
- ADR-012 — SOAR (doctrine no-delete des artefacts institutionnels/d'audit)
- `docs/rapport-pfe/journal-de-bord.md` — mesures et incidents du jalon
