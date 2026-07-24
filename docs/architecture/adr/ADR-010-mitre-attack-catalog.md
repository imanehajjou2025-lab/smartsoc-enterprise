# ADR-010 — Référentiel MITRE ATT&CK et corrélation des techniques

- **Statut** : Accepté
- **Date** : 2026-07-24
- **Décideurs** : Équipe SmartSOC

## Contexte

Les alertes SmartSOC portent déjà des techniques ATT&CK : `Alert` a un
champ `mitreTechniques`, une liste de chaînes brutes (`"T1059"`), stockée
en JSONB depuis le jalon Alertes (V3). Mais ces identifiants sont
**aveugles** — la plateforme ne connaît ni le nom d'une technique, ni sa
tactique, ni si elle est dépréciée. Le frontend en fait des puces qui
pointent vers `attack.mitre.org` sans rien afficher de local.

Il manque donc un **référentiel** de la matrice ATT&CK (tactiques,
techniques, sous-techniques) pour donner un sens à ces identifiants, et
la corrélation dans les deux sens : enrichir une alerte du nom et des
tactiques de ses techniques, et retrouver les alertes qui exercent une
technique donnée (couverture / retro).

La donnée ATT&CK est **publique et de référence** (CC-BY), publiée par le
MITRE en STIX 2.1. Elle n'est ni une infrastructure SOC ni un module IA :
c'est un catalogue à charger dans PostgreSQL (ADR-004), pas un service à
interroger au runtime. L'équipe dispose par ailleurs d'OpenCTI/MISP
(côté Ilyas), capables d'exporter la matrice — donc un chemin de
rafraîchissement existe.

Ce module est le **jumeau structurel de CTI** (ADR-009) : un référentiel
alimenté depuis l'extérieur, plus une corrélation calculée à la lecture.
Les décisions ci-dessous reprennent ce patron et notent, à chaque écart,
*pourquoi* MITRE diffère.

## Décision

### 1. L'identité d'une technique est son identifiant ATT&CK normalisé

L'identité est `attackId` (`T1059`, `T1059.001`), **finale** ; les
métadonnées (nom, description, URL, tactiques, dépréciation, version) sont
volatiles et rafraîchies par chaque import. `refreshFrom()` vérifie
l'identité et lève `MITRE_TECHNIQUE_IDENTITY_MISMATCH` plutôt que d'écraser
la clé de corrélation — exactement le rôle du couple (type, valeur) d'un
IOC ou du hostname d'un actif.

La normalisation (`MitreTechniqueId.normalize`) met en majuscule et valide
le format **caractère par caractère, en temps linéaire borné** (9
caractères max) : l'entrée vient de deux sources tierces — le bundle
importé ET les chaînes brutes des alertes — et une expression régulière à
rétro-suivi sur une entrée externe est un déni de service offert
(java:S8786, leçon CTI). La contrainte `ux_mitre_attack_id` grave l'unicité
en base.

### 2. La dépréciation SUIT l'import (elle ne survit pas au flux)

C'est l'écart notable avec CTI. La révocation d'un IOC est une **décision
d'analyste** qui survit aux ré-observations du flux (ADR-009 §3). La
dépréciation d'une technique, elle, est un **fait DU référentiel ATT&CK** :
c'est le MITRE qui déprécie, pas l'analyste. L'import fait donc foi dans
les deux sens, `deprecated` suit la dernière version importée.

Ce qui « survit », comme pour l'IOC périmé et l'actif décommissionné, c'est
la **non-suppression** : une technique dépréciée reste au catalogue pour
que les alertes historiques qui la référencent continuent de s'enrichir de
son nom. Les listes courantes la cachent par défaut (`includeDeprecated`
faux), jamais elles ne l'effacent.

### 3. Tactiques en enum, techniques en table, tactiques stockées en JSONB

Les **14 tactiques Enterprise** sont un vocabulaire fini et stable depuis
des années : un `enum MitreTactic`, comme `Severity` ou `IndicatorType`,
verrouillé par un test qui fige leurs identifiants et leur ordre de
colonnes (même garde que les 14 événements de timeline d'un cas). Pas une
table à maintenir.

Les **techniques** sont une table (`mitre_technique_catalog` — nommée
ainsi, et non `mitre_techniques`, pour la distinguer sans ambiguïté de la
colonne `alerts.mitre_techniques` à laquelle elle donne enfin un sens).
Les tactiques d'une technique sont stockées **en JSONB** et filtrées par
containment `@>`, en **réutilisant le `JsonbFunctionContributor`** déjà
construit pour les tags d'IOC — un petit ensemble dénormalisé (1 à 3
tactiques), jamais muté indépendamment, exactement le choix des tags.

### 4. Import de lot ÉLÉMENT PAR ÉLÉMENT

Un bundle est importé avec **tolérance par élément** : les techniques
valides entrent, les fautives (identifiant hors format, aucune tactique
reconnue) sont rejetées nommément (`index`, `attackId`, `code`, `message`),
le traitement continue. Les mêmes deux contraintes qu'en CTI imposent
l'architecture : une violation de contrainte rend la transaction PostgreSQL
irrécupérable (⇒ **une transaction par élément**), et un appel interne à une
méthode `@Transactional` court-circuite le proxy Spring (⇒ **un bean
séparé**). `MitreCatalogImportService` est donc non transactionnel et
délègue à `MitreCatalogService.importTechnique()`, bean distinct.

