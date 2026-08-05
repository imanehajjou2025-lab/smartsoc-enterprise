# ADR-014 — Architecture d'intégration de l'infrastructure SOC

- **Statut** : Accepté — *Architecture Baseline v1.0*
- **Date** : 2026-08-05
- **Décideurs** : Équipe SmartSOC
- **Document de référence** : `docs/architecture/soc-integration-plan.md`

## Contexte

La plateforme SmartSOC est développée et fonctionnelle (12 bounded contexts,
16 groupes d'endpoints, 15 migrations, 14 modules frontend, 104 fichiers de
test). L'infrastructure SOC est déployée séparément (ADR-005) : Wazuh Manager et
son Indexer OpenSearch, MISP, Shuffle, et des VM de détection, reliés par
WireGuard en Hub-and-Spoke.

La phase d'intégration commence. L'objectif fixé : **SmartSOC devient l'unique
interface utilisateur**, aucun opérateur n'accède directement au Wazuh Dashboard,
à MISP, à Shuffle ou à OpenSearch. Le backend joue le rôle de Gateway, Facade et
Orchestrateur.

Un audit du code a établi le point de départ réel et révélé trois faits qui
conditionnent l'architecture :

1. Le contexte `connectors`, déclaré parmi les 8 bounded contexts de l'ADR-002,
   n'a **jamais été construit**.
2. L'API Wazuh est une **API de gestion** : elle n'expose aucun endpoint de
   consultation d'alertes. Les alertes ne peuvent venir que du push (webhook
   déjà en place) ou de l'Indexer.
3. Aucune infrastructure de planification (`@EnableScheduling`) n'existe, alors
   que toute synchronisation périodique en dépend.

## Décision

### 1. Deux directions d'intégration, traitées séparément

L'intégration est scindée selon le sens du flux, car les deux régimes n'ont ni
le même coût ni le même risque :

| | Entrante (push) | Sortante (pull / act) |
| --- | --- | --- |
| Code plateforme | **déjà écrit** (webhook idempotent, clés d'API) | **entièrement à écrire** |
| Risque | faible | élevé |

L'intégration **ouvre par la direction entrante**, qui délivre des alertes
réelles pour un coût de développement nul et valide le chemin réseau avant tout
investissement en code sortant.

### 2. Le contexte `connectors` est construit comme bounded context

`domain/connectors` porte le vocabulaire de la **supervision des connecteurs**
(type, état, dernière synchronisation, exécutions). Les connecteurs eux-mêmes
sont des **adaptateurs sortants** : leur implémentation réside dans
`infrastructure`, leurs ports dans `application`.

Aucun package `connectors` n'est créé au niveau des couches — ArchUnit le
refuserait et cela contredirait l'ADR-002.

### 3. Un port par besoin, jamais par outil

Les ports sont nommés d'après le **besoin de la plateforme** :
`ThreatIntelPort`, `AgentInventoryPort`, `EventSearchPort` — jamais `MispPort`
ni `WazuhPort`. Remplacer un outil devient l'écriture d'un adaptateur, pas la
refonte d'un contrat.

Les ports d'intégration externe résident dans `application`, conformément à la
convention déjà en vigueur dans le code (`AlertClassifier`, `SocAssistant`,
`ReportNotifier`, `DatabaseBackupPort`), tandis que les ports de capacité
métier restent dans `domain` (`AlertRepository`, `HuntExecutionPort`).

### 4. Anti-Corruption Layer obligatoire par outil

Le modèle d'un outil externe (`WazuhAgentDto`, attribut MISP, document d'index)
ne quitte jamais son package d'infrastructure. Un mapper dédié — seul endroit du
projet connaissant les deux vocabulaires — assure la traduction. Si un outil
change de format, un seul fichier est impacté.

### 5. Deux adaptateurs par port, `simulation` par défaut

Chaque port possède un adaptateur `simulation` (données plausibles, marquées
comme simulées) et un adaptateur `live`. Le mode est choisi par configuration,
comme pour l'IA (ADR-008). La plateforme démarre, se teste en CI et se démontre
**sans aucun outil SOC allumé** — exigence ADR-005 maintenue.

### 6. La console lit PostgreSQL, jamais les outils en direct

Conformément à l'ADR-004, les données synchronisées sont persistées puis lues
depuis PostgreSQL. Un outil éteint ne produit jamais un écran vide : il produit
des données plus anciennes, avec leur date de dernière synchronisation affichée
honnêtement.

**Deux exceptions assumées**, justifiées par le besoin métier : l'enrichissement
VirusTotal et la chasse OpenSearch sont des opérations à la demande où
l'analyste attend une réponse fraîche. Appel synchrone, timeout court,
dégradation explicite.

