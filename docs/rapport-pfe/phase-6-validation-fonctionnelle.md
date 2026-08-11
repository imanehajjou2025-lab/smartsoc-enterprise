# Phase 6 — Validation fonctionnelle (volet « Fonctionnel »)

> Campagne manuelle scénarisée, conforme à `docs/architecture/soc-integration-plan.md` §4
> (phase 6) : chaque module parcouru avec des **données réelles**, contre le backend
> Docker reconstruit servant à la fois `http://localhost:8080` (build de production
> intégré) et vérifié séparément via `http://localhost:5173` (dev Vite). Aucune donnée
> fabriquée pour combler un écran vide — un module sans donnée réelle est noté comme tel.
>
> Démarré le 2026-08-11.

---

## 1. Dashboard

**Parcouru.** `GET /dashboard` (build de production, port 8080). Toutes les tuiles
affichent des données réelles et cohérentes avec le reste de la session : 427 alertes
totales, 105 CRITICAL, 4 incidents ouverts, couverture MITRE 29 % (7/24 techniques),
1005 indicateurs CTI actifs, « 2 playbooks actifs » (reflète bien les deux playbooks
réels déclarés pendant la phase 5), posture des actifs (5 actifs, MEDIUM). Aucune
erreur console. **Aucun écart.**

---

## 2. Alertes

**Parcouru.** `GET /alerts` (port 8080). Liste réelle : 427 alertes au total, pagination
25/page fonctionnelle, filtres Statut/Sévérité présents (combobox MUI). Lignes réelles
provenant du connecteur Wazuh (ex. « Executable file dropped in folder commonly used by
malware », source `wazuh`, actif `WIN10-CLIENT`, score IA 6 %).

Clic sur une ligne → ouverture du tiroir de détail (`AlertDetailDrawer`, rendu en portail
React hors de `<main>` — vérifié via `document.querySelectorAll('.MuiDrawer-paper')`
plutôt que `get_page_text`, qui ne capture que le contenu de `<main>`). Contenu du
tiroir, entièrement réel :
- Sévérité, statut, titre, source/ID externe (`wazuh · 1786378224.88893269`).
- Horodatage détection/réception réels (10/08/2026 18:10:24 → reçue 18:15:49).
- Actif concerné (`WIN10-CLIENT`), règle de détection Wazuh (`92213`).
- Technique MITRE ATT&CK mappée réellement (`T1105` — Ingress Tool Transfer).
- Score IA (classifieur TP/FP externe) avec zone d'enrichissement détaillée et réelle :
  `FINAL TRIAGE SCORE: 0.06 (5.9%)`, décomposé en 5 signaux réels (XGBoost Baseline,
  IOC Reputation, Historical Entity, MITRE Pattern, Novelty Graph) — pas de placeholder.
- Événement brut (évidence) : JSON Wazuh/Sysmon réel et complet (EventID 11, agent
  `004`/`10.100.0.9`, manager `vm-siem`, description de règle, groupes de décodeurs).
- Actions disponibles visibles (non déclenchées pour ne pas polluer les données de la
  campagne) : « Escalader en incident », « Demander à l'assistant », « Prise en
  charge », « Faux positif ».

**Aucun écart.**

---

## 3. Incidents

**Parcouru.** `GET /incidents` (port 8080). 4 incidents réels : `INC-2026-0004`
(CRITICAL, Ouvert, ouvert depuis une alerte Wazuh réelle), `INC-2026-0003` (CRITICAL,
brute force SSH), `INC-2026-0002` (MEDIUM, scan Nmap, statut Investigation),
`INC-2026-0001` (HIGH, compromission srv-web-01).

Tiroir de détail ouvert sur `INC-2026-0004` : contenu entièrement réel et cohérent avec
la campagne des phases précédentes — alerte liée (wazuh / `1786291231.30350102`),
**historique réel des 4 exécutions de playbook** de la phase 5 (« Confinement ransomware
verif E2E » : 3 Terminées + 1 Annulée), timeline avec horodatage et acteur réels
(`admin · 10/08/2026 01:58`). Actions présentes : « Ouvrir un cas », « Exécuter un
playbook », **« Déclencher via Shuffle »** (confirme l'intégration phase 5 visible dans
ce module), « Demander à l'assistant », changement de statut (Investigation/Clôturé),
affectation. Actions non déclenchées pour ne pas polluer les données de la campagne.

