# Diagrammes de séquence — SmartSOC Enterprise

- **Date** : 2026-08-05
- **Convention** : ✅ flux implémenté et vérifié en réel · 🔨 flux prévu par la
  [baseline d'intégration](soc-integration-plan.md)

---

## 1 · Ingestion d'une alerte Wazuh ✅

Le seul flux d'intégration déjà opérationnel. Il illustre trois règles
structurantes : idempotence, IA hors chemin critique, temps réel par événement.

```mermaid
sequenceDiagram
    autonumber
    participant WZ as 🐺 Wazuh Manager
    participant CTRL as IngestController
    participant SVC as AlertIngestionService
    participant DB as 🐘 PostgreSQL
    participant EV as ApplicationEventPublisher
    participant AI as AlertClassificationService
    participant CLS as 🧠 SocAI :8010
    participant WS as STOMP
    participant UI as ⚛️ Console

    WZ->>CTRL: POST /api/v1/ingest/alerts<br/>X-API-Key
    CTRL->>CTRL: filtre clé d'API<br/>comparaison temps constant
    CTRL->>SVC: ingest(commande)
    SVC->>DB: recherche (source, externalId)

    alt Alerte déjà connue — rejeu
        DB-->>SVC: alerte existante
        SVC-->>WZ: 200 OK · même id<br/>AUCUN événement publié
    else Nouvelle alerte
        SVC->>DB: INSERT
        SVC->>EV: publish(AlertIngested)
        SVC-->>WZ: 201 Created
        Note over SVC,WZ: Wazuh est libéré ICI —<br/>l'IA n'est pas sur le chemin critique

        par Diffusion temps réel
            EV->>WS: AlertRealtimePublisher
            WS->>UI: /topic/alerts
            UI->>UI: invalidation du cache React Query
        and Classification IA (@Async)
            EV->>AI: AlertClassified listener
            AI->>CLS: POST /api/v1/classifications
            alt Service IA disponible
                CLS-->>AI: score · verdict · zone · justifications
                AI->>DB: applyAiAssessment + applyAiEnrichment
                AI->>EV: publish(AlertClassified)
                EV->>WS: /topic/alerts/updates
                WS->>UI: alerte enrichie
            else Service IA injoignable
                CLS--xAI: timeout / circuit ouvert
                AI->>AI: journalise, n'échoue pas
                Note over AI: L'alerte reste triable sans score.<br/>Reclassification manuelle possible.
            end
        end
    end
```

**Points vérifiés en conditions réelles :** le rejeu ne crée pas de doublon et ne
republie rien ; le webhook répond avant la classification ; un service IA éteint
laisse l'alerte pleinement exploitable.

---

## 2 · Synchronisation des agents Wazuh 🔨

Premier flux sortant. Il fonde le socle réutilisé par tous les connecteurs.

```mermaid
sequenceDiagram
    autonumber
    participant SCH as WazuhAgentSyncScheduler
    participant SVC as WazuhAgentSyncService
    participant PORT as AgentInventoryPort
    participant ADP as LiveAgentInventoryAdapter
    participant CB as ⚡ CircuitBreaker
    participant WZ as 🐺 Wazuh API :55000
    participant ACL as WazuhAgentMapper
    participant DB as 🐘 PostgreSQL

    SCH->>SVC: déclenchement périodique (~5 min)
    SVC->>DB: ouvre un SyncRun (début)
    SVC->>PORT: listAgents()
    PORT->>ADP: (mode live)
    ADP->>CB: appel protégé

    alt Circuit fermé — nominal
        CB->>WZ: GET /agents (JWT Wazuh)
        WZ-->>CB: agents (modèle Wazuh)
        CB-->>ADP: réponse
        ADP->>ACL: traduction
        Note over ACL: SEUL point du système<br/>connaissant les deux modèles
        ACL-->>ADP: liste d'agents, modèle plateforme
        ADP-->>SVC: agents

        loop pour chaque agent
            SVC->>DB: recherche par identifiant externe
            alt Actif inconnu
                SVC->>DB: création
            else Actif connu et modifié
                SVC->>DB: mise à jour
                SVC->>SVC: publish(AssetUpdated)
            else Actif inchangé
                SVC->>SVC: ignore — aucun événement
            end
        end
        SVC->>DB: clôt le SyncRun (traités, rejetés, durée)

    else Circuit ouvert ou timeout
        CB--xADP: échec immédiat
        ADP-->>SVC: SocConnectorException
        SVC->>DB: SyncRun en échec + motif
        SVC->>SVC: publish(ConnectorStatusChanged)
        Note over SVC,DB: Les actifs restent consultables.<br/>La console affiche la date<br/>de dernière synchronisation réussie.
    end
```

**Règle clé :** la console ne lit jamais Wazuh. Elle lit PostgreSQL, alimenté par
cette synchronisation — un outil éteint dégrade la fraîcheur, jamais la
disponibilité.

---

## 3 · Synchronisation MISP 🔨

Même socle, avec la tolérance par élément déjà en place côté CTI.

```mermaid
sequenceDiagram
    autonumber
    participant SCH as MispSyncScheduler
    participant SVC as MispIndicatorSyncService
    participant PORT as ThreatIntelPort
    participant ADP as LiveThreatIntelAdapter
    participant MI as 🧬 MISP
    participant ACL as MispAttributeMapper
    participant ING as IndicatorFeedIngestionService
    participant DB as 🐘 PostgreSQL

    SCH->>SVC: déclenchement périodique (~30 min)
    SVC->>DB: ouvre un SyncRun
    SVC->>PORT: fetchIndicators(depuis dernière sync)
    PORT->>ADP: (mode live)
    ADP->>MI: GET /attributes/restSearch
    MI-->>ADP: attributs MISP
    ADP->>ACL: traduction
    Note over ACL: type MISP → IndicatorType<br/>tags → TlpMarking<br/>uuid → externalId<br/>« misp » → feedSource
    ACL-->>SVC: observations normalisées

    SVC->>ING: ingest(lot)
    Note over ING: Service EXISTANT, réutilisé tel quel

    loop pour chaque observation
        ING->>DB: transaction INDÉPENDANTE
        alt Observation valide
            DB-->>ING: indicateur créé ou mis à jour
        else Observation rejetée
            ING->>ING: consigne le motif, poursuit
            Note over ING: Une entrée malformée ne prive pas<br/>le SOC de tout le lot
        end
    end

    ING-->>SVC: résultat (acceptés, rejetés)
    SVC->>DB: clôt le SyncRun
    SVC->>SVC: publish(ConnectorSyncCompleted)
```

**Aucune modification du domaine Intelligence** : `feedSource` et `externalId`
existent déjà sur `Indicator`, et `IndicatorFeedIngestionService` est réutilisé
sans changement. Le connecteur ne fait qu'alimenter un modèle inchangé.

---

## 4 · Enrichissement VirusTotal 🔨

Le seul flux qui sort du réseau privé, et le seul soumis à un quota strict.

```mermaid
sequenceDiagram
    autonumber
    participant UI as ⚛️ Analyste
    participant CTRL as IocController
    participant SVC as ReputationService
    participant CACHE as CachedReputationAdapter
    participant RL as 🚦 RateLimiter
    participant ADP as LiveReputationAdapter
    participant VT as 🌐 VirusTotal · Internet

    UI->>CTRL: clic « Vérifier la réputation »
    CTRL->>CTRL: RBAC — ANALYST minimum
    CTRL->>SVC: reputationOf(observable)

    alt Type EMAIL
        SVC-->>UI: capacité non supportée par VirusTotal
        Note over SVC,UI: Le bouton est masqué en amont —<br/>déclaré, jamais échoué silencieusement
    else Type supporté (IP, domaine, URL, empreinte)
        SVC->>CACHE: lookup(observable)
        alt Réputation en cache et fraîche
            CACHE-->>SVC: réputation mise en cache
            Note over CACHE: Aucun appel réseau —<br/>protège le quota (4 req/min)
        else Cache absent ou périmé
            CACHE->>RL: demande d'autorisation
            alt Quota disponible
                RL->>ADP: autorisé
                ADP->>VT: GET /api/v3/... (clé VT)
                alt Réponse obtenue
                    VT-->>ADP: réputation
                    ADP->>CACHE: mise en cache
                    CACHE-->>SVC: réputation
                else Quota VirusTotal épuisé (429)
                    VT--xADP: 429
                    ADP-->>SVC: dégradation explicite
                end
            else Débit local dépassé
                RL--xCACHE: refus immédiat
                CACHE-->>SVC: « limite de débit atteinte, réessayez »
                Note over RL: On refuse AVANT d'appeler,<br/>pour ne jamais faire bloquer le compte
            end
        end
        SVC-->>UI: réputation ou motif de dégradation
    end
```

---

## 5 · Threat Hunting sur OpenSearch 🔨

La meilleure démonstration de l'architecture hexagonale : **seul l'adaptateur
change**, le port, le service, l'API et l'écran restent intacts.

```mermaid
sequenceDiagram
    autonumber
    participant UI as ⚛️ Écran Hunting (existant)
    participant CTRL as HuntController (existant)
    participant SVC as HuntExecutionService (existant)
    participant PORT as HuntExecutionPort (existant)
    participant SIM as SimulatedHuntExecutionAdapter ✅
    participant LIVE as OpenSearchHuntAdapter 🔨
    participant QB as QueryBuilder 🔨
    participant OS as 🔎 OpenSearch :9200
    participant DB as 🐘 PostgreSQL

    UI->>CTRL: POST /api/v1/hunts/{id}/execute
    CTRL->>CTRL: RBAC — ANALYST minimum
    CTRL->>SVC: execute(huntId, page)
    SVC->>PORT: execute(HuntGroup, PageQuery)
    Note over SVC,PORT: Ces trois couches ne changent PAS<br/>entre les deux modes

    alt Mode simulation (défaut)
        PORT->>SIM: traduction en Specification JPA
        SIM->>DB: requête sur les alertes locales
        DB-->>SIM: page de résultats
        SIM-->>PORT: HuntExecutionResult
    else Mode live
        PORT->>LIVE: (adaptateur OpenSearch)
        LIVE->>QB: projette HuntGroup → DSL
        Note over QB: 8 HuntField × 3 opérateurs<br/>Périmètre BORNÉ et testable<br/>exhaustivement
        QB-->>LIVE: requête DSL
        LIVE->>OS: POST /_search
        alt OpenSearch répond
            OS-->>LIVE: documents
            LIVE->>LIVE: ACL — document d'index → modèle d'alerte
            LIVE-->>PORT: HuntExecutionResult (MÊME type)
        else OpenSearch injoignable
            OS--xLIVE: timeout / circuit ouvert
            LIVE-->>PORT: SocConnectorException
            PORT-->>UI: « Chasse indisponible — OpenSearch injoignable depuis HH:MM »
            Note over UI: Message NOMMÉ,<br/>jamais un résultat vide trompeur
        end
    end

    PORT-->>SVC: résultat
    SVC-->>CTRL: page
    CTRL-->>UI: JSON identique dans les deux modes
```

---

## 6 · Déclenchement d'un playbook Shuffle 🔨 ⚠

Le flux le plus encadré du système : effet réel sur des machines de production.

```mermaid
sequenceDiagram
    autonumber
    participant UI as ⚛️ Analyste
    participant CTRL as PlaybookController
    participant ACT as SocActionService
    participant AUD as AuditRecorder ✅
    participant PORT as WorkflowTriggerPort
    participant ADP as ShuffleTriggerAdapter
    participant SH as ⚙️ Shuffle
    participant ING as IngestController ✅
    participant DB as 🐘 PostgreSQL

    UI->>UI: confirmation explicite NOMMANT la cible
    UI->>CTRL: POST .../execute
    CTRL->>CTRL: RBAC — ANALYST minimum
    CTRL->>ACT: execute(ActionRequest{acteur, cible, motif})

    Note over ACT: POINT DE PASSAGE UNIQUE —<br/>une règle ArchUnit interdit<br/>d'appeler le port directement

    ACT->>ACT: vérifie le plafond horaire
    alt Plafond dépassé
        ACT-->>UI: 429 — refus explicite
        ACT->>AUD: trace la tentative refusée
    else Autorisé
        ACT->>AUD: PLAYBOOK_TRIGGERED<br/>acteur · IP · cible · motif
        Note over AUD: REQUIRES_NEW —<br/>la trace survit même si<br/>la suite échoue
        ACT->>DB: exécution en statut « démarrage »
        ACT->>PORT: trigger(workflow, contexte)
        PORT->>ADP: (mode live)
        ADP->>SH: POST /api/v1/workflows/{id}/execute

        alt Shuffle accepte
            SH-->>ADP: identifiant d'exécution
            ADP-->>ACT: accepté
            ACT->>DB: statut « en cours » + id externe
            ACT->>ACT: publish(PlaybookStarted)
            ACT-->>UI: exécution démarrée
        else Shuffle refuse ou ne répond pas
            SH--xADP: erreur / timeout
            Note over ADP: AUCUN RETRY —<br/>rejouer, c'est agir deux fois<br/>dans le monde réel
            ADP-->>ACT: échec
            ACT->>DB: statut « échec de démarrage »
            ACT->>AUD: trace l'échec
            ACT-->>UI: message explicite
        end
    end

    Note over SH: … exécution asynchrone côté Shuffle …

    SH->>ING: POST /ingest/... (X-API-Key dédiée)
    ING->>DB: résultat rattaché à l'exécution
    ING->>ING: publish(PlaybookFinished)
    ING->>AUD: PLAYBOOK_COMPLETED
    ING-->>UI: mise à jour temps réel (STOMP)
```

**Six garde-fous visibles dans ce diagramme :** confirmation nommant la cible ·
RBAC · point de passage unique vérifié par ArchUnit · plafond horaire · audit
avant l'action (et non après) · aucun retry.
