# Journal de bord — SmartSOC Enterprise

> Une entrée par jalon significatif, rédigée au moment des faits.
> Chaque affirmation est traçable : PR, commit ou ADR référencé.

---

## 2026-07-10 — J1 : Initialisation du dépôt et gouvernance (PR #1, #2)

**Réalisé.** Dépôt GitHub public `smartsoc-enterprise` créé et lié au dossier
local ; arborescence monorepo (backend, frontend, ai-service, database,
docker, docs, monitoring…) ; fichiers de gouvernance (README, LICENSE MIT,
CONTRIBUTING avec Git Flow + Conventional Commits, SECURITY, CODE_OF_CONDUCT) ;
hygiène Git (.gitignore multi-stack, .gitattributes forçant LF, .editorconfig).
Git Flow opérationnel : `main` (stable), `develop` (défaut), branches
`feature/*` mergées par PR. Templates de PR/issues, Dependabot, CODEOWNERS.

**Choix techniques.**
- *Dépôt public* : minutes GitHub Actions illimitées, CodeQL et SonarCloud
  gratuits — indispensable pour la chaîne DevSecOps complète visée.
- *Monorepo* (ADR-001) : une fonctionnalité traverse souvent les 3 services ;
  PRs atomiques, un seul Compose, CI filtrée par chemins.
- Adresse noreply GitHub configurée pour l'attribution des commits.

**Difficulté rencontrée.** Dependabot a signalé des labels inexistants
(`ci`, `dependencies`) référencés par sa configuration : GitHub ne les crée
pas automatiquement. **Solution :** stratégie complète de 24 labels créée
par script via l'API GitHub (priorités, types, zones `area:*`, statuts).

---

## 2026-07-10 — J1 : Architecture fondatrice (PR #1 docs)

**Réalisé.** `ARCHITECTURE.md` en modèle C4 (contexte, conteneurs,
composants) + 4 ADR : monorepo (001), Clean Architecture + DDD en 4 modules
Maven (002), microservice IA indépendant derrière un contrat REST (003),
PostgreSQL source de vérité unique avec Flyway (004).

**Décisions structurantes** (justifications complètes dans les ADR) :
- Le frontend ne parle qu'au backend ; le backend est le seul écrivain en
  base ; temps réel par WebSocket, jamais de polling.
- 8 bounded contexts DDD : alerts, incidents, assets, intelligence, soar,
  connectors, identity, reporting.
- Frontière stricte : la plateforme *consomme* les outils SOC via des
  connecteurs, ne les déploie jamais.

---

## 2026-07-10 — J1 : Triage Dependabot — le piège Spring Boot 4 (PR #5–#11)

**Difficulté.** Dès l'activation de Dependabot sur Maven, 5 PRs ouvertes dont
deux **majeures dangereuses** : Spring Boot 3.5.16 → 4.1.0 et springdoc 2.x
→ 3.x (qui cible Spring Boot 4). Les accepter aurait cassé la stack imposée
par le cahier des charges.

**Solution.** Fermeture motivée des deux majeures ; bumps sûrs (ArchUnit,
JaCoCo) appliqués manuellement dans une PR unique car ils modifiaient le
même `pom.xml` et se seraient mutuellement invalidés ; règles `ignore`
ajoutées à la config Dependabot pour les majeures `org.springframework*` et
`org.springdoc:*`. **Leçon :** l'automatisation des dépendances exige un
garde-fou humain et une configuration explicite de la politique de versions.

---

## 2026-07-10 — J1 : Squelette backend multi-module (PR #4)

**Réalisé.** POM parent + 4 modules Maven (`domain`, `application`,
`infrastructure`, `api`) conformes ADR-002 ; Java 21, Spring Boot 3.5 ;
Maven Wrapper ; profils `dev`/`docker`/`prod` ; Actuator ; CI backend
(build + tests + JaCoCo) filtrée sur `backend/**` ; CodeQL activé à ce
moment précis (l'activer avant aurait échoué : aucun code à analyser).

**Choix.** Sens des dépendances `api → application → domain ← infrastructure`,
domaine 100 % sans framework — décision qui sera *verrouillée par machine*
au jalon suivant.

---

## 2026-07-10 — J1 : Socle transverse — erreurs RFC 9457 et ArchUnit (PR #10)

**Réalisé.** Hiérarchie d'exceptions métier pure Java (codes stables
machine-readable) ; `GlobalExceptionHandler` traduisant toute erreur en
RFC 9457 Problem Details (validation → champs structurés ; erreur interne →
500 opaque loggé côté serveur, jamais fuité) ; **test ArchUnit** qui fait
échouer la CI si une dépendance framework entre dans le domaine ou si le
sens des couches est violé.

**Choix.** L'ADR-002 cesse d'être une promesse documentaire : c'est une
contrainte exécutée à chaque build.

---

## 2026-07-10 — J1 : Contexte identity — domaine et persistance (PR #13)

**Réalisé.** Entité riche `User` (invariants imposés, normalisation
username/email, jamais de mot de passe en clair dans le domaine), enum RBAC
4 rôles, port `UserRepository` ; introduction JPA/Flyway ; migration
`V1__identity.sql` établissant les conventions ADR-004 (UUID, colonnes
d'audit, soft delete, index uniques partiels insensibles à la casse) ;
entité JPA séparée du domaine + MapStruct ; **Testcontainers** : le test de
contexte démarre un vrai PostgreSQL 18 éphémère, applique Flyway et fait
valider le mapping par Hibernate (`ddl-auto: validate`).

**Choix débattu.** Testcontainers plutôt que H2 : H2 « ment » (pas de JSONB,
comportements différents) ; des tests verts sur H2 peuvent casser en
production. Coût accepté : ~40 s de test en plus, Docker requis.

**Difficultés.**
1. Premier run : échec réseau du pull de l'image Testcontainers (TLS
   handshake timeout, Docker Desktop venait de démarrer). Solution : pré-pull
   manuel des images.
2. La validation Hibernate a immédiatement payé : `CHAR(64)` en SQL vs
   `VARCHAR(64)` attendu par l'entité — divergence attrapée avant tout merge.

---

## 2026-07-10 — J1 : Authentification JWT avec rotation (PR #14)

**Réalisé.** Spring Security stateless ; access token JWT HS256 15 min
(secret ≥ 256 bits exigé au démarrage, échec sinon) ; refresh token opaque
384 bits CSPRNG dont **seul le hash SHA-256 est stocké** ; **rotation avec
détection de vol** (familles de tokens : rejouer un token consommé révoque
toute la famille — OWASP) ; endpoints login/refresh/logout/me ; 401/403 de
la filter chain au format RFC 9457 ; auditeur JPA = principal authentifié ;
compte admin bootstrap au premier démarrage.

**Bug réel n°1 attrapé par les tests d'intégration.** La détection de
réutilisation révoquait la famille *puis* levait l'exception 401… dont le
rollback transactionnel **annulait la révocation** — la session volée
restait vivante. Correctif : `@Transactional(noRollbackFor =
InvalidRefreshTokenException.class)`, commenté dans le code.

**Épisode DevSecOps (CodeQL).** 3 alertes sur la PR : (1) mot de passe
généré loggé → design changé, `SMARTSOC_ADMIN_PASSWORD` obligatoire avec
échec au démarrage, défaut marqué dev uniquement ; (2) paramètre inutilisé →
corrigé ; (3) CSRF désactivé → **faux positif documenté et rejeté avec
justification auditée** (API stateless à bearer token, aucune surface CSRF).

---

## 2026-07-10 — J1 : Gestion des utilisateurs et OpenAPI (PR #15)

**Réalisé.** Premier use case du module `application`
(`UserManagementService`) : CRUD complet avec unicité, PATCH sémantique,
soft delete ; règle de sécurité : désactiver/supprimer un utilisateur
révoque immédiatement toutes ses sessions ; port `PasswordHasher` ;
endpoints `/api/v1/users` réservés ADMIN (`@PreAuthorize`) ; politique de
mot de passe 12–128 caractères ; springdoc/Swagger avec flux bearer câblé.

**Bug réel n°2.** Le refus RBAC (`AccessDeniedException` levée *dans* le
contrôleur par method security) était avalé par le handler générique →
**500 au lieu de 403**. Correctif : mapping explicite vers un problème
RFC 9457 403, cohérent avec la filter chain. Attrapé par le test
d'intégration « un analyste reçoit 403 ».

---

## 2026-07-10 — J1 : Conteneurisation Docker Compose (PR #16)