**Aucun écart.**

---

## 4. Investigations (cas)

**Parcouru.** `GET /investigations` (port 8080). 5 cas réels : `CASE-2026-0005`
(CRITICAL, En cours, ouvert depuis l'incident réel `INC-2026-0004`), `CASE-2026-0004`
(scan Nmap), `CASE-2026-0003` (brute force SSH), `CASE-2026-0002`/`CASE-2026-0001`
(campagne de phishing E2E, Clôturés — traces réelles des campagnes de vérification
antérieures de ce même projet).

Tiroir de détail sur `CASE-2026-0005` : lien réel vers l'incident source (`INC-2026-0004
— Executable file dropped...`), gestion de tâches (0/0, formulaire d'ajout présent),
timeline avec événement `CREATED · admin · 10/08/2026`, affectation, changement de
statut / clôture. Fonctionne en tiroir (portail React), cohérent avec Alertes/Incidents.

**Aucun écart.**

---

## 5. Actifs (+ actions Wazuh)

**Parcouru.** `GET /assets` (port 8080). 7 actifs réels, inventoriés automatiquement via
le connecteur Wazuh (ex. `post-1` CRITICAL/Décommissionné, `srv-web-01`
MEDIUM/Décommissionné, `ubuntu-sensor` Connecté, `win10-client` Déconnecté,
`windows-endpoin(t)` variantes Connecté/Jamais connecté).

Tiroir de détail sur `win10-client` : données réelles issues de Wazuh — IP `10.100.0.9`,
OS (`Microsoft Windows 10 Home 22H2, build 19045.6466`), matériel (12th Gen Intel Core
i5-12450H, 1 cœur, 2.0 Go RAM), description « Découvert automatiquement via le connecteur
Wazuh (id agent 004) », dernier contact réel (10/08/2026 21:33), **97 alertes
corrélées**. Boutons d'actions réelles de la phase 5 bien présents et rattachés à
l'actif : **« Redémarrer l'agent »** et **« Bloquer une IP »** (non déclenchés ici — déjà
vérifiés en conditions réelles pendant la phase 5).

**Aucun écart.**

---

## 6. Threat Intelligence

**Parcouru.** `GET /intelligence` (port 8080). **1 009 indicateurs réels**, synchronisés
depuis le connecteur MISP live (types IPv4/Domaine/URL/SHA-256/SHA-1, statuts
Actif/Révoqué/Expiré, TLP AMBER/GREEN/CLEAR/RED, confiance 50-95). Filtres (type,
statut, source, tag, confiance min.) présents.

Tiroir de détail sur `203.0.113.42` : source `misp`, identifiant externe réel
(`sim-misp-ioc-1` — nom d'événement MISP côté source, pas une donnée fabriquée côté
plateforme), première/dernière observation réelles, bouton **« Vérifier la réputation »**
(connecteur VirusTotal, à la demande), section « Alertes citant cet indicateur ».

Vérification croisée via **Paramètres → Connecteurs** : les 5 connecteurs (Wazuh,
OpenSearch, MISP, VirusTotal, Shuffle) sont tous **« Connecté »** en mode live, avec
horodatage de dernière synchronisation réel (ex. MISP : 11/08/2026 00:31:32, « Succès ·
1000 traités · 0 rejetés » ; OpenSearch : « Partiel · 659 traités · 1335 rejetés »).
Confirme que l'intégration ADR-014 est bien active de bout en bout, pas seulement pour
Threat Intelligence.

**Aucun écart.**

---

## 7. MITRE ATT&CK

**Parcouru.** `GET /mitre` (port 8080). Matrice ATT&CK réelle et cohérente avec le
Dashboard : 7/24 techniques observées (29 % de couverture), 7/14 tactiques touchées,
425 alertes corrélées. Répartition par tactique réelle (ex. Credential Access : `T1003`
OS Credential Dumping (2), `T1110` Brute Force (308) ; Privilege Escalation : 4
techniques dont `T1078` Valid Accounts (6)). Classement « Techniques les plus citées »
cohérent (`T1110` en tête avec 308 alertes).

Drill-down sur `T1110` (Brute Force) : description ATT&CK officielle, tactique
`credential-access`, version ATT&CK 16.1, lien vers attack.mitre.org, et liste réelle des
308 alertes citant la technique (« Multiple Windows Logon Failures », horodatées
individuellement).

**Aucun écart.**

---

## 8. Threat Hunting

**Parcouru.** `GET /hunting` (port 8080). Aucune chasse sauvegardée au départ (état vide
honnête, pas de donnée fabriquée). Constructeur de requête structurée fonctionnel
(Champ/Opérateur/Valeur, ex. `Sévérité = CRITICAL`).

**Chasse exécutée réellement** contre le connecteur OpenSearch Indexer live (phase 4) :
`Sévérité = CRITICAL` → **162 correspondances en 395 ms**, résultats réels (source
`wazuh`, hôte `WIN10-CLIENT`, horodatage réel).

**Écart mineur noté (à creuser en phase Résilience, hors périmètre « Fonctionnel »)** :
le nombre de correspondances CRITICAL via Hunting (162) ne correspond pas exactement au
compteur « 105 CRITICAL » du Dashboard — écart probablement dû à une différence de
fenêtre temporelle ou de dédoublonnage entre l'index OpenSearch brut et la vue Alertes
agrégée, pas à une donnée fabriquée (les deux sources restent des données réelles). À
vérifier plus précisément lors du volet Résilience/Charge de la phase 6. Par ailleurs, la
carte connecteur OpenSearch (Paramètres) affiche un dernier cycle **« Partiel · 659
traités · 1335 rejetés »** — rejets à investiguer également à ce moment-là.

**Aucun autre écart.**

---

## 9. Playbooks SOAR (+ Shuffle)

**Parcouru.** `GET /soar` (port 8080). 2 playbooks réels actifs, cohérents avec le
Dashboard (« 2 playbooks actifs ») : « Confinement ransomware » (v1, 2 étapes) et
« Confinement ransomware verif E2E » (v2, 1 étape, lié à Shuffle).

Dialogue « Modifier » ouvert sur le second : valeurs réelles chargées, y compris les
identifiants Shuffle capturés lors de la phase 5 — `shuffleWorkflowId =
fb0e09e3-402f-4d20-9bc1-f7fa845d4314`, `shuffleWebhookPath =
webhook_a0fa6c78-fa6c-41a1-ac56-3c7f514ba8f4` (exploration annulée sans sauvegarde).
L'historique des 4 exécutions réelles de ce playbook a déjà été confirmé via le tiroir
Incidents (module 3) : 3 Terminées + 1 Annulée, déclenchement Shuffle live vérifié
end-to-end pendant la phase 5.

**Aucun écart.**

---

## 10. Rapports

**Parcouru.** `GET /reports` (port 8080). 2 rapports hebdomadaires réels déjà générés
(31/07/2026, 26/07/2026, par `admin`). Boutons Export CSV/PDF présents — **non
déclenchés** (téléchargement de fichier, hors périmètre d'une vérification automatique
sans confirmation explicite).

Tiroir de détail sur « Rapport hebdomadaire 31/07/2026 » : instantané figé réel et
cohérent — 9 alertes (période 24/07→31/07), 0 incident ouvert/clôturé sur la période,
SOAR (2 démarrées, 1 terminée, 1 annulée), Threat Hunting (0 requête exécutée avec des
notes méthodologiques honnêtes sur la limite ADR-011 — pas d'historique d'exécution
persisté), couverture MITRE en instantané cumulatif (10 techniques, ex. `T1110` 8
alertes, `T1078` 6 alertes).

**Aucun écart.**

---

## 11. Assistant IA

**Parcouru.** `GET /assistant` (port 8080). Interface de chat réelle avec suggestions de
prompts prédéfinies, zone de saisie fonctionnelle.

Question réelle envoyée : « Combien d'incidents CRITICAL sont actuellement ouverts sur
la plateforme ? ». Appel LLM réel confirmé (état « L'assistant rédige une réponse… »
pendant ~15 s avant la réponse finale — pas instantané/simulé). Réponse reçue : *« Je
peux vous aider à analyser les détails réels des incidents CRITICAL sur votre plateforme
SmartSOC. Pourriez-vous me fournir le numéro de l'incident que vous voulez examiner ? »*

**Observation (comportement réel, pas un bug bloquant)** : l'assistant ne répond pas
directement à une question agrégée (« combien de… ») et redirige vers une consultation
par identifiant d'incident précis — cohérent avec un outillage LLM axé sur le lookup
ciblé (par ID) plutôt que sur l'agrégation libre. Comportement à documenter comme limite
connue plutôt qu'anomalie, la réponse reste honnête (elle ne invente pas de chiffre).

**Aucun écart bloquant.**

---

## 12. Utilisateurs & rôles

**Parcouru.** `GET /admin/users` (port 8080). 1 utilisateur réel (`admin`,
`admin@smartsoc.local`, rôle Administrateur, Actif) — cohérent avec un déploiement
mono-analyste réel, pas une donnée manquante. Actions « Nouvel utilisateur »,
« Modifier », « Supprimer » présentes (non exercées pour éviter de créer/supprimer un
compte réel sur l'unique utilisateur de la plateforme).

Dialogue « Modifier admin » ouvert : garde-fou réel confirmé — **« Vous ne pouvez pas
modifier votre propre rôle »** (le champ Rôle est verrouillé sur Administrateur).
Fermé sans sauvegarde.

**Aucun écart.**

---

## 13. Paramètres — sécurité / IA / notifications

**Parcouru.** `GET /settings` (port 8080), sous-sections suivantes :

- **Authentification & sécurité** : durées JWT réelles non modifiables depuis la
  console par design (ADR-005) — jeton d'accès 15 min, rafraîchissement 7 j. Présence
  (jamais la valeur) des secrets confirmée : webhook d'ingestion SOC « Configuré »,
  outils assistant IA « Configuré ».
- **Intelligence artificielle** : mode **« Réel (live) »** confirmé (ADR-008) — les deux
  modules IA développés séparément par l'équipe SOC/IA sont bien branchés en direct :
  Classifieur TP/FP (`http://host.docker.internal:8010`, Disponible, Clé configurée) et
  Assistant conversationnel (`http://host.docker.internal:8001`, Disponible, Clé
  configurée). Cohérent avec le score IA vu sur les alertes (module 2) et les réponses de
  l'Assistant IA (module 11).
- **Notifications** : mode **Simulation** honnête — SMTP « Non configuré », aucun e-mail
  réel envoyé, bouton « Tester l'envoi » désactivé tant que le mode live n'est pas
  configuré (ADR-005). Comportement attendu pour ce déploiement de laboratoire.

**Aucun écart.**

---

## 14. Paramètres — opérations

**Parcouru.** `GET /settings` (port 8080 puis 5173), sous-sections Journal d'audit /
Sauvegarde & restauration / Santé des services / Maintenance / À propos.

### Écart réel trouvé et corrigé : crash du Journal d'audit

**Symptôme.** La section « Journal d'audit » plantait entièrement (React Router
`ErrorBoundary`, page blanche) : `TypeError: Cannot read properties of undefined
(reading 'main')`.