### 7. Les actions à effet réel forment une classe à part

Le contrôle des agents Wazuh (redémarrage, active-response) et le déclenchement
de workflows Shuffle agissent sur des machines de production. Ils sont traités
ensemble, en dernière phase d'intégration, avec des garde-fous non négociables :
déclenchement **manuel** par un analyste, RBAC ANALYST+, confirmation explicite
nommant la cible, audit nominatif avec adresse IP, **aucun retry**, plafond
d'exécutions et marquage d'origine contre les boucles d'amplification.

### 8. L'intégration est additive

Aucun des 12 bounded contexts existants n'est refondu. Le seul agrégat modifié
est `Asset`, qui reçoit des colonnes nullables (système d'exploitation, dernier
contact, identifiant externe, source). Tous les tests existants doivent rester
verts à chaque PR — c'est la définition opérationnelle du caractère additif.

### 9. Ordre des phases

```
0 · Réseau + capture d'échantillons réels        ← bloque tout
1 · Wazuh   1.1 alertes (push) · 1.2 API lecture + socle · 1.3 vulnérabilités
2 · MISP        3 · VirusTotal
4 · OpenSearch (chasse réelle)
5 · Actions réelles (contrôle agents + Shuffle manuel)
6 · Validation complète de la plateforme
    ─── puis, hors périmètre fonctionnel : exposition Cloudflare Tunnel
```

Deux dépendances sont **dures** : la phase 0 conditionne tout le reste, et la
phase 4 exige que la phase 1.1 ait alimenté l'Indexer en événements réels — le
mapping d'index ne peut pas être écrit sur un schéma supposé.

## Conséquences

- **Positives.** Le remplacement d'un outil SOC devient l'écriture d'un
  adaptateur. La plateforme conserve son autonomie de démonstration et de test.
  La chasse OpenSearch n'ajoutera qu'un adaptateur, sans toucher au port, au
  service, à l'API ni à l'écran — démonstration concrète de l'architecture
  hexagonale. Les 5 outils partagent un socle unique (santé, secrets,
  résilience, planification, traçabilité).
- **Coût accepté.** Chaque connecteur exige deux adaptateurs et un ACL, soit
  davantage de code qu'un appel direct. C'est le prix du découplage, déjà payé
  et validé sur l'intégration IA.
- **Limite connue.** La chasse restera de forme « alerte » : `HuntField` est un
  enum fermé de 8 champs. Chasser sur des logs bruts arbitraires demanderait de
  l'étendre — décision métier distincte, hors périmètre.
- **Périmètre nouveau.** La gestion des vulnérabilités est un module métier
  complet (le concept n'existe nulle part dans le code), rattaché au domaine
  fonctionnel Wazuh.
- **Ce document est figé.** Toute décision découverte pendant l'implémentation
  fera l'objet d'un ADR distinct plutôt que d'une réécriture de la baseline.
  La topologie réseau retenue en phase 0 fera l'objet de l'**ADR-015**.

---

## Amendement v1.1 — évaluation de 8 propositions d'évolutivité

*Date : 2026-08-05. Aucun choix structurant de la v1.0 n'est remis en cause.*
**5 propositions retenues, 3 écartées.** Les refus sont consignés ici avec leur
raison technique, afin qu'ils ne soient pas reproposés sans ce contexte.

### Retenues

**A1 — Un `HealthIndicator` par connecteur + `ConnectorStatusAggregator`**
*(remplace le `ConnectorHealthService` unique)*. Retenu : évite qu'un service
central grossisse à chaque connecteur ajouté, et s'aligne sur le mécanisme natif
de Spring Boot, qui agrège déjà les `HealthIndicator` dans `/actuator/health`.
L'agrégateur ne conserve que la composition **métier** destinée à la console.
Corrige au passage une incohérence de la v1.0, qui annonçait un service unique
en §5.2 et une sonde par connecteur en §8.

**A2 — Un planificateur par connecteur** *(remplace le `ConnectorSyncScheduler`
unique)*. Retenu : chaque source a sa propre fréquence, et ajouter un connecteur
ne doit modifier aucun fichier existant (principe ouvert/fermé). Le comportement
partagé — ouverture d'un `SyncRun`, capture des erreurs, trace de fin — reste
factorisé dans `AbstractSyncTask`.

**A3 — Sous-contexte `actions` isolant les opérations à effet réel.** Retenu, et
renforcé : les ports d'action sont regroupés dans
`application.connectors.actions`, derrière un `SocActionService` qui constitue le
**point de passage unique** (RBAC, audit, plafond, interdiction de retry). Une
**règle ArchUnit** interdit à toute classe extérieure de dépendre d'un port
d'action — l'audit devient structurellement impossible à contourner, dans la
même démarche que l'ADR-002 dont les règles sont vérifiées par machine.

*Nuance de découpage :* la séparation s'applique aux **ports** (dans
`application`), pas aux **adaptateurs** (dans `infrastructure`), qui restent dans
le package de leur outil car ils partagent client Feign, authentification et DTO
avec les adaptateurs de lecture.