**Réalisé.** Dockerfile backend multi-stage (cache des dépendances Maven,
runtime JRE 21 Alpine, utilisateur non-root, healthcheck actuator) ;
`docker-compose.yml` PostgreSQL 18 + backend, secrets obligatoires
(`:?err`), démarrage ordonné par healthchecks ; workflow CI Docker : build
buildx avec cache GHA + **Trivy** (rapport MEDIUM+, gate bloquante sur les
CRITICAL corrigeables, SARIF dans l'onglet Security). **ADR-005** : la
séparation plateforme / SOC / IA est confirmée par l'équipe — les deux
modules IA sont développés hors dépôt, la plateforme livre les contrats.

**Vérification réelle.** Stack démarrée localement : 2 conteneurs healthy,
login admin → tokens, `/auth/me` → ADMIN, Swagger 200. Scan : 0 CRITICAL.

**Difficultés.**
1. Tag d'action inexistant (`trivy-action@0.33.1` au lieu de `v0.33.1`).
2. Piège documenté du format SARIF : le filtre `severity` y est ignoré par
   défaut → la gate bloquait malgré 0 CRITICAL. Correctif :
   `limit-severities-for-sarif: true`.

---

## 2026-07-10 — J1 : Chaîne DevSecOps complète (PR #17, #18)

**Réalisé.** **Gitleaks** (historique complet + scan hebdomadaire,
allowlist minimale limitée aux défauts dev documentés) — historique vérifié
100 % propre ; **SonarCloud** (workflow Maven+JaCoCo qui saute proprement
tant que `SONAR_TOKEN` absent, guide d'onboarding rédigé) ;
**dependency-review** (bloque l'introduction de dépendances vulnérables
high+) ; **push protection GitHub** activée par API.

**Choix.** SonarCloud plutôt que SonarQube auto-hébergé (même moteur,
gratuit en public, ~2 Go de RAM économisés sur la future VM Azure). OWASP
Dependency Check **différé** : triple doublon avec Trivy + Dependabot +
dependency-review, et il ralentirait chaque build (sync NVD).

**Difficulté.** dependency-review échouait : le *dependency graph* GitHub
n'était pas activé sur le dépôt. Activé par API avec les alertes Dependabot.

**Premières métriques SonarCloud (après onboarding)** : couverture
**70,5 %**, 0 bug, 0 duplication, 2 code smells mineurs, 1 « vulnérabilité »
= le même faux positif CSRF que CodeQL (S4502), traité par suppression
**dans le code** avec justification versionnée (PR #18).

---

## 2026-07-10 — J1 : Frontend F1 — squelette Vite/React (PR #19)

**Réalisé.** Scaffold Vite 8 + React 19 + TypeScript 6 ; Prettier ; Vitest +
Testing Library (premier smoke test) ; proxy dev `/api` → backend (le
frontend ne connaît jamais l'URL du backend, même topologie qu'en
production) ; CI frontend (format, lint, tests + couverture, build,
artefact) filtrée sur `frontend/**`.

**Choix.** **Oxlint conservé** au lieu d'ESLint : c'est désormais le linter
par défaut du template Vite (Rust, ~50× plus rapide, règles react-hooks
incluses) ; ESLint reste possible si un plugin spécifique manque un jour.

**Observation.** La chaîne DevSecOps a immédiatement servi :
dependency-review a scanné les ~400 nouveaux paquets npm de la PR (aucun
vulnérable), Gitleaks a validé l'ensemble.

---

## 2026-07-10 — J1 : Frontend F2 — thème sombre et layout de la console (PR #21)

**Réalisé.** Thème MUI sombre inspiré des consoles SOC modernes (fond
bleu-nuit, accent bleu, palette de **sévérités centralisée**
critical/high/medium/low/info exportée pour tous les futurs modules) ;
layout applicatif : topbar fixe, sidebar permanente organisée en 4 sections
(Supervision, Intelligence, Réponse, Plateforme) avec surlignage du module
actif ; routing React Router — 13 routes correspondant aux modules du
cahier des charges, chacune sur un stub titré indiquant son jalon ;
polices Roboto auto-hébergées (`@fontsource`) — aucune dépendance CDN.

**Choix.**
- La carte de navigation (`navigation.ts`) est le miroir des bounded
  contexts backend : même vocabulaire du domaine à l'API et à l'UI.
- Les chemins d'URL sont définitifs dès maintenant ; seuls les contenus
  des pages changeront — les liens et captures du rapport resteront valides.

**Difficultés.**
1. MUI 7 a supprimé la prop `paragraph` de `Typography` (breaking change
   silencieux vs les exemples de la documentation courante) — corrigé.
2. Testing Library : sans `globals: true` côté Vitest, le **cleanup
   automatique entre tests ne s'exécute pas** — le second test voyait le DOM
   du premier (« Found multiple elements »). Correctif : `cleanup()` explicite
   dans le setup de test, commenté pour les futurs contributeurs.

**Vérification.** Tests (2/2), lint, build OK ; rendu vérifié visuellement
dans le navigateur (thème, sections, surlignage actif, redirection
`/` → `/dashboard`).

---

## 2026-07-11 — J2 : Frontend F3 — authentification complète (PR #22)

**Réalisé.** Flux d'authentification client branché sur l'API réelle :
page de login (erreurs RFC 9457 affichées) ; client axios unique avec
intercepteurs — injection du Bearer, **refresh silencieux sur 401 en
« single-flight »** (dix requêtes simultanées en 401 ⇒ un seul appel
refresh, indispensable avec la rotation des tokens) ; restauration de
session au chargement (`bootstrapSession`) ; guards `RequireAuth` (loader
pendant la restauration, redirection login avec retour à la destination) et
`RequireRole` (RBAC, page 403) ; menu utilisateur (rôle affiché,
déconnexion) ; état de session en Redux Toolkit.

**Choix de sécurité documenté.** Access token **en mémoire uniquement**
(jamais dans le storage, expire en 15 min) ; refresh token en localStorage
pour survivre au rechargement — risque XSS assumé et mitigé côté backend
par la rotation + détection de réutilisation qui révoque la famille
(PR #14). Compromis standard des SPA sans cookie httpOnly.

**Vérification de bout en bout dans le navigateur, contre le backend
Docker réel** : login admin → dashboard ; rechargement complet → session
restaurée par le refresh silencieux ; route ADMIN accessible ; menu
utilisateur ; déconnexion (purge du token vérifiée dans localStorage) ;
accès anonyme redirigé vers login ; mauvais mot de passe → « Invalid
username or password » (le 401 opaque du backend, sans oracle).

**Difficulté.** Pendant la vérification, erreur « could not find
react-redux context value » : artefact de HMR (le module router rechargé
sans réexécuter `main.tsx` qui monte le Provider). Un rechargement complet
la dissipe — aucun défaut de code ; noté pour ne pas confondre artefact de
dev et vrai bug.

---

## 2026-07-11 — J2 : Frontend F4 — module Administration/Utilisateurs (PR #23)

**Réalisé.** Première feature complète de bout en bout de la console,
branchée sur l'API \`/api/v1/users\` (PR #15) : tableau des comptes
(rôle en chip coloré, statut actif/désactivé, badge « vous ») ; création
avec validation (mot de passe 12+, rôle sélectionnable) ; édition PATCH
(nom, rôle, activation) ; suppression logique avec confirmation explicitant
la révocation des sessions. **React Query** gère l'état serveur (cache
30 s, invalidation après chaque mutation) — première utilisation, complète
la paire avec Redux Toolkit (état client/session) comme prévu en conception.

**Choix UX/sécurité.** L'interface empêche l'auto-sabotage : impossible de
supprimer son propre compte, de se désactiver ou de changer son propre
rôle (contrôles désactivés avec explication). Les erreurs RFC 9457 du
backend (unicité username/email → 422) s'affichent telles quelles dans les
dialogues.

**Difficultés.** Série de breaking changes MUI 7 vs les exemples courants :
props \`paragraph\`, \`fontWeight\`, \`display\` retirées de Typography (→ \`sx\`),
icône \`DeleteOutline\` renommée \`DeleteOutlined\`. Attrapées par la
compilation TypeScript avant tout commit — illustre l'intérêt du typage
strict sur les dépendances récentes. Côté tests : un mock de module doit
couvrir **tous** les exports importés par l'arbre rendu, et l'attente
asynchrone doit cibler un élément qui n'apparaît qu'après chargement.

---

## 2026-07-11 — J2 : Frontend F5 — conteneurisation nginx, plateforme complète en une commande (PR #24)

**Réalisé.** Dockerfile frontend multi-stage (build Vite sous Node 24 →
**nginx unprivileged**, non-root comme le backend) ; configuration nginx
reproduisant exactement le contrat du dev : SPA fallback pour React Router,
relais `/api` vers le service backend, en-têtes de sécurité
(nosniff, X-Frame-Options DENY, Referrer-Policy), cache immutable des
assets fingerprintés, gzip. Service `frontend` ajouté au Compose
(healthcheck, démarrage après backend healthy). Workflow Docker CI passé en
**matrice** : les deux images sont construites et scannées par Trivy
(gate CRITICAL) avec caches séparés.

**Choix.** Le navigateur ne connaît toujours qu'une seule origine : en dev
Vite proxifie `/api`, en conteneur nginx fait de même — aucune URL backend
dans le code frontend, aucun CORS à configurer, même topologie qu'en
production Azure derrière un reverse proxy.

**Difficulté.** Conteneur frontend « unhealthy » alors qu'il répondait
parfaitement depuis l'hôte : dans le conteneur, `localhost` se résout
d'abord en IPv6 (`::1`) alors que nginx n'écoutait qu'en IPv4 — le
healthcheck échouait donc seul. Correctif : `127.0.0.1` explicite dans le
healthcheck, commenté dans le Dockerfile. Piège réseau classique des
conteneurs, bon cas d'école pour le rapport.

**Vérification.** `docker compose up -d --build` : 3 conteneurs healthy ;
connexion via nginx (port 3000) ; accès direct à `/admin/users` en
rechargement complet — valide d'un coup le fallback SPA, la restauration
de session et le proxy API ; en-têtes de sécurité présents (vérifiés par
curl). La plateforme complète démarre d'une seule commande.

---

## 2026-07-11 — J2 : Jalon Alertes A1 — domaine et persistance (PR #25)

**Réalisé.** Le concept central de la plateforme : l'entité `Alert`
normalisée (source, externalId, sévérité 5 niveaux, hostname, ruleId,
techniques MITRE, payload brut intégral) avec **cycle de vie gardé par le
domaine** (NEW → ACKNOWLEDGED → IN_PROGRESS → RESOLVED, sortie
FALSE_POSITIVE, statuts terminaux verrouillés) ; champs IA nullables
(`aiScore`, `aiVerdict`) prêts pour le classifieur externe (ADR-005) avec
la méthode `applyAiAssessment` bornée [0,1]. **Pagination indépendante du
framework** introduite dans le domaine (`PageQuery`/`PageResult`).
Migration `V3__alerts.sql` : JSONB pour le payload brut et les
techniques MITRE, index unique de déduplication `(source, external_id)`,
index des chemins d'accès de la console, **pas de soft delete** (une
alerte est une pièce d'évidence SOC). Adaptateur avec recherche par
`Specification` (filtres statut/sévérité/source, tri détection récente).

**Choix.**
- Une alerte ne se « rouvre » pas : statuts terminaux définitifs, on
  ingère un nouvel événement — cohérent avec les pratiques SIEM.
- `raw_payload` JSONB plutôt que colonne texte : requêtable plus tard
  (corrélation, threat hunting) sans re-parsing.
- Le domaine impose l'unicité de création par `Alert.ingest()` : pas de
  constructeur public, pas d'état incohérent possible.

**Vérification.** 29 tests verts, dont 3 nouveaux tests d'intégration sur
PostgreSQL réel : aller-retour JSONB, **déduplication par index unique**
(violation attendue vérifiée), recherche filtrée par sévérité avec
pagination (2 éléments/page, total ≥ 3, 2 pages).

---

## 2026-07-11 — J2 : Jalon Alertes A2 — ingestion webhook (PR #26)

**Réalisé.** `POST /api/v1/ingest/alerts`, le point d'entrée des outils
SOC : authentification par **clé d'API** (`X-API-Key`, filtre dédié avec
**comparaison en temps constant** — pas d'oracle de timing), rôle
`INGEST` distinct des utilisateurs ; **ingestion idempotente** (201 à la
création, 200 avec l'alerte existante au replay — les retries des outils
SOC sont sûrs), y compris en cas de **course entre deux webhooks
identiques** (violation d'unicité rattrapée puis relecture — service
volontairement sans transaction englobante, expliqué en commentaire) ;
clé vide = ingestion désactivée avec warning (plateforme autonome,
ADR-005). **Contrat d'intégration publié** (`docs/integration/
alert-ingestion.md`) : schéma, sémantique d'idempotence, guide de mapping
des sévérités Wazuh/Suricata, exemples curl. **Scripts de simulation**
(`scripts/simulate-alerts.sh` + `.ps1`) : jeu d'alertes réalistes
multi-sources avec replay volontaire pour démontrer l'idempotence.

**Choix.** Clé d'API statique plutôt que JWT pour l'ingestion : les
outils SOC ne savent pas jouer un flux login/refresh ; c'est le standard
du domaine (TheHive, Shuffle). Révocable par changement d'environnement.

**Difficulté.** Le module `application` ne compilait plus : `@Slf4j`
requiert `slf4j-api`, absent de ce module volontairement minimal.
Ajouté en tant qu'API pure (l'implémentation reste fournie par le
module `api`) — le module reste sans framework lourd.

**Vérification.** 33 tests verts, dont 4 nouveaux tests d'intégration du
webhook complet : 201 + normalisation, **replay → 200 même id**, clé
absente/fausse → 401 RFC 9457, payload invalide → 400 avec champs.

---

## 2026-07-11 — J2 : Jalon Alertes A3 — API de consultation et triage (PR #27)

**Réalisé.** `GET /api/v1/alerts` (filtres statut/sévérité/source,
pagination uniforme via `PageResponse`), `GET /{id}` (détail avec payload
brut et techniques MITRE), `PATCH /{id}/status` (transitions du cycle de
vie). **RBAC gradué** : lecture pour tout utilisateur authentifié
(VIEWER inclus), triage réservé à ANALYST et plus. Transition illégale →
422 RFC 9457 avec le code `INVALID_ALERT_TRANSITION` (la règle vient du
domaine, l'API ne fait que la traduire).

**Vérification.** 38 tests verts, dont 5 nouveaux tests d'intégration
couvrant le **flux SOC complet sur PostgreSQL réel** : ingestion par clé
d'API → consultation JWT filtrée/paginée → triage à travers tout le cycle
de vie → transition illégale rejetée 422 → **un VIEWER peut lire mais
reçoit 403 au triage** (RBAC prouvé de bout en bout).

**Deux incidents DevSecOps sur cette PR (chaîne à l'œuvre).**
1. *Quality gate SonarCloud en échec* : couverture du nouveau code 54,5 %
   < 80 % — alors que tout était testé. Cause : le problème JaCoCo
   classique du multi-module — les classes du module `application`
   exercées par les tests d'intégration du module `api` étaient créditées
   0 % (rapports par module). Correctif : rapport **`report-aggregate`**
   produit par le module api + propriété `sonar.coverage.jacoco.
   xmlReportPaths`. Après correctif : **100 % de couverture sur le
   nouveau code**, gate OK.
2. *Gitleaks en échec* : détection de la fausse clé d'API des tests
   d'intégration (entropie 4,39 — le scanner fait son travail).
   Traitement : allowlist **par valeur littérale exacte** dans
   `.gitleaks.toml`, datée et commentée — le reste du dépôt et de
   l'historique reste intégralement scanné.

---

## 2026-07-11 — J2 : Jalon Alertes A4 — temps réel WebSocket (PR #29)

**Réalisé.** Endpoint STOMP `/ws` ; chaque **nouvelle** alerte ingérée
(pas les replays) est publiée sur `/topic/alerts` — la règle
d'architecture « WebSocket, jamais de polling » devient réalité.
**Découplage par événement applicatif** : l'ingestion publie
`AlertIngestedEvent` ; le relais WebSocket n'est qu'un listener du module
api — demain, le scoring IA et les déclencheurs SOAR s'abonneront au même
événement sans toucher à l'ingestion. **Authentification du CONNECT
STOMP par le même JWT que l'API REST** (header natif Authorization —
jamais de token en query string, il finirait dans les logs des proxys) ;
connexion refusée sans token valide. Le payload STOMP est le même
`AlertResponse` que l'API REST : un seul contrat côté frontend.

**Vérification.** 40 tests verts, dont un test temps réel de bout en
bout : un vrai client STOMP authentifié s'abonne, le webhook ingère →
**l'alerte arrive en < 10 s sur le topic** ; le replay du même événement
n'est pas republié ; le CONNECT sans token est rejeté.

---

## 2026-07-11 — J2 : Jalon Alertes A5 — module frontend Alertes (PR #30)

**Réalisé.** La file de triage — le cœur opérationnel de la console :
table paginée avec sévérités colorées (palette centralisée du thème),
filtres statut/sévérité (React Query, `keepPreviousData` pour une
pagination sans clignotement) ; **panneau de détail** : contexte SOC
complet, chips MITRE ATT&CK **cliquables vers attack.mitre.org**,
payload brut JSON formaté (l'évidence), emplacement du score IA
(« Non évalué — service IA non connecté » tant que le classifieur
externe n'est pas branché, ADR-005) ; **actions de triage** limitées aux
transitions autorisées — la carte des transitions du frontend est le
miroir du domaine backend, qui reste l'autorité (422 sinon) ; boutons
masqués pour le rôle VIEWER. Champ `rawPayload` ajouté au contrat
`AlertResponse` backend (additif).

**Difficultés (série MUI 7).** Trois breaking changes de props attrapés
par TypeScript à la compilation (`paragraph`, `fontWeight`, `flexWrap`
retirés au profit de `sx`) — le typage strict transforme des breaking
changes silencieux en erreurs de build immédiates.

**Vérification.** 11 tests frontend + 40 backend verts ; vérification
E2E navigateur contre la stack Docker reconstruite avec alertes
simulées (voir A6).

---

## 2026-07-11 — J2 : Jalon Alertes A6 — temps réel de bout en bout (PR #31)

**Réalisé.** Le jalon Alertes est complet. Client STOMP frontend
(`@stomp/stompjs`) : URL relative `/ws` (même topologie que `/api` —
proxy Vite en dev, nginx en conteneur, **aucun hôte codé en dur**) ;
JWT de session posé sur la trame CONNECT, relu à chaque reconnexion
(`beforeConnect`, compatible rotation) ; reconnexion automatique (5 s) ;
badge « Temps réel / Hors ligne » dans la file ; à chaque alerte reçue,
**invalidation du cache React Query** — la file se rafraîchit en
respectant filtres et pagination courants, plutôt qu'une insertion
manuelle dans le DOM qui les contournerait. Proxy `/ws` ajouté à Vite
(`ws: true`) et à nginx (`Upgrade`/`Connection`, `proxy_read_timeout`
long pour les connexions persistantes).

**Vérification E2E en conditions réelles** — la démonstration clé du
projet : page Alertes ouverte, badge « Temps réel » actif, puis
`simulate-alerts.sh` exécuté dans un terminal → **la table passe de 5 à
10 lignes sans aucun rechargement**, pagination mise à jour (1–10 of 10).
Chaîne complète prouvée : webhook (clé d'API) → événement applicatif →
STOMP `/topic/alerts` → invalidation → re-fetch.

---

## 2026-07-11 — J2 : Jalon Dashboard — la console prend vie (PR #32)

**Réalisé.** Backend : `GET /api/v1/alerts/stats` — agrégations JPQL
(sévérité, statut, source) + timeline 7 jours en SQL natif
(`date_trunc`), **jours vides inclus** (une courbe d'activité doit
montrer les silences autant que les pics) ; type domaine
`AlertStatistics`, port étendu. Frontend : 4 cartes KPI (totales, à
trier, critiques, faux positifs), 3 graphes **ECharts** (aire d'activité
7 jours, donut de sévérités aux couleurs de la palette centralisée,
barres top sources) via un wrapper React minimal (init/dispose/resize
liés au cycle de vie) ; **badge temps réel** : le dashboard réutilise le
hook STOMP du jalon Alertes — la clé de cache `['alerts','stats']`
partage le préfixe invalidé à chaque alerte reçue, donc les KPIs et
graphes se rafraîchissent seuls.

**Vérification E2E.** Dashboard ouvert : 10 alertes, 8 à trier,
3 graphes rendus, badge « Temps réel » ; injection de 5 alertes par le
script de simulation → **cartes passées à 15/13 en direct, sans
rechargement**. Bonus vérifié : la session a survécu au redémarrage du
backend (refresh silencieux). 41 tests backend + 14 frontend.

---

## 2026-07-12 — Correction de l'architecture SOC (PR #34)

**Réalisé.** Régénération du diagramme d'architecture SOC après validation
d'équipe des flux réels. Cinq corrections structurantes vs les versions
précédentes : (1) **suppression de Nginx** — Cloudflare Tunnel expose
directement le backend Spring Boot ; (2) **Siham ne communique jamais avec
SmartSOC** — elle n'envoie que ses logs au Wazuh Manager d'Imane via
WireGuard ; (3) **deux chemins vers SmartSOC** (Wazuh API *et* Shuffle),
tous deux via Cloudflare Tunnel uniquement ; (4) **MISP alimente Wazuh
*et* Shuffle** en IOC ; (5) flux d'attaque explicite Ilyas (Kali / Atomic
Red Team) → endpoints Siham → détection → Wazuh. Noms réels des étudiants
(Siham, Ilyas) substitués aux « Étudiant 2/3 ». Diagramme Mermaid corrigé
+ version visuelle SVG produite pour la soutenance.

**Choix.** Cloudflare Tunnel comme unique point d'entrée HTTPS : connexion
sortante depuis le PC d'Imane, **aucun port entrant ouvert**, aucun reverse
proxy à administrer — plus sûr et plus simple qu'un Nginx exposé.

---

## 2026-07-12 — Alignement plateforme / architecture SOC : suppression de Nginx (PR #35, ADR-007)

**Contexte.** L'architecture SOC exige que Cloudflare Tunnel soit l'unique
entrée HTTPS vers SmartSOC, **sans Nginx**. Or l'implémentation (PR #24)
exposait la plateforme via un conteneur `frontend` sous Nginx —
contradiction directe avec le schéma. Contradiction identifiée en revue
d'architecture, corrigée ici.

**Réalisé.** **Spring Boot sert désormais lui-même le build React**
(embarqué dans `classpath:/static/`) : la plateforme n'expose qu'**un seul
service `:8080`** rendant l'UI *et* l'API. Nginx supprimé
(`frontend/Dockerfile` + `frontend/nginx.conf` retirés, service `frontend`
retiré du Compose). Dockerfile multi-stage (Node build React → Maven copie
le `dist` dans les ressources statiques → package). Service SPA
(`WebMvcConfigurer`) : fichiers statiques réels + fallback `index.html`
pour les routes client, en excluant `/api`, `/actuator`, `/swagger`,
`/api-docs`, `/webjars`, `/ws`. Sécurité : UI publique (coquille HTML),
**API et actuator sensibles restent protégés**. CI Docker : une seule
image au lieu de deux. **ADR-007** rédigé.

**Vérification réelle.** 32 tests backend verts (dont Swagger et les 401
d'auth, inchangés). Stack Docker reconstruite (2 conteneurs : backend +
postgres). Sur `:8080` : UI racine 200, deep-link `/alerts` 200 (fallback
SPA), asset JS hashé 200, `/api/v1/auth/me` sans token 401, Swagger 200,
health 200, login admin → token → identité ADMIN. React se monte et
redirige `/alerts` → `/login` — le tout servi par Spring Boot, sans Nginx.

---

## 2026-07-12 — Jalon Incidents I1 — domaine et persistance (PR #35)

**Réalisé.** Le contexte `incidents` : un incident **regroupe des alertes**
et porte le travail de l'analyste. Entité `Incident` (domaine pur) avec
**cycle de vie gardé** (OPEN → INVESTIGATING → CONTAINED → RESOLVED →
CLOSED, réouverture depuis RESOLVED, CLOSED terminal), affectation
normalisée, référence lisible. `IncidentTimelineEntry` (trace
d'investigation horodatée) et `IncidentEventType`. Ports `IncidentRepository`
et `IncidentReferenceGenerator`. Migration `V4` : tables `incidents`,
`incident_alerts` (liaison N↔1, FK vers `alerts`, `ON DELETE CASCADE`),
`incident_timeline`, + **séquence PostgreSQL** pour les références. Pas de
soft delete (pièce de dossier SOC). Adaptateurs : recherche par
`Specification` (statut/sévérité/assigné, tri ouverture récente), liaison
d'alertes **idempotente** (`ON CONFLICT DO NOTHING`), générateur de
référence via `nextval`.

**Choix.**
- **Référence `INC-YYYY-NNNN`** générée par séquence Postgres : unicité et
  monotonie garanties par la base, sans course possible (vs un max()+1
  applicatif).
- La `Severity` est **réutilisée** du contexte `alerts` (même échelle) —
  vocabulaire partagé du domaine.
- Liaison d'alertes idempotente : rejouer un lien ne crée pas de doublon.

**Vérification.** 41 tests verts, dont 5 nouveaux tests d'intégration sur
PostgreSQL réel : référence au format attendu et monotone, aller-retour,
liaison/déliaison idempotente d'une alerte (avec FK réelle), timeline
ordonnée, recherche filtrée par sévérité et paginée. Migration V4 appliquée.

---

## 2026-07-14 — Jalon Incidents I2 — API REST (PR #36)

**Réalisé.** `IncidentService` (module application) + `IncidentController` :
création manuelle, **escalade depuis une alerte** (crée l'incident à partir
du titre/sévérité de l'alerte et la lie), liste filtrée/paginée, détail
(incident + alertes liées + timeline), transitions de statut, assignation /
désassignation, notes, liaison/déliaison d'alertes. **Chaque action inscrit
une entrée de timeline** avec l'analyste authentifié (JWT `sub`) comme
auteur. RBAC gradué : lecture pour tout authentifié, écriture réservée à
ANALYST et plus (`@PreAuthorize`). Transition illégale → 422
`INVALID_INCIDENT_TRANSITION` (règle du domaine). Réutilisation de
`AlertResponse` pour les alertes liées (contrat unique).

**Vérification.** 47 tests verts, dont 5 nouveaux tests d'intégration API
sur PostgreSQL réel : création + timeline `CREATED`, escalade depuis une
alerte réellement liée, cycle de vie + rejet 422, assignation/note, **RBAC
(VIEWER lit mais reçoit 403 à l'écriture)**.

---

## 2026-07-14 — Jalon Incidents I3 — module frontend (PR #37)

**Réalisé.** Le module Incidents de la console : liste filtrée/paginée
(référence, sévérité, statut, assigné, date), dialogue de création, et
**tiroir de détail** complet — transitions de statut (miroir du cycle de
vie du domaine), assignation/désassignation, alertes liées (avec déliaison),
timeline avec ajout de notes. **Bouton « Escalader en incident »** ajouté au
tiroir d'alerte : crée un incident depuis l'alerte, la lie, et redirige vers
`/incidents`. Écriture réservée aux analystes ; réutilisation de
`SeverityChip` et du contrat `AlertResponse`.

**Vérification E2E réelle** (navigateur Vite → backend Docker reconstruit
avec l'API I2) : login, page Incidents, création via API, **escalade d'une
alerte → INC-2026-0002 avec l'alerte liée**, ouverture du détail (alerte
liée + timeline `CREATED` visibles), **transition OPEN → INVESTIGATING** qui
inscrit « OPEN → INVESTIGATING » dans la timeline et met à jour les boutons.
16 tests frontend (2 nouveaux). Jalon Incidents (I1→I3) terminé.

---

## 2026-07-17 — Jalon IA C1 — contrats d'intégration des services IA (PR #43, ADR-008)

**Contexte.** Les deux services IA du projet (classifieur TP/FP et agent
conversationnel SOC) sont développés séparément, hors dépôt, puis intégrés
à la plateforme (ADR-005). Pour que les deux équipes avancent en parallèle
sans se bloquer, la frontière doit être fixée **avant** toute
implémentation : c'est l'objet de ce jalon, purement contractuel.

**Réalisé.** Deux contrats **OpenAPI 3.1** publiés dans `docs/integration/`,
désormais référence commune versionnée (semver) :
- `ai-classifier-api.yaml` — scoring d'une alerte : la plateforme envoie
  les caractéristiques normalisées (alignées sur l'entité `Alert`), le
  service répond **verdict explicite + score [0,1] + version du modèle**.
- `ai-assistant-api.yaml` — chat **sans état** : l'historique complet est
  porté par chaque requête (la plateforme reste propriétaire des
  conversations), contexte métier optionnel (alerte/incident).
**ADR-008** fixe l'architecture d'intégration côté plateforme : un **port**
applicatif par service IA, deux adaptateurs par port (`simulation` par
défaut — stub embarqué, plateforme démoable seule ; `live` — client
OpenFeign, URL et clé d'API par variables d'environnement), timeouts
5 s / 30 s, circuit breaker, et **IA jamais sur le chemin critique**
(classification asynchrone après réponse au webhook). README d'intégration
avec outillage pour l'équipe IA (préview Swagger, génération de squelette
FastAPI, tests de conformité Schemathesis).

**Choix.**
- **Le seuil de décision TP/FP appartient au modèle**, pas à la plateforme :
  le verdict est toujours explicite dans la réponse, le score n'est jamais
  interprété côté SmartSOC — on peut réentraîner le modèle sans toucher à
  la plateforme.
- **Assistant sans état côté service** : redémarrage ou remplacement du
  service IA sans perte de conversation ; le service n'est jamais exposé
  au frontend, la plateforme le proxifie derrière JWT + RBAC.

**Vérification.** Les deux specs passent `redocly lint` (0 erreur) ;
alignement contrôlé avec le domaine existant (`Severity`, `AiVerdict`,
invariant score ∈ [0,1] de `Alert.applyAiAssessment`).

---

## 2026-07-17 — Jalon IA C2 — classifieur TP/FP derrière un port interchangeable (PR #44)

**Réalisé.** Première concrétisation de l'ADR-008 : le port `AlertClassifier`
(couche application) et ses **deux adaptateurs sélectionnés par
configuration** (`SMARTSOC_AI_MODE`) — le futur service IA remplacera la
simulation par pure configuration, **zéro refactoring** côté plateforme :
- `simulation` (défaut) : stub embarqué **déterministe** (score de base par
  sévérité ± bruit dérivé de l'id) — la plateforme se démontre seule ;
- `live` : client **OpenFeign** implémentant exactement le contrat
  `ai-classifier-api.yaml` (X-API-Key, timeouts 2 s/5 s, **circuit breaker
  Resilience4j**) ; les beans Feign n'existent qu'en mode live.

L'IA n'est **jamais sur le chemin critique** : classification asynchrone
(`@Async` sur `AlertIngestedEvent`) après la réponse au webhook ; l'échec
laisse l'alerte traitable sans score. `POST /api/v1/alerts/{id}/classify`
(ANALYST+) pour la (re)classification manuelle — 503 `AI_UNAVAILABLE` si le
service ne répond pas. Alerte classée poussée sur `/topic/alerts/updates`
(topic distinct : `/topic/alerts` garde la sémantique « nouvelle alerte »,
les tests temps réel existants restent intacts).

**Difficulté rencontrée.** Démarrage du contexte cassé par un
`ClassNotFoundException` Resilience4j : le pin direct de
`resilience4j-spring-boot3` 2.3.0 cohabitait avec des transitives 2.2.x
gérées par le BOM Spring Cloud. **Solution :** import du
`resilience4j-bom` AVANT `spring-cloud-dependencies` dans le
dependencyManagement — toutes les briques Resilience4j alignées sur la
même version. **Leçon :** ne jamais pinner un artefact isolé d'une famille
qui publie un BOM.

**Vérification.** **83 tests verts** (build complet sur PostgreSQL
Testcontainers) dont 15 nouveaux : orchestration (verdict appliqué,
indisponibilité silencieuse à l'ingestion mais signalée à la demande),
déterminisme du stub, E2E simulation (le webhook répond AVANT le verdict,
verdict asynchrone appliqué, RBAC VIEWER → 403), et **WireMock rejouant le
contrat OpenAPI** : verdict du service appliqué (0.87/TRUE_POSITIVE),
X-API-Key transmis, payload conforme, service down → 503 + alerte intacte.

---

## 2026-07-17 — Le gate Trivy bloque le merge : la chaîne DevSecOps fait son travail (PR #44)

**Difficulté.** CI de la PR #44 verte partout (build, 83 tests, SonarCloud,
CodeQL) **sauf le gate Trivy** : l'image Docker est refusée pour une
vulnérabilité CRITICAL. Diagnostic mené en local (scan Trivy du fat jar,
scan de l'image de base, `dependency:tree`) :

- **CVE-2025-14813** (CRITICAL, CVSS 4.0 = 9.3) — `bcprov-jdk18on:1.80`
  (Bouncy Castle), faille CWE-327 dans le chiffrement GOST ; arrivé en
  transitif par `spring-cloud-starter-openfeign` → `spring-cloud-starter`
  (support du chiffrement de configuration `encrypt.*`, inutilisé) ;
- **CVE-2025-48976** (HIGH, CVSS 7.5) — `commons-fileupload:1.5`, DoS
  multipart ; transitif via `feign-form-spring` (multipart Feign, inutilisé
  — nos clients IA n'échangent que du JSON) ;
- l'image de base `eclipse-temurin:21-jre-alpine` : **0 CRITICAL** sur les
  73 paquets OS — le problème venait bien des jars introduits par la PR,
  pas d'un CVE préexistant sur `develop`.

**Solution retenue : réduire la surface d'attaque plutôt que patcher.**
Les deux bibliothèques n'apportant aucune fonctionnalité à SmartSOC,
exclusion Maven de `bcprov-jdk18on` et de `commons-fileupload` (versions
corrigées 1.80.2+/1.6.0 disponibles mais inutiles ici). Subtilité
découverte en re-testant : impossible d'exclure `feign-form-spring` en
entier — la classe `FeignClientsConfiguration` de Spring Cloud référence
son `SpringFormEncoder` à l'introspection du contexte ; seule sa
transitive `commons-fileupload` est exclue. **Ce sont les tests
d'intégration WireMock du mode live qui ont attrapé cette régression
immédiatement** — la preuve par l'exemple de leur valeur.

**Leçons.**
- Un starter Spring Cloud embarque des capacités (crypto de config,
  multipart) qu'on n'a pas demandées : les inventorier et retirer ce qui
  ne sert pas.
- Le gate « CRITICAL = merge bloqué » a fonctionné exactement comme conçu :
  la vulnérabilité n'a jamais atteint `develop`.

**Vérification.** `clean verify` : 83 tests verts (WireMock inclus) ;
`dependency:tree` : plus aucune occurrence des deux artefacts ; fat jar :
0 fichier `bcprov*`/`commons-fileupload*` dans `BOOT-INF/lib` ; re-scan
Trivy local puis CI complète sur la PR.

---

## 2026-07-18 — Jalon Investigations V1 — backend complet (PR #45)

**Contexte.** Réorientation validée en équipe : les modules métier du SOC
(investigations, actifs, CTI, MITRE, hunting, SOAR, rapports) sont
développés AVANT l'assistant IA conversationnel, qui viendra en dernier
pour exploiter toutes les capacités de la plateforme via des contrats
stables, sans retouche à chaque nouveau module.

**Réalisé.** Le contexte `investigations` de bout en bout (domaine →
migration V5 → adaptateurs → service → API), avec `Case` comme entité
métier interne (vocabulaire TheHive) et « Investigations » comme nom
fonctionnel. Trois décisions structurantes validées en conception :
- **Cycle `OPEN → IN_PROGRESS → CLOSED`, CLOSED strictement terminal** :
  pas de réouverture — la reprise d'enquête passe par un **cas de suivi**
  (`openFollowUp`) référençant l'origine (`origin_case_id`
  auto-référencé), qui doit être CLOSED. Traçabilité d'audit : un dossier
  clôturé n'est jamais modifié.
- **Clôture = acte formel** : endpoint dédié `/close`, conclusion
  obligatoire (interdit aussi par `transitionTo(CLOSED)`), et contrainte
  SQL `ck_cases_closure` — la règle est gravée dans la base, pas
  seulement dans le code.
- **Timeline propre au cas** (14 types d'événements, de CREATED à
  FOLLOW_UP_OPENED) : chaque action d'API inscrit sa trace avec
  l'analyste authentifié — matière des audits, des rapports et des
  futurs tools de l'assistant.

Le cas regroupe **incidents ET alertes** (liaisons N↔N idempotentes,
`ON CONFLICT DO NOTHING`) et porte une **checklist** de tâches
(TODO/IN_PROGRESS/DONE, `completedAt` tracé). Référence `CASE-YYYY-NNNN`
par séquence PostgreSQL. La garde d'immutabilité du cas clôturé est
étendue par le service aux opérations satellites (liaisons, tâches,
notes) que l'entité ne peut pas protéger. API `/api/v1/investigations` :
16 endpoints, RBAC identique aux incidents, réutilisation des contrats
`IncidentResponse`/`AlertResponse` existants.

**Méthode.** Travail découpé en 8 lots de 1 à 5 fichiers, chacun revu et
validé avant le suivant — revue humaine complète de chaque couche.

**Difficulté : le gate SonarCloud bloque la PR.** Première CI presque
verte, mais quality gate en échec : **75,1 % de couverture sur le
nouveau code, seuil 80 %**. L'analyse fichier par fichier (API
SonarCloud) a localisé les manques : traces d'assignation/statut/
liaisons de `CaseService` (39 lignes), branches rename/assign de
`CaseTask`, 4 endpoints de liaison du controller jamais appelés, énum
`CaseEventType` non exercée. **Solution :** un commit de tests
uniquement (aucun code de production modifié) — dont un test qui
verrouille le **vocabulaire d'audit à 14 événements** (tout ajout/
retrait devient un choix explicite, aligné sur la contrainte SQL) —
couverture remontée à **96,6 %**, gate vert. **Leçon :** après Trivy
(PR #44), deuxième garde-fou de la chaîne DevSecOps qui bloque
réellement un merge — le seuil de 80 % n'est pas décoratif, il a forcé
la couverture des chemins d'API secondaires (désassignation,
déliaisons) qu'on aurait sinon livrés non testés.

**Vérification.** Domaine : 9 tests (immutabilité du cas clos testée
mutation par mutation). Application : 8 tests (types et auteurs des
traces capturés, follow-up tracé des deux côtés). Persistance : 7 tests
d'intégration sur PostgreSQL réel (V5 + `ddl-auto: validate`, contrainte
de clôture, liaisons idempotentes avec vraies FK, chaîne de suivi). API :
5 tests E2E (cycle complet avec timeline exacte dans l'ordre des faits,
422 sur cas clos, follow-up refusé avant clôture puis accepté, ouverture
depuis incident, RBAC VIEWER → 403).

---

## 2026-07-18 — Bug de fuseau horaire détecté par un run de nuit, invisible en CI UTC (PR #46)

**Difficulté.** Lors du `clean verify` du module Investigations lancé à
00:50 (heure locale, UTC+2), `AlertStatsIntegrationTest` — un test du
module alertes qui passait depuis des semaines — échoue : le bucket
« aujourd'hui » de la timeline 7 jours du dashboard est à 0 alors que
3 alertes viennent d'être ingérées.

**Diagnostic.** La fenêtre de 7 jours est construite en **UTC** côté
Java (`LocalDate.now(ZoneOffset.UTC)`), mais le SQL
`date_trunc('day', detected_at)` sur un `timestamptz` est évalué dans
le **fuseau de session PostgreSQL**, que le driver pgJDBC aligne sur
celui de la JVM (UTC+2 en local). Entre 22 h et minuit UTC, une alerte
« d'aujourd'hui UTC » est donc datée « demain » par le SQL — hors
fenêtre, comptée nulle part. Conséquences réelles : en production, la
courbe du dashboard aurait perdu les alertes du soir chaque nuit ; et
**la CI GitHub, dont les runners vivent en UTC (JVM UTC = session
UTC), ne pouvait par construction jamais détecter ce bug** — seul un
run local nocturne pouvait le révéler.

**Correctif** (une ligne) : `date_trunc('day', detected_at AT TIME
ZONE 'UTC')` — le bucket devient indépendant du fuseau de session,
aligné sur la fenêtre Java.

**Test de non-régression déterministe** (exigence de revue) : la CI
UTC doit désormais couvrir ce cas pour toujours. Le nouveau test force
un fuseau de session non-UTC sur CHAQUE connexion du pool via
`spring.datasource.hikari.connection-init-sql=SET TIME ZONE
'Europe/Paris'` (plus fiable que changer le fuseau de la JVM, fragile
avec le cache de contexte Spring), et ingère une alerte témoin datée
**hier 23:30 UTC** — toujours dans la fenêtre, toujours « demain » en
heure de Paris. Assertion par delta sur le bucket d'hier : immunisée
contre les alertes des autres tests, indépendante de l'heure réelle.

**Preuves (sorties conservées).** Sans correctif : nouveau test rouge
(`expected: 1L but was: 0L`) et ancien test rouge en conditions réelles
à 01:46 locale. Avec correctif : nouveau test vert, et l'ancien test
vert exécuté à **01:59:53 locale (23:59:53 UTC)** — dans les dernières
secondes de la fenêtre pathologique qui le faisait échouer 13 minutes
plus tôt.

**Leçons.**
- Un `timestamptz` ne porte pas de fuseau : toute fonction de date SQL
  le convertit dans le fuseau de session — expliciter `AT TIME ZONE`
  dès qu'un calcul de calendrier traverse la frontière Java/SQL.
- Une CI verte ne prouve que ce que son environnement exerce : les
  runners UTC masquaient structurellement le cas ; le test force
  désormais l'environnement pathologique au lieu de le subir.

---

## 2026-07-18 — Jalon Investigations V2 — module frontend (PR #47)

**Réalisé.** Le module Investigations de la console, miroir strict des
conventions du module Incidents (fonctions async sur le client axios
partagé + TanStack Query, jamais de nouveau paradigme de fetch) :
- `investigationsApi.ts` : types alignés champ par champ sur les DTOs
  backend (vérifié en réel contre l'API : les 11 clés de `Case`, le
  champ `investigation` du détail), `ALLOWED_TRANSITIONS` miroir du
  cycle de vie du domaine ;
- liste filtrée/paginée (statut, priorité), dialogue de création
  (réutilisé en mode « cas de suivi » avec titre pré-rempli) ;
- **tiroir de détail** : transitions (la clôture ne passe QUE par le
  dialogue à conclusion obligatoire, bouton désactivé si vide),
  checklist interactive (`completedAt` affiché), liaisons
  incidents/alertes avec déliaison, timeline des 14 événements avec
  auteur, **badge « Cas clôturé — immuable »** avec disparition de tous
  les contrôles d'écriture sur un cas CLOSED, chaîne origine ↔ suivis ;
- bouton **« Ouvrir un cas »** dans le tiroir d'incident (miroir de
  l'escalade alerte → incident) : crée le cas, le lie, redirige.

**Vérification E2E réelle** (navigateur → backend Docker reconstruit
avec l'API #45) : cycle complet prouvé par les séquences réseau —
création (201, référence `CASE-2026-0001` par la séquence V5),
transition, tâche créée puis cochée, note, timeline exacte
`CREATED → STATUS_CHANGED → TASK_ADDED → TASK_COMPLETED → NOTE_ADDED`
avec auteur ; clôture (conclusion vide refusée, 200 puis cas figé) ;
**test d'immuabilité en concurrence réelle** : cas clôturé par API dans
le dos de l'UI puis note envoyée depuis le tiroir périmé → 422
`CASE_CLOSED` affiché proprement (message du ProblemDetail, pas
d'erreur brute) ; follow-up 201 avec `originCaseId`, listé dans
`followUps` de l'origine avec trace `FOLLOW_UP_OPENED`. Console
vérifiée **à froid sur un onglet neuf : zéro erreur, zéro
avertissement**. 18 tests frontend (2 nouveaux, avec le piège des
libellés « Ouvert » / « Ouvert le » couvert par correspondance exacte).

**Difficultés débusquées par la vérification navigateur — deux bugs
qu'aucun test unitaire n'aurait vus :**
- *followUps périmés* : après l'ouverture d'un cas de suivi, le
  dialogue n'invalidait que la liste (`['investigations']`) — le tiroir
  du cas d'origine, resté monté, affichait un cache périmé sans le
  nouveau suivi ni la trace `FOLLOW_UP_OPENED`. **Solution :**
  invalider aussi le préfixe `['investigation']` (tous les détails).
  **Leçon :** une mutation doit invalider toutes les vues qui montrent
  la donnée, pas seulement celle qui a déclenché l'action.
- *`inputProps` fantôme* : la prop `inputProps` du Checkbox, retirée de
  l'API MUI v7, fuyait telle quelle vers le DOM (avertissement React).
  **Solution :** `slotProps={{ input: … }}`, l'API actuelle. **Leçon :**
  les exemples mémorisés d'une version antérieure d'une bibliothèque se
  périment ; seule la console du navigateur l'a signalé — d'où la
  valeur de la règle « console propre à froid prouvée, pas supposée »
  ajoutée à la revue de chaque lot frontend.

---

## 2026-07-18 — Jalon Actifs A1 — backend complet (PR #48)

**Réalisé.** Le contexte `assets` de bout en bout : l'inventaire des
actifs supervisés qui donne son contexte métier au SOC — quand
`srv-web-01` lève une alerte, l'analyste voit criticité, exposition et
propriétaire de la machine. Entité `Asset` (hostname **immuable et
normalisé** = clé de corrélation, cycle ACTIVE ⇄ DECOMMISSIONED sans
suppression physique, actif décommissionné en lecture seule), migration
**V6** avec les règles gravées en SQL
(`ck_assets_hostname_normalized`, `ck_assets_decommission`), API
`/api/v1/assets` (7 endpoints, RBAC habituel, endpoints d'état dédiés
`decommission`/`reactivate` sur le patron de `close`), et la
**corrélation alertes ↔ actifs** par hostname.

**Un bug de la famille « silencieuse » évité AVANT d'exister.** La revue
de conception a repéré que l'ingestion normalise `source` mais stocke le
`hostname` BRUT de l'outil SOC : une jointure en égalité stricte aurait
affiché « 0 alerte corrélée » sur tout actif dont les alertes arrivent
en majuscules ou avec espaces — même famille que le bug de fuseau
horaire (PR #46) : faux en silence, invisible en conditions de test
naïves. Traitement complet :
- jointure normalisée côté SQL (`lower(trim(hostname)) = :hostname`),
  mot pour mot le prédicat de l'**index fonctionnel**
  `ix_alerts_hostname_normalized` créé par V6 — preuve `EXPLAIN
  ANALYZE` sur 20 003 lignes : Bitmap Index Scan, 0,089 ms, les trois
  variantes brutes (`SRV-WEB-01  `, `srv-web-01`, ` Srv-Web-01`)
  rattachées ;
- le compteur de corrélation EST le total de la même requête paginée
  (même prédicat dans la countQuery) — aucun count séparé qui puisse
  diverger ;
- **piège `Locale.ROOT`** : `toLowerCase()` sans locale dépend de la
  JVM (en locale turque, le I divergerait du `lower()` SQL) —
  normalisation Java figée sur `Locale.ROOT` pour correspondre
  exactement au SQL. Même classe de bug environnemental que le fuseau.

**Choix de conception.**
- **`AssetCriticality` dédiée**, pas la `Severity` des alertes : la
  sévérité qualifie une détection, la criticité qualifie un bien —
  et INFO n'aurait aucun sens pour un actif. Le tri « criticité la
  plus haute d'abord » repose sur un CASE ordinal (l'ordre alphabétique
  mettrait LOW avant MEDIUM), avec la garde JPA sur la requête de
  comptage.
- **Limitation FQDN actée** : `srv-web-01` ≠ `srv-web-01.corp.local`,
  pas de rapprochement flou — en SOC, un faux rattachement est pire
  qu'une absence. Si le Wazuh réel mélange les formes, l'évolution
  sera une liste d'alias explicites par actif.
- **Conflit d'unicité en 409, typé** : nouvelle
  `DuplicateResourceException` de domaine (miroir de
  `ResourceNotFoundException`) mappée 409 Conflict RFC 9457 — un
  doublon de hostname n'est pas une violation de cycle de vie (422),
  et l'exception est réutilisable telle quelle par les futurs contextes.

**Vérification.** Preuve `EXPLAIN` (Index Scan, jamais de Seq Scan) ;
tests domaine (normalisation majuscules/espaces, immutabilité du
hostname, lecture seule du décommissionné), application (clé normalisée
transmise au port, 0 alerte sans erreur, doublon même à casse
différente y compris sous course), intégration API E2E sur PostgreSQL
réel : 201 puis **409 à casse différente** (corps RFC 9457 avec
`ASSET_ALREADY_EXISTS`), corrélation d'une alerte ingérée par le vrai
webhook avec `hostname` brut majuscules+espace, tri
CRITICAL → HIGH → MEDIUM → LOW sur données réelles, cycle
décommission/réactivation avec corrélation toujours lisible, RBAC
VIEWER → 403.

---

## 2026-07-19 — Jalon Actifs A2 — module frontend et corrélation visible (PR #49)

**Réalisé.** Le module Actifs de la console, en 6 lots revus : inventaire
filtré (recherche, type, criticité, exposition, statut ; tri serveur par
rang de criticité), dialogues d'enregistrement/édition (le hostname est
absent du type d'édition — `Omit<…, 'hostname'>` : l'immutabilité est
dans le compilateur), fiche d'actif avec cycle décommission/réactivation
(badge « Lecture seule », historique de corrélation préservé) et alertes
corrélées paginées dont le totalElements EST le compteur. Chips :
`CriticalityChip` dédiée mais couleurs de la palette partagée (un
CRITICAL se lit pareil partout), `ExposureChip` avec « Exposé Internet »
en chip pleine rouge — l'information qui fait réagir un analyste.
**Le 409 du backend devient un message métier** (« Un actif est déjà
inventorié pour ce hostname ») via le statut HTTP, prouvé en réel à
casse différente.

**La corrélation visible des deux côtés.** Côté actif : les alertes du
hostname (prouvé en réel : 1 alerte ingérée par le vrai webhook en
majuscules+espace + 3 alertes historiques du même hôte rattachées par la
jointure normalisée). Côté alerte : **enrichissement progressif** du
tiroir — un lookup `by-hostname` (valeur brute envoyée, normalisation
serveur, `encodeURIComponent` pour le chemin) fait apparaître une puce
cliquable colorée par criticité menant à `/assets?selected={id}`.
Décisions de comportement validées en revue :
- **le 404 est une information métier** (« aucun actif inventorié ») :
  retry conditionnel qui l'exclut, aucun message, console propre —
  prouvé sur onglet neuf (« No console logs », une seule requête) ;
- **pas de scintillement** : seule `data` est consommée (ni spinner ni
  placeholder), le tiroir d'alerte s'ouvre aussi vite qu'avant ;
- **lien profond robuste à froid** : `/assets?selected={id}` collé dans
  un onglet neuf ouvre le bon tiroir (état initial lu depuis l'URL) ;
  un id inexistant referme silencieusement le tiroir et nettoie l'URL
  (`replace`) — liste affichée, zéro erreur.

**Difficulté d'outillage.** Deux pièges d'encodage découverts en testant
réellement l'endpoint `by-hostname` : `URLEncoder` encode l'espace en
`+` (sémantique query string, pas chemin) et `TestRestTemplate`
ré-encode un chemin déjà encodé (`%20` → `%2520`) — résolus par `%20`
explicite et `URI.create()`. Côté navigateur, `encodeURIComponent` fait
le bon choix nativement — la valeur du miroir test-réel/client-réel.

**Vérification.** 20 tests frontend verts (2 nouveaux) ; preuves
navigateur en conditions réelles sur le backend Docker : enregistrement
201 + 409 à casse différente avec message dédié, corrélation (4),
cycle décommission/réactivation en séquence réseau, les trois scénarios
du lien alerte ↔ actif ci-dessus, console propre à froid sur onglet
neuf à chaque étape.

---

## 2026-07-20 — Jalon CTI C1 — référentiel des IOC, backend (PR en cours)

**Réalisé.** Le socle du contexte `intelligence` : l'entité `Indicator`,
son schéma (**V7**), son adaptateur de persistance et son service
applicatif. Le module donne au SOC sa mémoire du renseignement — quand
une alerte cite une adresse ou un hash, l'analyste doit savoir si cet
observable est déjà connu comme malveillant.

**Le point dur, trouvé avant d'écrire une ligne.** Une alerte SmartSOC ne
contient AUCUN observable : `Alert` porte `hostname`, `ruleId`,
`mitreTechniques` et `rawPayload`, mais ni IP, ni hash, ni domaine. Or un
IOC n'est que cela. Sans observable, « enrichissement des alertes par
IOC » n'a rien à corréler. Deux voies, une seule tenable :
- extraire les observables de `rawPayload` par expressions régulières —
  **rejeté** : dépendant du format de chaque outil, et une regex d'IP
  attrape aussi bien la version d'un agent qu'un identifiant de règle.
  C'est exactement la doctrine posée sur les actifs (FQDN) : *un faux
  rattachement est pire qu'une absence* ;
- **étendre le contrat d'ingestion** d'un champ `observables` optionnel
  et typé, déclaré par le producteur qui, lui, connaît son format.
Retenu : la seconde. Additive et rétrocompatible — les producteurs
actuels ne subissent aucune régression — et c'est la raison pour laquelle
l'enrichissement fait l'objet d'une PR distincte : il touche un contrat
public.

**Identité contre métadonnées.** La séparation structure toute l'entité :
l'**identité métier** est le couple (type, valeur normalisée), `final`,
jamais modifiée ; les **métadonnées CTI** (confiance, TLP, source, tags,
fenêtre de validité, dates d'observation) sont volatiles et rafraîchies à
chaque passage du flux. `refreshFrom()` vérifie l'identité avant tout et
lève `INDICATOR_IDENTITY_MISMATCH` : une observation qui ne porte pas
exactement le même couple parle d'un autre indicateur, et le signaler
vaut mieux qu'écraser silencieusement une clé de corrélation.

**Quatre bugs silencieux verrouillés par des tests.** Tous de la même
famille que le fuseau horaire (PR #46) et la casse du hostname (PR #48) :
ils ne lèvent aucune erreur, ils produisent « 0 IOC corrélé ».
- *Normalisation dépendante de la locale* : en locale turque,
  `"I".toLowerCase()` donne `ı` — la clé Java divergerait du `lower()`
  SQL. Test exécuté sous `Locale.setDefault("tr")`. La primitive
  `TextNormalization` (Locale.ROOT) est désormais partagée, et `Asset`
  y délègue au lieu de porter sa propre copie de la règle.
- *Notation défangée* : MISP et les analystes écrivent `1.2.3[.]4`,
  `hxxp://`, `contact[at]evil[.]com`. Stocké tel quel, un IOC défangé ne
  correspond à aucun observable réel — il n'alerte jamais. Le refangage
  est la première étape de la normalisation.
- *Identité réduite à la valeur* : la corrélation compare toujours le
  COUPLE (type, valeur). Prouvé jusqu'en base — `('IPV4','45.83.12.7')`
  et `('DOMAIN','45.83.12.7')` coexistent, un second `IPV4` identique est
  refusé par `ux_indicators_identity`.
- *Expiration* : `EXPIRED` n'est **pas** une colonne, il se déduit de
  `valid_until` à la lecture. Une colonne de statut exigerait un batch de
  péremption ; le jour où ce batch prend du retard, des IOC périmés
  continuent d'enrichir en se déclarant actifs. Déduire supprime le batch
  ET la classe de bug. Seule la révocation est un fait stocké — décision
  d'analyste, qui survit aux ré-observations du flux.

**Normalisation par type, avec ses exceptions assumées.** Un hash se met
en minuscules sans réserve (les exports MISP les sortent en majuscules) ;
une URL non — `/Login` et `/login` sont deux ressources, seuls le schéma
et l'hôte sont insensibles à la casse. L'IPv6 est canonicalisé
(`2001:DB8::1` et sa forme développée sont la même adresse et doivent
produire la même clé), via `InetAddress` mais **gardé par une regex de
littéral** pour qu'aucune résolution DNS ne soit possible depuis le
domaine. La contrainte SQL `ck_indicators_value_normalized` grave la
règle en base avec son exception URL explicite ; elle reste un plancher,
le domaine demeure l'autorité.

**Un décalage attrapé par la vérification, pas par la relecture.** Le
premier passage de Flyway V1→V7 avec `ddl-auto=validate` a échoué :
`wrong column type encountered in column [confidence] : found [int2
(SMALLINT)], but expecting [integer (INTEGER)]`. J'avais écrit `SMALLINT`
par réflexe d'économie alors que le domaine porte un `int`. Corrigé en
`INTEGER`, l'échelle 0-100 restant garantie par la contrainte, qui est le
vrai garde-fou.

**Recherche par tag : quand un index ne sert à rien (lot 3bis).** Le
filtre par tag doit être EXACT — un `like` sur le texte JSON rattacherait
le tag `c2` à `c2-proxy`. La forme fonction `jsonb_exists(tags, :tag)`
donne bien ce résultat exact, mais **n'emprunte jamais l'index GIN** :
PostgreSQL ne fait correspondre un index qu'à une expression d'OPÉRATEUR,
jamais à l'appel de fonction équivalent. Vérifié en forçant la main au
planificateur — avec `enable_seqscan = off`, le plan reste un Seq Scan
annoté `Disabled: true`, faute de toute alternative.

L'opérateur natif `?` de PostgreSQL est, lui, inutilisable via JDBC : le
caractère entre en conflit avec les paramètres liés — c'est précisément
pour cela que le driver documente `jsonb_exists` comme contournement, et
c'est ce contournement qui coûte l'index. Reste le containment `@>`, à la
fois exact et indexable, mais que l'API Criteria ne sait pas émettre.
Solution retenue : une fonction Hibernate enregistrée **par motif**.
Contrairement à `cb.function()` qui produit toujours une syntaxe d'appel
`nom(args)`, un motif est recopié tel quel dans le SQL — le moteur voit
une vraie expression d'opérateur et l'index redevient éligible.

Mesures sur 20 002 IOC, **même session, caches chauds, exécutions
alternées** :

```
AVANT — where jsonb_exists(tags, 'c2')
 Seq Scan on indicators (actual rows=1.00 loops=1)
   Filter: jsonb_exists(tags, 'c2'::text)
   Rows Removed by Filter: 20001
   Buffers: shared hit=397
 Execution Time: 3,129 ms  (répétitions : 3,409 / 3,602 ms)

APRÈS — where (tags @> cast('["c2"]' as jsonb)) = true
 Bitmap Heap Scan on indicators (actual rows=1.00 loops=1)
   Recheck Cond: (tags @> '["c2"]'::jsonb)
   Heap Blocks: exact=1
   Buffers: shared hit=53
   ->  Bitmap Index Scan on ix_indicators_tags (actual rows=1.00 loops=1)
         Index Cond: (tags @> '["c2"]'::jsonb)
 Execution Time: 1,160 ms  (répétitions : 1,105 / 0,916 ms)
```

**L'argument robuste n'est pas le temps** (≈ 3× à ce volume, sur une base
de test chargée en mémoire), **c'est le changement de plan** : la lecture
intégrale de la table disparaît. 20 001 lignes parcourues puis jetées →
0 ; 397 buffers → 53. C'est ce coût-là qui croît linéairement avec le
référentiel — un MISP abonné aux flux ouverts dépasse rapidement le
million d'IOC, où le Seq Scan est cinquante fois plus lourd tandis que le
parcours d'index ne bouge quasiment pas.

> **Note de méthode — pourquoi ces chiffres remplacent les premiers.**
> La première mesure annonçait 4,053 ms → 0,118 ms, soit un gain de 34×.
> Elle était fausse comme comparaison : les deux valeurs provenaient de
> **sessions différentes, avec des états de cache différents** — le
> « avant » lisait la table à froid, l'« après » profitait d'un index
> déjà chaud. Le protocole a été refait dans une session unique, caches
> chauds, en alternant les deux formes et en répétant chaque mesure.
> Le gain réel est plus modeste, et l'argument déplacé du temps vers le
> plan d'exécution et les buffers — deux métriques qui, elles, ne
> dépendent pas de l'état du cache. Un chiffre spectaculaire mais non
> reproductible n'a aucune valeur dans un rapport : mieux vaut un gain
> honnête et un raisonnement qui tient.

**Vérification.** Rendu SQL réellement émis par Hibernate, relevé dans
les logs : `where (tags @> cast('["c2"]' as jsonb)) and 1=1` — Hibernate
absorbe même le `= true`. Exactitude confirmée (`c2` ne remonte pas
`c2-proxy`). Le tag finissant en **littéral SQL inliné** et non en
paramètre lié, une charge hostile a été testée (`x') = true or 1=1 --`) :
l'apostrophe est doublée par Hibernate, la charge reste enfermée dans la
chaîne JSON, 0 résultat — pas d'injection. Les sept contraintes de V7 ont
été exercées une à une sur PostgreSQL réel (majuscules refusées hors URL,
URL à chemin capitalisé acceptée, doublon d'identité refusé, même valeur
sous un autre type acceptée, révocation sans motif refusée, `last_seen`
antérieur refusé). Tests : 50 domaine, 31 application, 8 infrastructure,
plus l'ArchUnit qui confirme que le domaine reste sans framework —
`java.net`, utilisé pour la canonicalisation IPv6, n'y est pas interdit.

---

*Prochaines entrées : CTI (API et enrichissement, puis frontend), MITRE,
hunting, SOAR, rapports, puis assistant IA (backend + frontend).*