**Cause racine.** Le type frontend `AuditAction` (`settingsApi.ts`) et les tables de
libellés/couleurs `AUDIT_ACTION_LABELS` / `AUDIT_ACTION_COLORS`
(`settingsChips.tsx`) n'avaient jamais été mises à jour lors de l'ajout des 3 nouvelles
valeurs d'audit de la phase 5 (`WAZUH_AGENT_RESTART_REQUESTED`,
`WAZUH_AGENT_FIREWALL_DROP_REQUESTED`, `SHUFFLE_WORKFLOW_TRIGGER_REQUESTED`, cf.
`AuditAction.java`). `AUDIT_ACTION_COLORS[action]` renvoyait `undefined` pour ces
actions, puis `resolveChipColor` (`chipStyles.ts`) faisait `theme.palette[undefined].main`
→ crash. Comme des actions Wazuh (redémarrage, blocage IP) et Shuffle réelles ont bien
été déclenchées pendant la phase 5, le journal d'audit contenait déjà de telles entrées
— la page était donc **cassée dès la première consultation réelle** de ce module en
phase 6, pas dans un cas limite hypothétique.

**Correction.** Ajout des 3 valeurs manquantes au type `AuditAction`
(`frontend/src/features/settings/settingsApi.ts`), à `AUDIT_ACTION_LABELS`/
`AUDIT_ACTION_COLORS` (`frontend/src/features/settings/settingsChips.tsx`) et à la liste
de filtre `ACTIONS`
(`frontend/src/features/settings/sections/AuditLogSection.tsx`). `Record<AuditAction,
…>` étant maintenant exhaustif, TypeScript empêchera une régression silencieuse
similaire à l'avenir. Test de non-régression ajouté dans `SettingsPage.test.tsx` (rendu
du journal avec une entrée `SHUFFLE_WORKFLOW_TRIGGER_REQUESTED`). Suite de tests
frontend complète : 56 tests, 4 échecs en exécution parallèle dus à des timeouts
d'environnement (confirmés non liés à ce correctif — les 4 fichiers passent
individuellement), `npm run build` réel réussi. Backend reconstruit et redéployé
(`docker compose build backend && up -d backend`), correction **vérifiée en direct** sur
`:8080` (build de production) et `:5173` (dev Vite) : le journal affiche désormais
correctement l'historique réel des actions phase 5 (« Déclenchement workflow Shuffle »,
« Blocage IP demandé », « Redémarrage d'agent demandé », avec raison, IP, `executionId`
réels).