### 5. Alimentation HYBRIDE : semis embarqué + import par un admin

Deux chemins complémentaires, calqués sur la doctrine « simulation par
défaut / live » de l'IA (ADR-008) et du référentiel CTI :

- **Semis au démarrage.** Un `ApplicationRunner` idempotent (ne sème que
  si le catalogue est vide) charge un **sous-ensemble ATT&CK Enterprise
  versionné, embarqué** en ressource classpath. La plateforme est ainsi
  **démontrable seule**, matrice déjà peuplée, sans aucun import externe.
  Désactivable par `smartsoc.mitre.seed-on-startup=false`.
- **Import par un bundle.** `POST /api/v1/mitre/import`, **réservé aux
  administrateurs (JWT)**, pour rafraîchir le catalogue depuis un export
  ATT&CK (OpenCTI/MISP d'Ilyas, ou le MITRE directement).

**Écart avec CTI, assumé** : l'import MITRE est un endpoint **admin JWT**,
pas un webhook `X-API-Key` comme le flux CTI (ADR-009 §1). Raison : le
rafraîchissement d'un catalogue de référence est un **acte de gestion
rare**, fait par un administrateur, pas un flux continu poussé par un outil
SOC. Le webhook resterait une évolution possible si un jour OpenCTI devait
pousser la matrice automatiquement — sans remettre en cause ce choix.

### 6. La corrélation avec les alertes sera calculée à la lecture — PR-2

> **Portée — à lire avant de relire le code.** Cet ADR couvre l'ensemble du
> module, mais **PR-1 (référentiel MITRE) se limite au catalogue** :
> domaine, persistance V9, cas d'usage, semis, et API de consultation +
> import. La **corrélation arrive en PR-2** et n'est donc pas un oubli.

La correspondance techniques ↔ alertes se fera **sans retoucher le
domaine des alertes** : `alerts.mitre_techniques` est déjà là. On lit ce
JSONB — par containment `@>` pour le retro (technique → alertes) et par
agrégation `jsonb_array_elements_text` pour la heatmap de couverture. Le
**seul contact** avec la table `alerts` sera un index GIN sur cette
colonne (migration dédiée, unique point de contact inter-contextes, comme
l'index fonctionnel des actifs). Bénéfices, identiques à CTI (ADR-009 §6) :
retro-hunt gratuit, et compteur de couverture évalué au même instant et par
le même prédicat que la liste.

### 7. Étendue : matrice cœur (tactiques + techniques + sous-techniques)

Le module se limite à la matrice Enterprise. Les objets CTI plus riches
d'ATT&CK — mitigations, groupes d'attaquants, logiciels — sont **hors
périmètre** : ils n'apportent rien à l'enrichissement d'alerte ni à la
heatmap de couverture, et alourdiraient le modèle. Ils restent une
évolution possible, pas un manque.

## Conséquences

**Positives.** La matrice donne enfin un sens aux identifiants déjà portés
par les alertes, sans aucune migration des données existantes. La
plateforme reste autonome et démontrable seule (semis embarqué). Le module
se construit **sans toucher au domaine des alertes**. Le patron CTI est
réutilisé (identité immuable, upsert tolérant, JSONB `@>`), y compris le
`JsonbFunctionContributor` existant : peu de code neuf, des invariants déjà
éprouvés.

**Négatives.** Le semis embarqué est un **sous-ensemble curé**, pas la
matrice complète (~600 techniques) : un import est nécessaire pour la
couverture totale. La version ATT&CK embarquée doit être rafraîchie
manuellement à chaque montée de version du MITRE.

**Limitations actées.** Les identifiants de technique des alertes qui ne
correspondent à **aucune** entrée du catalogue restent visibles tels quels
(non résolus) plutôt que masqués — un identifiant inconnu est une
information, pas une erreur (même doctrine que l'observable sans
correspondance en CTI). La résolution d'une technique dépréciée reste
possible : c'est le but.

## Alternatives écartées

| Alternative | Raison du rejet |
| --- | --- |
| Client HTTP vers `attack.mitre.org` au runtime | Couplage réseau, plateforme non démontrable seule, contraire à ADR-004/005 |
| Semis par Flyway (INSERT SQL) | Données de référence volumineuses et versionnées mal maintenables en SQL ; l'import tolérant réutilise le domaine |
| Tactiques en table de jointure | Un enum fini et stable suffit ; JSONB `@>` réutilise l'infra CTI et garde le catalogue en une table |
| Webhook `X-API-Key` pour l'import | Le rafraîchissement d'un référentiel est un acte admin rare, pas un flux SOC continu |
| Retoucher `alerts` pour une table joignable des techniques | La colonne JSONB existe déjà ; un index GIN suffit, contact inter-contextes minimal |
| Colonne `deprecated` protégée du flux (comme la révocation IOC) | La dépréciation est un fait du référentiel ATT&CK, pas une décision d'analyste |

## Références

- ADR-004 — PostgreSQL source de vérité unique (Flyway)
- ADR-005 — plateforme autonome et contrats d'intégration
- ADR-008 — intégration IA (patron simulation/live du semis + import)
- ADR-009 — modèle CTI (patron identité immuable, upsert tolérant, JSONB
  `@>` ; contrastes de la dépréciation et de l'import admin)
- `docs/rapport-pfe/journal-de-bord.md` — mesures et incidents du jalon