**A4 — Troisième état `disabled` par connecteur.** Retenu dans sa forme
économique : le mode d'un connecteur devient `simulation` (défaut) · `live` ·
`disabled`, généralisant le patron de l'ADR-008. Couper un connecteur devient
une variable d'environnement, sans redéploiement d'image ni modification de code.

*Écarté dans sa forme dynamique :* un commutateur à chaud (sans redémarrage)
exigerait un magasin de flags, une interface d'administration et son propre
audit — un module en soi. `simulation` remplit déjà l'office d'un « arrêt sûr »
qui laisse la plateforme utilisable, ce qu'un `disabled` brut ne permet pas.

**A5 — Version et capacités détectées par connecteur.** Retenu, sur une
justification concrète issue de l'audit : **l'origine des données de
vulnérabilité Wazuh dépend de la version déployée**. Un `ConnectorDescriptor`
(version réelle + capacités déduites) permet à l'adaptateur de choisir le bon
chemin, et à la console de déclarer honnêtement une capacité indisponible plutôt
que d'échouer sans diagnostic. Ce n'est donc pas de la généralisation
spéculative, mais la réponse à un cas déjà identifié.

### Écartées

**R1 — WebClient pour VirusTotal à la place de Feign.** Écarté.

- La plateforme est un socle **servlet pur** : `spring-boot-starter-web`, sans
  aucune dépendance réactive. Introduire `WebClient` impose
  `spring-boot-starter-webflux`, donc toute la pile Reactor Netty, pour un seul
  connecteur. Cela contredit directement une décision déjà prise et vérifiée sur
  ce projet : lors de la PR #44, deux bibliothèques transitives avaient été
  **exclues** au motif de réduire la surface d'attaque plutôt que de la patcher.
- Le *backpressure* invoqué n'a pas d'objet ici : l'enrichissement VirusTotal est
  une requête unitaire déclenchée par un clic d'analyste, jamais un flux continu.
- La limitation de débit ne dépend pas du client HTTP : `@RateLimiter` de
  Resilience4j — **déjà présent dans le projet** — s'applique à l'adaptateur, qui
  est un bean Spring. Timeouts et retries sont pareillement disponibles des deux
  côtés.

*Décision : Feign + `@RateLimiter` Resilience4j.* Si un client non-Feign devenait
un jour nécessaire, le candidat serait `RestClient` (Spring Framework 6.1,
disponible sur Spring Boot 3.5.16), qui offre une API moderne **sans** tirer la
pile réactive.

**R2 — `VaultPort` pour préparer Azure Key Vault / HashiCorp Vault.** Écarté,
car l'abstraction existe déjà et elle est fournie par le framework.

Les secrets sont lus via `Environment` et `@ConfigurationProperties`. Azure Key
Vault comme HashiCorp Vault s'intègrent à Spring en tant que `PropertySource` :
la migration consiste à ajouter une dépendance et une configuration, **sans
modifier une seule ligne de code applicatif**. Introduire un `VaultPort`
remplacerait une abstraction éprouvée et gratuite par une abstraction maison à
maintenir — un recul net.

*L'objectif visé est donc déjà atteint*, et la règle « aucun secret en base ni en
Git, environnement uniquement » (§7.2) reste la garantie qui compte.

**R3 — `ConnectorMetricsPort` pour découpler de Micrometer.** Écarté, pour la
même raison : **Micrometer *est* la façade neutre**, l'équivalent de SLF4J pour
les métriques. Prometheus, OpenTelemetry et Datadog n'en sont que des registres ;
en changer revient à remplacer une dépendance, sans toucher au code. Un
`ConnectorMetricsPort` serait une façade au-dessus d'une façade.

*Le souci sous-jacent reste légitime et il est traité :* la contrainte réelle est
qu'ArchUnit interdit toute dépendance framework dans le domaine. Les métriques
sont donc émises depuis `infrastructure` et `application`, où Spring est déjà
présent — jamais depuis `domain`. La règle existante suffit à garantir
l'isolation recherchée.