Autres sous-sections vérifiées, aucun écart :
- **Sauvegarde & restauration** : export `pg_dump` réel tracé en audit, restauration
  volontairement absente de la console (poste DevSecOps). Téléchargement non déclenché
  (action nécessitant confirmation explicite hors périmètre d'une vérification
  automatique).
- **Santé des services** : Backend + BDD « Opérationnelle », rafraîchi toutes les 30 s ;
  Classifieur TP/FP et Assistant conversationnel « Disponible / Mode live ».
- **Maintenance** : section honnêtement vide (« Aucun mode maintenance ni purge de cache
  n'existe encore côté plateforme... À venir ») — pas de fonctionnalité fictive.
- **À propos** : version réelle `0.1.0-SNAPSHOT`, Java 21.0.11, horodatage de démarrage
  cohérent avec le redéploiement qui vient d'avoir lieu.

---

## 15. Écart complémentaire trouvé et corrigé : `CapabilityProbe` jamais implémenté

En revoyant la carte Connecteurs (Paramètres, hors périmètre strict de la campagne
module-par-module mais soulevé par un contrôle visuel de l'utilisateur), « Version
détectée » et « Capacités » affichaient systématiquement « Non détectée » / « Aucune
capacité confirmée » pour les **5** connecteurs, sans exception. Investigation :
- `ConnectorDescriptor` (domaine) et toute la vitrine console existent depuis l'origine
  (ADR-014 §6.5), mais le composant **`CapabilityProbe`** documenté dans
  `docs/architecture/CONNECTORS-REFERENCE.md` §1/§2 (« détecte la version réelle et en
  déduit les capacités ») n'avait **jamais été écrit** — chaque adaptateur repassait
  simplement son propre descripteur `unknown()` à chaque succès.
- Fonctionnalité documentée mais jamais construite, pas un bug de câblage.

**Implémenté pour 4 des 5 connecteurs**, avec des échantillons réels capturés en amont
(`docs/integration/fixtures/{wazuh,misp,shuffle}/*.json`) :
- **Wazuh** : `GET /` → `api_version` réel (`4.12.0`). Capacités confirmées :
  Inventaire d'agents, Inventaire système, Statistiques du gestionnaire ; Contrôle
  d'agent annoncé uniquement si `wazuh.actions.mode=live` (indépendant du mode lecture —
  actuellement en simulation sur ce déploiement, donc honnêtement absent).
- **MISP** : `GET /servers/getVersion` → `version` réel (`2.5.44`) + capacité Threat
  Intelligence.
- **Shuffle** : cette instance self-hosted n'expose **aucun** endpoint de version
  (`/api/v1/version` → 404, vérifié en réel) — `GET /api/v1/environments` sert de signal
  le plus proche (type d'environnement auto-déclaré, ex. `Shuffle (onprem/docker)`),
  explicitement documenté comme tel dans le code, jamais une valeur inventée. Capacités :
  Déclenchement de workflow, Statut de workflow.
- **VirusTotal** : aucune sonde réseau possible — service SaaS sans notion de version
  (décision utilisateur confirmée). Le descripteur le dit explicitement
  (`« Service cloud — pas de version applicable »`) plutôt que de laisser un champ vide
  muet. Capacité : Réputation d'observables.
- **OpenSearch** : reporté — le compte de service dédié n'a que des droits de lecture
  sur les index (`403` sur `/`, `_cluster/health`, `_nodes/http`). Nécessite l'ajout du
  rôle `cluster:monitor/main` côté SOC avant de pouvoir sonder une version réelle ; à
  faire par l'utilisateur (infra SOC), pas par la plateforme.

Chaque sonde respecte une doctrine commune : TTL d'1 h pour éviter de sonder à chaque
cycle, repli silencieux sur le descripteur précédent en cas d'échec (jamais de valeur
supposée), et le même triptyque Live/Simulation(marquée « Simulation »)/Désactivé que le
reste du socle connecteurs (ADR-014 §1).

**Vérifié réellement** : suite backend complète (tests unitaires + intégration WireMock
sur échantillons réels), backend reconstruit et redéployé, confirmé en direct sur
`http://localhost:8080/settings?section=connectors` — Wazuh (`4.12.0`) et MISP
(`2.5.44`) affichent désormais une version et des capacités réelles au premier cycle
suivant le redéploiement.

**Complément du 2026-08-11 — Shuffle, VirusTotal et OpenSearch, les 5/5.**
- **Shuffle et VirusTotal** confirmés en conditions réelles à la demande d'Imane : un
  vrai déclenchement de workflow Shuffle (`INC-2026-0004`, playbook « Confinement
  ransomware verif E2E ») → carte mise à jour en `Shuffle (onprem/docker)` avec les
  capacités Déclenchement/Statut de workflow ; une vraie vérification de réputation
  VirusTotal (IOC `203.0.113.42`) → carte mise à jour en
  `Service cloud — pas de version applicable` avec la capacité Réputation
  d'observables.
- **OpenSearch** débloqué : Imane a élargi les droits du compte `smartsoc-reader` côté
  OpenSearch Security (rôle réservé `readall` immuable — création d'un rôle
  personnalisé `smartsoc_cluster_monitor` avec la permission `cluster:monitor/main`,
  mappé sur le backend role `readall`, via l'API `_plugins/_security` en s'authentifiant
  avec le compte `admin` de l'Indexer, trouvé dans `wazuh-install-files.tar` sur la VM
  `vm-siem`). `GET /` accessible immédiatement après. Sonde implémentée à l'identique
  des 4 autres (`GET /`, TTL 1 h, repli silencieux) : version réelle détectée `7.10.2`
  (cluster `wazuh-cluster`), capacités Recherche d'événements + Flux de vulnérabilités.
  Confirmé en direct après reconstruction/redéploiement du backend.

**Les 5 connecteurs affichent désormais une version et des capacités réelles.**
`CapabilityProbe` (ADR-014 §6.5) est intégralement implémenté.

---

