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

## 2026-07-20 — Jalon CTI C1 — référentiel des IOC, backend (PR #50, ADR-009)

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

### Durcissement après revue SonarCloud (même jalon)

La première analyse de la PR a bloqué sur **`java:S5998`** (MAJOR, typée
BUG) : l'expression de validation des domaines répétait un GROUPE, or le
moteur d'expressions régulières de Java récurse à chaque répétition. Un
nom à un millier de labels faisait **déborder la pile** — et la valeur
arrive d'un flux CTI externe, jusqu'à 2048 caractères. C'était un déni de
service offert au producteur du flux.

Le correctif appliqué, le gate est repassé au vert — **et c'est ce vert
qui a failli clore l'affaire trop tôt**. En ouvrant la liste complète des
issues, deux **`java:S8786`** subsistaient : `IPV6_LITERAL` et
`EMAIL_FORMAT` rétro-suivaient de façon super-linéaire sur les mêmes
données externes. Elles ne bloquaient pas parce que Sonar les classe en
CODE_SMELL, et que `new_reliability_rating` ne compte que les BUG.

**Décision : les corriger malgré le gate vert.** La classification d'un
outil décrit une forme, pas un contexte. Ces motifs s'appliquent à
`indicators.value`, fournie par un tiers via un webhook public — ce que
l'outil ne peut pas savoir. Corollaire de méthode : la même cause avait
produit un BUG *et* deux CODE_SMELL ; ne traiter que le BUG, c'était
corriger un tiers d'un défaut unique.

| Avant | Après |
| --- | --- |
| groupe répété `(?:…)+` → récursion, débordement de pile | boucle sur les labels, pile constante |
| `[0-9a-f:]*:[0-9a-f:]*` → rétro-suivi super-linéaire | parcours caractère par caractère, un seul passage |
| `[^@\s]+@[^@\s]+\.[^@\s]+` → rétro-suivi | découpage sur `@`, puis `isValidDomain()` |
| aucune borne de taille dans le domaine | 2048 par valeur, 253 par domaine, 45 par IPv6 |

Trois acquis : plus aucune expression à rétro-suivi non borné sur une
donnée externe (temps de validation prévisible quelle que soit
l'entrée) ; les gardes de longueur vivent **dans le domaine**, donc tout
futur appelant en hérite ; et le domaine d'une adresse e-mail est validé
par `isValidDomain()` — une seule règle, deux implémentations ne peuvent
plus diverger. Un IOC hostile est rejeté en **temps linéaire**.

Les 35 *code smells* de tests ont été traités dans la foulée (`S5778` :
les arguments des lambdas `assertThatThrownBy` sont construits avant la
lambda, pour qu'un test ne puisse pas passer au vert parce que le
*builder* a levé ; `S5838`, `S9024`, `S6068`, `S1192`). **Aucune
exclusion, aucun `NOSONAR`** : zéro issue introduite par la PR au final.

**Deux checks vérifiés plutôt que crus sur parole.** Gitleaks a échoué
deux fois sur un `503` de l'API GitHub — l'outil n'avait jamais scanné.
Un `503` n'est pas une preuve d'absence de fuite : scan rejoué en local
avec l'image officielle sur **toutes les refs**, 77 commits (le total
sans les 40 commits de merge, qui n'introduisent aucun contenu),
`no leaks found`. Le check GitHub est repassé au vert ensuite,
confirmant l'incident de plateforme. Le check `Trivy` affichait
`neutral` (« 2 configurations not found ») : lecture du journal du job
faite — l'image est bien construite et scannée, la barrière sur les
CRITICAL passe, le SARIF est envoyé et traité ; le `neutral` ne porte
que sur le récapitulatif agrégé de GitHub Code Scanning.

**CI finale** : Build & Test, Quality analysis, SonarCloud (gate OK,
couverture du code nouveau **84,8 %**, duplication 0 %, 0 issue),
CodeQL (« no new alerts »), Trivy, Gitleaks, dependency-review, sanity
check — tous au vert.

---

## 2026-07-20 — Jalon CTI C2 — observables d'alerte et enrichissement (PR #55)

**Réalisé.** Le chaînon qui donne sa valeur au référentiel : les alertes
déclarent désormais leurs **observables** (IP, domaine, URL, hash), et la
plateforme les rapproche du référentiel IOC **dans les deux sens** —
alerte → IOC pour le triage, IOC → alertes pour le retro-hunt.

**Une seule normalisation, appelée et non recopiée.** `Observable` vit
dans le contexte `intelligence`, avec `IndicatorType`, et sa valeur est
produite par `IndicatorType.normalize()` — le même code exactement que
pour un IOC. Le test central ne fige aucune chaîne attendue : il
**compare les deux chemins** type par type
(`Observable.of(t, v).value()` contre `t.normalize(v)`). Une seconde
implémentation qui apparaîtrait un jour casserait le test immédiatement,
là où une assertion sur `"evil.com"` ne l'aurait pas vue.

**Tolérance par élément, jusque dans le contrat.** Un observable mal
formé est écarté **individuellement** et l'alerte est créée quand même :
perdre une détection à cause d'un champ annexe serait un très mauvais
échange. Le producteur reçoit un `observableReport` avec ses deux
compteurs et le détail par entrée. Le champ **`code` est contractuel et
stable** (`INVALID_SHA256`, `TOO_MANY_OBSERVABLES`…), dérivé du type
annoncé ; le `message` reste informatif — un producteur qui l'analyserait
se lierait à une formulation plutôt qu'à une règle. Un test parcourt les
huit valeurs de l'énumération pour que la convention tienne même si un
type est ajouté plus tard.

**La règle métier vit dans le SQL, pas après.** Le filtre d'activité
(`revoked = false AND (valid_until IS NULL OR valid_until > :at)`) est
dans la requête ; l'adaptateur ne fait qu'un `map(mapper::toDomain)`,
sans `filter` ni `removeIf`. Il n'y a rien à oublier de filtrer parce
qu'il n'y a rien à filtrer. Prouvé sur 20 003 IOC : les trois couples
`(type, valeur)` de l'alerte témoin correspondent (requête témoin sans
filtre : 3 lignes) mais la requête complète n'en rend **qu'une**.

**Cohérence temporelle garantie par le type.** L'enrichissement est
calculé à la lecture ; un indicateur pourrait donc expirer *pendant* le
traitement d'une requête et se retrouver actif pour le SQL puis périmé
dans la réponse. La parade retenue n'est pas une convention de passage
de paramètre : `ThreatIntelEnrichment` **transporte** l'instant qui a
servi au filtre SQL, et `ThreatIntelApiMapper` n'accepte **aucun**
paramètre `Instant` — son unique source est celui que porte
l'enrichissement. Il n'existe pas de signature permettant de calculer un
statut à un autre moment.

> **Une règle ArchUnit aurait été le mauvais outil.** Interdire
> `Instant.now()` dans la couche API paraissait plus rigoureux, mais
> c'était faux : les horodatages RFC 9457 du gestionnaire d'erreurs et
> les TTL de jetons en ont un besoin légitime. Une règle qui casse du
> code sain pour protéger un cas particulier donne une impression de
> rigueur sans en avoir la substance. Le typage, lui, s'applique
> exactement là où le risque existe.

**Choix de persistance, et deux corrections venues de la mesure.** Table
dédiée `alert_observables` (V8) plutôt que JSONB — contrairement aux
techniques MITRE, on **joint** sur ces valeurs. Clé primaire
`(alert_id, type, value)` qui dédoublonne en base, contrainte de
normalisation identique à `indicators.value` (exception URL comprise), et
index dédié `(type, value)` : la clé primaire commence par `alert_id` et
n'aurait pas servi le sens IOC → alertes.
- **LAZY → EAGER** : l'adaptateur convertit l'entité en objet de domaine
  dès la sortie du dépôt, donc la collection est toujours parcourue ; en
  LAZY, tout appelant hors transaction levait une
  `LazyInitializationException`. Compter sur « il y a toujours une
  transaction » était une hypothèse fragile — les trois tests de
  persistance l'ont démentie.
- **`@Fetch(SUBSELECT)` mesuré plutôt qu'affirmé** : sur une requête de
  liste, Hibernate émet
  `select … from alert_observables where alert_id in (select id from alerts where …)`,
  soit **une** requête complémentaire pour toute la page au lieu d'une
  par alerte. Le N+1 sur l'écran le plus consulté du SOC est écarté.

**Plans d'exécution.** Sens alerte → IOC : les couples cherchés sont
recomposés en table par `unnest` de deux tableaux parallèles, si bien que
le moteur voit une jointure ordinaire — `Index Scan using
ux_indicators_identity`, 12 buffers, 0,061 ms sur 20 003 IOC. Une longue
disjonction de `OR`, la formulation naïve, aurait dégénéré dès qu'une
alerte cite beaucoup d'observables. Sens IOC → alertes : `Index Scan
using ix_alert_observables_identity` pour la liste **et** pour la
`countQuery`, qui reprend le même prédicat — le total de la page est le
compteur, il ne peut pas diverger.

**Un trou de robustesse trouvé sur mon propre travail.** Le tableau
`observables` du payload n'était borné qu'**après** désérialisation, par
la règle métier de 100. Un producteur pouvait donc envoyer un million
d'entrées que Jackson aurait intégralement matérialisées. Même classe de
problème que les expressions régulières non bornées de CTI-1 : une
donnée externe sans plafond. Corrigé par deux bornes de **natures
différentes** — 100 (métier, tolérance, l'alerte est conservée) et 1000
(anti-abus, `400` avant désérialisation). L'écart entre les deux est
intentionnel : un dépassement ordinaire ne doit jamais faire perdre une
alerte.

**Une intuition démentie par la mesure.** Je soupçonnais qu'une simple
transition de triage provoquerait un `DELETE` + `INSERT` complet de la
collection d'observables. Les requêtes réellement émises disent le
contraire : seul `update alerts` part, `alert_observables` n'est pas
touchée. La raison tient au `equals`/`hashCode` de l'embeddable, mis en
place pour le dédoublonnage du `Set` — Hibernate s'en sert pour constater
que la collection n'a pas changé. Un choix fait pour une raison en a
réglé une autre ; rien à corriger.

**Vérification.** 7 tests E2E sur PostgreSQL réel : tolérance avec code
de rejet typé, rejeu qui resurface l'erreur de mapping, alerte **sans**
observable strictement inchangée (ingestion, consultation, et
enrichissement à `200` avec deux listes vides), corrélation ne rendant
que l'IOC actif alors que le périmé et le révoqué correspondent aussi,
les observables sans correspondance conservés dans la réponse,
retro-hunt sur trois alertes antérieures à l'IOC, pagination, et
historique toujours consultable après révocation. Les **15 tests
d'intégration d'alertes existants passent sans modification** — la
preuve directe qu'un producteur d'avant CTI-2 est intact.

*Incident de test à noter : la première version du contrôle « aucun effet
de bord » comparait l'alerte entière avant et après. Elle échouait — non
pas à cause de l'enrichissement, mais parce que le **classifieur IA
écrit de façon asynchrone** après l'ingestion (jalon IA). Le test compare
désormais les champs stables, en excluant explicitement `aiScore` et
`aiVerdict` avec la raison écrite dans le code.*

---

## 2026-07-21 — Jalon CTI C3 — module Threat Intelligence, frontend (PR #56)

**Réalisé.** La console expose enfin le référentiel CTI construit en
CTI-1/CTI-2 : liste filtrée des IOC, tiroir de détail avec révocation et
**retro-hunt**, déclaration manuelle, et surtout l'**enrichissement CTI
du tiroir d'alerte** — le moment où le renseignement devient visible pour
l'analyste. La route `/intelligence` remplace son `PageStub`.

**Miroir strict du module Actifs** (axios partagé + TanStack Query, jamais
RTK Query) : liste paginée côté serveur, tiroir, dialogues, chips
dédiées. Six filtres propres au CTI (recherche, type, statut, source,
tag, confiance minimale), tous appliqués **par le serveur**.

**Le statut n'est jamais recalculé côté navigateur.** `ACTIVE / EXPIRED /
REVOKED` est déduit côté backend de `validUntil` à un instant que le
serveur fixe ; le front l'affiche tel quel. Re-dériver l'expiration en
JavaScript recréerait exactement le décalage temporel que CTI-2 avait
fermé par le typage (`ThreatIntelEnrichment` transporte son
`evaluatedAt`). Le test Vitest fige ce contrat : il fournit des statuts
dans les données simulées, sans aucune date à interpréter.

**« Feeds » = une colonne et un filtre, pas une entité gérée.** Les flux
poussent par webhook (ADR-005) ; la console montre *d'où vient* chaque
IOC (`feedSource`) et permet de filtrer dessus. Il n'y a délibérément
aucun écran de « connexions MISP » à administrer.

**Enrichissement du tiroir d'alerte.** Chaque observable cité devient une
puce ; celles qui correspondent à un indicateur **ACTIF** sont rouges et
cliquables vers `/intelligence?selected={id}`, les autres restent
discrètes. La correspondance se fait sur le COUPLE (type, valeur), déjà
normalisé des deux côtés par le serveur. Au passage, le type `Alert` du
front portait un contrat incomplet : il lui manquait `observables` depuis
CTI-2 — corrigé.

**Une régression attrapée par le build, pas par la relecture.** Rendre
`observables` obligatoire sur le type `Alert` cassait les deux fixtures
d'`AlertsPage.test.tsx`. Le `tsc --noEmit` isolé ne les incluait pas ;
c'est le **build de production** (qui compile les tests) qui l'a vu. Le
commit du lot a été amendé avant d'être poussé — pas de commit cassé dans
l'historique.

**Vérification E2E réelle, contre le backend CTI reconstruit** (Docker
sur `develop`, 6 IOC de test + alertes ingérées par le webhook) :
connexion, déclaration d'un IOC, **normalisation** (`"  EVIL-DEMO[.]COM. "`
→ `evil-demo.com`), **409** sur doublon écrit différemment avec message
métier, affichage en liste, ouverture du tiroir, **révocation à motif
obligatoire** (bouton de confirmation inactif tant que le motif est vide,
puis statut `Révoqué` + `Lecture seule` + motif horodaté), **retro-hunt**
(l'IOC montre l'alerte qui le cite — y compris sur un indicateur
révoqué), **enrichissement** (3 observables, 1 seul match rouge cliquable
car les deux autres sont l'un révoqué et l'autre inconnu), **lien
profond** `?selected=` qui ouvre le tiroir même à froid après
rechargement, et **console propre** à chaque étape.

**Filtres prouvés jusqu'au bout de la pile** : `?status=ACTIVE` réduit la
liste à 4 sur 6 ; `?tag=c2` ne rend qu'un seul IOC — ce qui exerce la
recherche JSONB `@>` et son index GIN construits en CTI-1, et confirme
que `c2` n'attrape pas `cobalt-strike`.

> **Incident de méthode — automatisation du navigateur.** Le clic
> automatisé sur le bouton de connexion ne déclenchait pas le
> gestionnaire `onSubmit` de React, alors que les valeurs du DOM étaient
> correctes et le bouton actif. Diagnostic : l'outil d'automatisation ne
> propageait pas l'état contrôlé de React — **pas un défaut de
> l'application**. Le login a donc été effectué en pilotant les vrais
> événements `input` puis le vrai `onSubmit` du formulaire : credentials
> réels, `POST /auth/login → 200`, jeton réel. L'authentification est
> donc bien testée ; seul le déclencheur du clic a contourné la
> limitation de l'outil.

> **Faux positif de diagnostic, tranché par la donnée.** Une première
> alerte de test ne remontait que 2 observables sur 3. Avant de conclure
> à un bug, vérification en base : le volume PostgreSQL avait survécu au
> rebuild et l'alerte **existait déjà** — l'ingestion était un **rejeu
> idempotent**, qui rapporte ce que le payload contenait sans réécrire
> les observables stockés. Comportement CTI-2 voulu et déjà testé. Une
> ré-ingestion avec un `externalId` neuf a rendu `201` et les 3
> observables. Ni bug frontend, ni bug backend : jeu de données
> contaminé.

**Vérification finale.** Prettier, Oxlint, `tsc` (exit 0), **Vitest 22
tests / 9 fichiers**, build de production — tous verts.

---

## 2026-07-23 — Durcissement fiabilité : Quality Gate SonarCloud de develop (PR #57)

**Contexte.** Après le merge de CTI-3, le Quality Gate de `develop` est
passé au ROUGE — non sur la maintenabilité (A) ni la duplication (0,2 %),
mais sur **`new_reliability_rating = 3`**, six bugs de deux familles.

**`java:S2259` ×4 — NPE potentiels dans `IndicatorType`.** Les quatre
normaliseurs (IPv6, domaine, hash, e-mail) déréférençaient le résultat de
`TextNormalization.lowerTrim()`, déclarée nullable. Inatteignable en
pratique (le point d'entrée `normalize()` rejette le null en amont), mais
la sûreté était **conventionnelle, pas structurelle** — un futur appel
direct à une méthode privée l'aurait cassée. Ajout de
`lowerTrimRequired()` (précondition explicite via `requireNonNull`) ;
échec immédiat et nommé plutôt qu'un NPE au fond d'une expression
régulière.

**`java:S8688` ×2 — fuseau des références CASE/INC** (la dette différée
après CTI-2). `Year.now()` sans fuseau suivait celui de la JVM : une
référence ouverte le 31 décembre à 23h30 UTC portait une année différente
selon la machine — exactement la classe du décalage des statistiques
d'alertes corrigé en PR #46. Correction par **horloge UTC injectée**
(nouveau bean `ClockConfig`), qui rend le comportement à la fois correct
et **testable** — ce que `Year.now()` en dur ne permettait pas.

**Tests de non-régression.** `TextNormalizationTest` (indépendance à la
locale turque, tolérance au null vs échec rapide) ; et surtout
`ReferenceGeneratorYearRolloverTest`, qui fige l'horloge au 31/12 23h30
UTC **et force le fuseau JVM à Europe/Paris puis Asia/Tokyo** : la
référence reste sur l'année UTC (`CASE-2026`, `INC-2026`), là où la
version buggée aurait produit 2027.

Aucun changement de comportement fonctionnel — uniquement la sûreté et le
fuseau. Leçon transverse : un Quality Gate rouge après merge se lit
d'abord par **métrique** (ici la fiabilité, pas la maintenabilité qu'on
soupçonnait), puis par issue.

---

## 2026-07-24 — Jalon MITRE M1 — référentiel ATT&CK, backend (PR #59, ADR-010)

**Contexte.** Les alertes portaient déjà des techniques ATT&CK
(`Alert.mitreTechniques`, en JSONB depuis V3) mais **aveugles** : aucun
nom, aucune tactique, aucune dépréciation connue — juste des chaînes
`"T1059"` pointant en aveugle vers `attack.mitre.org`. Ce jalon construit
le **référentiel** qui leur donne enfin un sens. C'est le jumeau structurel
de CTI : ADR-010 reprend le patron d'ADR-009 (identité immuable, upsert
tolérant, JSONB `@>`) et note, à chaque écart, *pourquoi* MITRE diffère.

**Réalisé (backend complet, 7 lots reviewables validés un à un).**
Domaine → persistance V9 → cas d'usage → semis → API :
- *Domaine* : `enum MitreTactic` (14 tactiques, **verrouillées par test**
  dans l'ordre des colonnes), entité `MitreTechnique` (identité `attackId`
  immuable + métadonnées rafraîchies + `parentId` de sous-technique),
  `MitreTechniqueId.normalize()` (validation **caractère par caractère,
  sans regex à rétro-suivi** — leçon S8786), port `MitreCatalogRepository`.
- *Persistance* : **V9** `mitre_technique_catalog`, entité JPA (tactiques en
  JSONB), mapper MapStruct, adaptateur de recherche.
- *Application* : `MitreCatalogService` (upsert par identité, consultation)
  + `MitreCatalogImportService` (**lot tolérant par élément, bean séparé
  non transactionnel** — même doctrine que le flux CTI).
- *Semis* : `ApplicationRunner` idempotent chargeant une **ressource ATT&CK
  Enterprise embarquée versionnée** (25 techniques, 14 tactiques, 4
  sous-techniques, 1 dépréciée) — plateforme démontrable seule.
- *API* : `GET /api/v1/mitre/{tactics,techniques,techniques/{id}}` +
  **`POST /import` réservé ADMIN**.

**Choix structurants** (justifications complètes dans ADR-010).
- **La dépréciation SUIT l'import**, elle ne survit pas au flux — l'écart
  notable avec CTI. La révocation d'un IOC est une décision d'analyste qui
  prime sur le flux ; la dépréciation d'une technique est un **fait DU
  référentiel ATT&CK**, donc l'import fait foi. Ce qui « survit » est la
  non-suppression : une technique dépréciée reste consultable.
- **Tactiques en enum, JSONB pour le stockage** : les 14 tactiques
  Enterprise sont un vocabulaire fini et stable (un enum, comme `Severity`),
  et les tactiques d'une technique sont filtrées par containment `@>` en
  **réutilisant le `JsonbFunctionContributor` déjà construit pour les tags
  d'IOC** — aucune infrastructure neuve.
- **Alimentation hybride** : semis embarqué (démoable seule, doctrine
  « simulation par défaut ») + import **admin JWT** — écart assumé avec le
  webhook `X-API-Key` de CTI, parce que rafraîchir un référentiel est un
  acte de gestion rare, pas un flux SOC continu.
- **Table `mitre_technique_catalog`**, nommée distinctement de la colonne
  `alerts.mitre_techniques` à laquelle elle donne un sens (la corrélation,
  reportée en **PR-2**, lira ce JSONB sans retoucher le domaine des alertes,
  ADR-010 §6).

**Points notables.**
- La sérialisation JSONB d'un `List<MitreTactic>` (enum) par
  Hibernate/Jackson a été **prouvée par l'aller-retour Testcontainers** — le
  choix de garder les types forts jusqu'en base tenait, validé plutôt que
  supposé.
- Tests d'intégration rendus **déterministes** : semis coupé
  (`smartsoc.mitre.seed-on-startup=false`) sur le test de persistance pour
  un catalogue vide, actif sur le test de semis dédié.
- **ArchUnit a validé** que les nouveaux packages `mitre` respectent les
  frontières de couches — garde-fou machine, pas relecture humaine.

**Vérification.** 28 tests MITRE (13 domaine + 6 application + 9 API sur
PostgreSQL réel), **suite complète 226 tests verts** sur les 4 modules
Maven. Flyway applique **V9 sur chaque base Testcontainers neuve** (aucune
régression des 8 migrations existantes). Semis vérifié de bout en bout :
log `Seeded MITRE ATT&CK catalog (v16.1): 25 created, 0 rejected`.

**Repli de journal.** Correction, dans ce jalon (pas de PR journal seule),
du numéro de PR des jalons **CTI C2 (→ #55)** et **CTI C3 (→ #56)**, restés
à tort « (PR en cours) ».

---

## 2026-07-25 — Jalon MITRE M2 — corrélation alerte ↔ ATT&CK, backend (PR #60)

**Contexte.** M1 a construit le référentiel ; M2 lui donne sa valeur en le
reliant aux alertes **dans les deux sens**, en lisant le JSONB
`alerts.mitre_techniques` déjà présent — **sans retoucher le domaine des
alertes** (ADR-010 §6). Trois capacités : enrichissement alerte→technique,
retro-hunt technique→alertes, et heatmap de couverture.

**Décision de conception validée en équipe : hypothèse d'identifiant
canonique.** Les alertes stockent leurs techniques en chaînes BRUTES
(`Alert.ingest` fait un `List.copyOf`, jamais de normalisation) ; le
catalogue, lui, stocke des `attackId` canoniques majuscules. Trois options
ont été pesées (comparaison `@>` exacte indexée GIN ; normalisation à
l'ingestion + réécriture des lignes ; index GIN fonctionnel normalisé).
Retenue : **`@>` exact + index GIN**, en assumant que les outils SOC
(Wazuh, Suricata, exports ATT&CK) émettent des identifiants canoniques
`T####` — un rapprochement **explicite plutôt que flou**, exactement la
doctrine de la limitation FQDN des actifs. Zéro changement au domaine des
alertes, ADR-010 §6 tenu. L'enrichissement, lui, normalise côté Java :
il reste robuste à la casse même sur une alerte non canonique.

**Réalisé (4 lots reviewables, backend).**
- **V10** : index **GIN** sur `alerts.mitre_techniques` — l'UNIQUE point de
  contact du contexte `mitre` avec la table `alerts`.
- **Retro** `AlertRepository.findByMitreTechnique` : requête native
  `mitre_techniques @> cast(:t as jsonb)` + `countQuery` identique (le total
  de la page EST le compteur), jumelle de `findByObservable` de CTI-2.
- **Couverture** `AlertRepository.mitreCoverage` : agrégation
  `jsonb_array_elements_text` groupée par technique — même esprit que les
  statistiques du dashboard, lecture pure du JSONB.
- **`MitreCorrelationService`** : enrichissement (résout les IDs bruts
  contre le catalogue, **les inconnus — hors format ou absents — restent
  VISIBLES**, jamais masqués, comme un observable sans correspondance),
  retro (normalise avant la requête), couverture. Corrélation **calculée à
  la lecture** : une technique cataloguée après coup enrichit les alertes
  existantes, une alerte d'hier remonte pour une technique consultée
  aujourd'hui — aucun rattrapage.
- **API** : `GET /api/v1/alerts/{id}/mitre` (enrichissement, à côté de
  `/threat-intel`), `GET /api/v1/mitre/techniques/{attackId}/alerts`
  (retro), `GET /api/v1/mitre/coverage` (heatmap) — lecture pour tout
  authentifié.

**Preuve mesurée (exigence de revue).** Un test force
`enable_seqscan = off` puis lit le plan `EXPLAIN` de
`mitre_techniques @> '["T1059"]'::jsonb` : il montre un **Bitmap Index Scan
sur `ix_alerts_mitre_techniques`**, et non un Seq Scan désactivé — le GIN
sert bien le containment (même méthode que la mesure de l'index de tags CTI).

**Vérification.** 10 tests MITRE-2 (retro + total + preuve EXPLAIN,
couverture, service, E2E enrichissement/retro/couverture avec lecture
VIEWER) ; **suite complète verte** (module api 103, ArchUnit et Flyway V10
inclus). Pas de nouvel ADR : la corrélation est l'implémentation d'ADR-010
§6, déjà décidée.

---

## 2026-07-25 — Jalon MITRE M3 — module frontend (matrice, tiroir, enrichissement) (PR #61)

**Contexte.** Le backend MITRE (M1 catalogue + M2 corrélation) est complet ;
ce jalon l'expose dans la console, en miroir strict du module
`intelligence` — axios partagé + TanStack Query, **jamais RTK Query**.

**Réalisé (5 lots reviewables).**
- `mitreApi.ts` (client typé : tactiques, techniques, couverture, retro,
  enrichissement) + `mitreChips.tsx`.
- `MitrePage.tsx` : la matrice ATT&CK, 14 tactiques en colonnes, techniques
  colorées par couverture (`GET /techniques` × `GET /coverage`, jointure
  côté client) ; remplace le `PageStub` de `/mitre`.
- `TechniqueDetailDrawer.tsx` : métadonnées (tactiques, sous-technique,
  dépréciation, lien attack.mitre.org) + **alertes corrélées paginées**
  (retro-hunt), lien profond `?selected=`.
- Enrichissement d'`AlertDetailDrawer.tsx` : les puces MITRE deviennent
  **catalogue-conscientes** (`GET /alerts/{id}/mitre`) — connues =
  nom + tactique, cliquables vers `/mitre?selected=` ; **inconnues restent
  visibles**, discrètes, lien externe attack.mitre.org (jamais masquées).
- **Restyle en tableau de bord** (demandé après relecture visuelle) : cartes
  KPI iconées et accentuées par couleur (techniques observées, tactiques
  touchées, alertes corrélées, % de couverture), en-têtes de colonne et
  pastilles de case teintées par intensité d'activité, panneau « techniques
  les plus citées », **anneau de couverture en vrai donut ECharts** (comme
  le dashboard, `EChart` partagé), bouton « Vue ATT&CK Navigator » (lien
  externe), barre d'outils avec filtre État.

**Choix assumé : pas de groupes/logiciels/campagnes/mitigations dans le
tableau de bord**, malgré une maquette de référence qui les affichait.
Périmètre acté dès ADR-010 §7 (matrice cœur uniquement) : ces objets ATT&CK
n'ont aucune donnée backend, les inventer aurait été mentir à l'écran.
Seul ce que le catalogue + la corrélation savent réellement est affiché.

**Vérification E2E réelle contre la stack Docker reconstruite** (V9+V10
appliquées, semis 25 techniques, 4 alertes de démonstration ingérées via le
webhook, dont une citant une technique `T9999` hors catalogue) :
- matrice rendue avec les 25 techniques de base (sous-technique et
  dépréciée exclues par défaut), heatmap colorée proportionnellement
  (mesurée : `T1110`=6→intensité 0.80, `T1078`=4→0.59, `T1059`=2→0.39) ;
- tiroir technique ouvert par **lien profond à froid**
  (`/mitre?selected=T1059`) : métadonnées + « Alertes citant cette
  technique (2) » exactes ;
- tiroir d'alerte : `T1071` résolue et cliquable, `T9999` inconnue mais
  **visible** (« Non cataloguée localement ») ; clic sur la puce connue →
  navigation vérifiée vers `/mitre?selected=T1071` ;
- **console propre à froid, vérifiée à plusieurs reprises** (jamais
  supposée) ; `npm run build` et Vitest verts avant chaque lot.

**Note méthodologique.** Le test `AppLayout.test.tsx` (préexistant, sans
dépendance au module MITRE) a échoué deux fois par **timeout** (5000ms) en
suite Vitest complète, alors que la même exécution en isolation passe en
moins d'une seconde de transform. Diagnostic : contention machine locale
(Docker + Testcontainers du backend récemment sollicité, plusieurs workers
Vitest concurrents) — le temps de transform mesuré passait de ~700ms
(isolé) à ~15s (suite complète). Confirmé comme un aléa d'environnement,
pas une régression : la CI GitHub Actions tourne sur un runner isolé sans
cette contention.

---

## 2026-07-25 — Jalon Threat Hunting — backend complet (PR #62, ADR-011)

**Changement de méthode de travail (décision d'Imane).** À partir de ce
jalon, un module = **2 PR maximum** (backend puis frontend), par grandes
étapes vérifiées, plus le découpage en 6-7 petits lots relus un par un
comme MITRE — jugé trop coûteux en temps et en tokens pour l'objectif de
terminer la plateforme rapidement. Les validations explicites avant
implémentation et avant merge restent dues ; c'est la granularité
intermédiaire qui disparaît.

**Contexte.** Chasse proactive : l'analyste formule une requête structurée
plutôt que d'attendre une alerte. **ADR-004 (déjà acceptée) avait déjà
tranché** le rôle de fond du module — les événements bruts massifs restent
dans OpenSearch côté outils SOC, la plateforme interroge « à la demande via
les connecteurs » (bounded context `connectors`, jamais construit). Second
fait antérieur : `alerts.raw_payload` avait été choisi en JSONB dès le
jalon Alertes A1 **explicitement pour être requêtable en threat hunting**
— ce jalon lui donne enfin son usage.

**Décision de portée validée avant tout code** : adaptateur `simulation`
(PostgreSQL, sur les alertes déjà ingérées) seul livré ; le mode `live`
(OpenSearch) est différé — deviner le schéma d'index Wazuh réel sans
l'infra réelle aurait produit un mapping fictif à refaire.

**Quatre exigences d'évolutivité posées par Imane avant le code, toutes
encodées dans le domaine :**
1. **Arbre de critères extensible sans rupture d'API.** `HuntNode` (scellé)
   = `HuntCondition` | `HuntGroup` (`AND`/`OR`/`NOT`, récursif) — la forme
   complète existe dès la V1 ; seul `HuntQuery` restreint la RACINE à un
   `AND` de conditions plates (`HUNT_LOGICAL_OPERATOR_UNSUPPORTED`,
   `HUNT_NESTED_GROUPS_UNSUPPORTED`). Débloquer l'imbrication plus tard
   change une validation, jamais le schéma JSONB ni le contrat API.
2. **Résultats réutilisables par un futur SOAR.** `HuntExecutionResult` =
   `HuntExecutionSummary` (métadonnées seules, consommable sans charger les
   résultats) + `HuntStatistics` (miroir de `AlertStatistics`, scopé à la
   chasse) + `PageResult<Alert>` (réutilisation totale, zéro DTO dupliqué).
3. **Stats d'exécution.** `tookMillis`/`matchedCount`/`truncated` dans le
   résumé. `truncated=false` toujours en simulation (comptage exact) — le
   champ existe parce qu'OpenSearch, lui, tronque réellement au-delà d'un
   seuil : la sémantique deviendra vraie sans changement de contrat.
4. **Visibilité `PRIVATE`/`TEAM`.** Stockée dès maintenant, **non appliquée**
   — documenté explicitement (Javadoc + commentaire de colonne) pour ne
   jamais laisser croire qu'une chasse « privée » l'est réellement.

**Réalisé (backend complet, une seule PR, grandes étapes).** Domaine
(`HuntField` — chaque champ déclare ses opérateurs compatibles et sa
normalisation, `MITRE_TECHNIQUE` réutilisant directement
`MitreTechniqueId.normalize()` du module MITRE — `HuntCondition`,
`HuntGroup`, `HuntQuery`, ports) → persistance (**V11** `hunt_queries`,
critères sérialisés en JSON via un **codec récursif écrit à la main**
plutôt que des annotations Jackson sur le domaine — celui-ci doit rester
framework-free, ADR-002 — MapStruct sélectionnant le codec via `uses`) →
application (`HuntQueryService` CRUD, `HuntExecutionService` orchestrant
l'exécution + le repère `lastExecutedAt`) → API (`HuntController`,
8 endpoints, RBAC lecture/exécution pour tout authentifié, écriture
ANALYST+) → ADR-011.

**Difficulté rencontrée et corrigée.** Le filtre `RAW_PAYLOAD_TEXT`
(`ILIKE` sur `raw_payload`) échouait avec
`FunctionArgumentException: lower() ... type STRING ... mapped to '3001'`
— Hibernate 6 refuse de passer un attribut mappé `SqlTypes.JSON`
directement à une fonction texte, même si PostgreSQL accepterait très bien
`raw_payload::text` en SQL brut. **Solution :** un second motif enregistré
dans `JsonbFunctionContributor` (déjà utilisé pour le containment `@>`
MITRE/CTI) — `jsonb_as_text` → `cast(?1 as text)` — appliqué avant
`lower()`. Même mécanisme, même fichier, aucune duplication.

**Vérification.** 21 tests domaine (dont le rejet nommé de chaque
restriction V1), 3 tests du codec JSON (aller-retour, y compris un arbre
imbriqué hors-scope V1 que le codec transporte quand même fidèlement),
10 tests application, **6 tests E2E sur PostgreSQL réel** — dont une
**preuve de filtrage combiné réel** (sévérité + hostname + technique MITRE
+ texte du payload brut sur 3 alertes semées, une seule correspond), le
cycle de vie complet (créer/lire/modifier/exécuter/supprimer), les 3 rejets
V1 nommés (422), RBAC (VIEWER lit et exécute, ne peut ni créer ni
supprimer), et le catalogue `/hunts/fields`. **Suite complète : 276 tests
verts** (103 domaine + 51 application + 13 infrastructure + 109 api),
`CleanArchitectureTest` vert — les nouveaux packages `hunting` respectent
les frontières de couches. Zéro régression sur les 236 tests préexistants.

---

## 2026-07-25 — Jalon Threat Hunting — frontend complet (PR #63)

**Contexte.** Deuxième et dernière PR du module (nouveau rythme à 2 PR),
démarrée seulement après merge + CI verte du backend (PR #62). Miroir des
modules existants : axios partagé + TanStack Query, jamais RTK Query.

**Réalisé.** `huntingApi.ts` (client typé, arbre `HuntNode` récursif
identique au backend). `HuntingPage.tsx` : constructeur de requête piloté
par `GET /hunts/fields` (le sélecteur d'opérateur se recalcule selon le
champ choisi, la valeur devient un select ou un texte libre selon le type
— sévérité/statut en liste fermée, date en `datetime-local` converti en
instant UTC, le reste en texte), liste des chasses sauvegardées (recherche,
exécution directe, suppression avec confirmation), table de résultats
**réutilisant intégralement** `SeverityChip`/`StatusChip`/
`AlertDetailDrawer` — zéro composant dupliqué, l'enrichissement MITRE et le
score IA du tiroir d'alerte s'appliquent donc aussi aux résultats de
chasse sans code supplémentaire. `SaveHuntDialog.tsx` (miroir de
`DeclareIocDialog`, pas de test dédié — convention déjà établie pour les
dialogues de création simples). Remplace le `PageStub` de `/hunting`.

**Choix UX assumé.** Deux façons distinctes d'exécuter, sans état caché à
deviner : le bouton « Exécuter » du constructeur appelle toujours
l'exécution AD HOC sur les critères affichés à l'écran ; le bouton ▶ de la
liste des chasses sauvegardées appelle l'endpoint dédié (marque
`lastExecutedAt`). Charger une chasse dans le constructeur (✎ implicite au
clic) ne l'exécute pas automatiquement — évite l'ambiguïté « est-ce que ce
que je vois à l'écran correspond à ce qui vient de s'exécuter ? ».

**Vérification.** 27 tests frontend verts (3 nouveaux sur `HuntingPage` —
rendu, exécution ad hoc avec résultats, ajout de condition), build et lint
(Prettier + Oxlint, **zéro avertissement** cette fois) propres. **Vérif E2E
réelle contre la stack Docker reconstruite** (backend PR #62 embarqué, V11
appliquée) : 3 alertes de démonstration ingérées via webhook, cycle complet
exercé dans le vrai navigateur — requête à 2 conditions (sévérité CRITICAL
+ hôte srv-hunt-01) donnant **exactement 1 correspondance sur 3 alertes
semées** (preuve de filtrage combiné réel, pas supposée), sauvegarde,
exécution sauvegardée avec `lastExecutedAt` visible après invalidation du
cache, ouverture du tiroir d'alerte réutilisé (enrichissement MITRE T1003
et score IA visibles), suppression avec confirmation. **Console propre à
froid, vérifiée à 4 reprises** au fil du parcours.

---

## 2026-07-25 — Dette technique CodeQL — accesseurs défensifs (PR #64)

**Contexte.** Avant d'attaquer SOAR, vérification de la dette CodeQL
ouverte plutôt que de l'accepter sur parole : l'API `code-scanning/alerts`
montrait en réalité **deux** alertes `java/internal-representation-exposure`,
pas une seule comme supposé — `HuntGroup.children()` (nouvelle, PR #62 du
jour) et `MitreTechnique.getTactics()` (plus ancienne, ouverte depuis le
jalon MITRE M1, jamais réellement corrigée malgré l'enveloppement déjà fait
à la construction).

**Correctif.** Même remède déjà appliqué à `Alert.getObservables()`/
`getMitreTechniques()` (leçon du jalon MITRE) : un accesseur écrit à la
main plutôt que généré (record ou Lombok), enveloppant la collection dans
`Collections.unmodifiableList`/`Set(...)` **au moment de la lecture**.
CodeQL ne fait pas confiance à une garantie d'immuabilité prise en amont à
la construction — il veut voir l'enveloppement dans le corps de
l'accesseur lui-même. Aucun changement de comportement : les deux
collections étaient déjà immuables en pratique.

**Vérification.** 276 tests verts (suite complète, zéro régression), dont
les tests d'immutabilité déjà existants pour les deux classes.

**Suite (2 PR séparées, même jour) : le rescan post-merge ferme une alerte
sur deux, et la première tentative de suppression échoue silencieusement.**
`MitreTechnique.getTactics()` (#26) fermée. `HuntGroup.children()` (#27)
**reste ouverte** malgré le même correctif — CodeQL continue de désigner
le constructeur compact comme site d'exposition même avec l'accesseur
canonique explicitement surchargé, signe d'une limite de son modèle des
records Java (le remède marche pour une classe Lombok classique, pas pour
ce type précis).

**Première tentative (PR #65) : commentaire en ligne `// codeql[...]`,
sans effet réel.** Rejoué au rescan suivant : l'ancienne alerte se ferme
bien (le code a changé), mais une **nouvelle** alerte (#28) réapparaît à
la ligne déplacée — la suppression en ligne n'a supprimé rien du tout,
elle a juste laissé passer un commentaire décoratif inefficace. Cause
exacte non confirmée (peut-être liée à `build-mode: none` du workflow
CodeQL de ce dépôt) — annoncé en clair plutôt que supposé.

**Correctif retenu : suppression via l'API `code-scanning` avec
justification tracée** (`state=dismissed`, `dismissed_reason=false
positive`, commentaire audité), le mécanisme réellement supporté et
vérifié effectif ici — même doctrine que le faux positif CSRF Sonar
(S4502) documenté au jalon Identity, sur un canal différent. Le
commentaire en ligne non fonctionnel a été retiré du code (un commentaire
qui prétend agir sans agir est pire qu'absent) et remplacé par un Javadoc
qui documente honnêtement les deux tentatives. Preuve à l'exécution que
l'immuabilité tient réellement, indépendamment de l'outil :
`HuntGroupTest.childrenAreDefensivelyCopied`.

**Leçon.** Une correction appliquée n'est prouvée que par le rescan
observé, jamais par la plausibilité de la syntaxe — la première
suppression avait l'air correcte (bonne ligne, bonne règle) et ne
marchait pas.

---

## 2026-07-26 — Jalon SOAR — backend complet (PR #67, ADR-012)

**Contexte.** `soar` est déclaré dès ADR-002 comme *« Playbooks, workflow
engine, exécutions, versioning »*. Comme pour Hunting (ADR-011), une
vérification des ADR déjà validées avant tout code a livré le fait
déterminant : `SOC-ARCHITECTURE.md` documente que **Shuffle**, opéré hors
de ce dépôt, est le vrai moteur d'automatisation — il exécute déjà les
playbooks réels et **pousse ses résultats vers SmartSOC via le webhook
d'ingestion existant** (`source=shuffle`, déjà observé dans les données de
démo). Le module SOAR de la plateforme documente et **suit** des
procédures de réponse, il ne les automatise pas — même doctrine que
Hunting/OpenSearch : le connecteur réel (`connectors`, jamais construit)
reste différé faute de schéma d'intégration réel.

**Réalisé (backend complet, une seule PR, grandes étapes).** Domaine
(`Playbook` — versioning léger, `order` toujours dérivé de la position de
liste jamais de la valeur fournie ; `PlaybookExecution` — cible incident
uniquement ; `PlaybookExecutionStep` — **réutilise exactement le patron
`CaseTask`** des Investigations, transitions libres + `SKIPPED` en plus)
→ persistance (**V12** `playbooks`/`playbook_executions`/
`playbook_execution_steps`) → application (`PlaybookService`,
`PlaybookExecutionService` — démarrage fige une copie des étapes) → API
(`PlaybookController`, `PlaybookExecutionController`, 9 endpoints) →
ADR-012.

**Point technique notable.** `Playbook.steps` (`List<PlaybookStepTemplate>`,
un record PLAT sans hiérarchie scellée) se sérialise nativement en JSONB
via Jackson, **sans codec dédié** — vérifié par un test de persistance
dédié. Contraste volontairement documenté avec l'arbre de critères de
Hunting, qui EN nécessitait un : la différence est la présence ou non de
polymorphisme (sealed interface) dans le type stocké.

**Vérification.** 32 tests nouveaux (15 domaine + 10 application + 2
persistance + 5 E2E sur PostgreSQL réel — dont la preuve qu'éditer un
playbook APRÈS le démarrage d'une exécution ne change jamais son nom, sa
version ni ses étapes déjà figées, RBAC, 404 sur incident inexistant,
archivage sans suppression). **Suite complète : 308 tests verts**
(118+61+13+116), zéro régression sur les 276 tests préexistants.

---

## 2026-07-25 — Jalon SOAR — frontend complet (PR #68)

**Contexte.** Deuxième et dernière PR du module (backend fusionné en #67,
ADR-012), démarrée seulement après merge + CI verte du backend — même
rythme à 2 PR que Hunting et MITRE.

**Réalisé.** `soarApi.ts` (client typé, `Playbook`/`PlaybookExecution`).
`SoarPage.tsx` : catalogue (créer/modifier/archiver, recherche, filtre
archivés), lien profond `?execution={id}` ouvrant `PlaybookExecutionDrawer`
dès le premier rendu — même patron que le tiroir d'alerte MITRE et le
tiroir d'exécution de chasse. `PlaybookDialog.tsx` (étapes dynamiques,
ajout/suppression de lignes). `PlaybookExecutionDrawer.tsx` : une ligne par
étape (Select de statut + `TextField` de note sauvegardée `onBlur`, pas de
bouton Enregistrer séparé), boutons Terminer/Annuler **masqués une fois
l'exécution dans un état terminal** (le composant ne les affiche pas s'il
n'y a plus de transition possible, plutôt que de les désactiver). Sur
`IncidentDetailDrawer.tsx` (existant, édité) : bouton « Exécuter un
playbook » ouvrant `StartPlaybookExecutionDialog.tsx` (sélecteur de
playbook actif, démarre puis navigue vers `/soar?execution=`), section
« Réponses (N) » listant les exécutions liées à l'incident, cliquables
vers le même lien profond. Remplace le `PageStub` de `/soar`.

**Vérification.** 5 tests frontend verts (`SoarPage` : catalogue + filtre
recherche ; `PlaybookExecutionDrawer` : métadonnées + étapes). Les tests de
flux d'écriture (déclarer un playbook, terminer/annuler) ont été omis au
niveau page — même convention déjà établie pour `IntelligencePage` et
`HuntingPage` : le store Redux partagé des tests n'a pas d'utilisateur
authentifié synchrone, donc `canWrite` masque ces boutons en environnement
de test. `tsc --noEmit` et `oxlint` propres. **Vérif E2E réelle contre la
stack Docker reconstruite** (backend PR #67 embarqué, V12 appliquée) :
playbook « Confinement ransomware » (2 étapes) créé dans le vrai
navigateur, déclenchement depuis l'incident réel INC-2026-0003 — snapshot
des 2 étapes vérifié identique au playbook au moment du démarrage, statut
d'étape et note (« Pare-feu coupé, hôte isolé du VLAN ») persistés et
confirmés après navigation aller-retour, section Réponses de l'incident
passée de 0 à 1 puis 2 exécutions avec le bon statut affiché. **Terminaison
et annulation testées sur deux exécutions distinctes** (`Terminée` avec
horodatage, `Annulée` sans horodatage de complétion figé) — boutons
d'action disparus dans les deux cas une fois l'état terminal atteint.
Console propre à froid vérifiée à chaque étape du parcours, aucune requête
réseau en erreur (`GET`/`POST`/`PATCH` tous 200/201).

---

## 2026-07-26 — Jalon Rapports — backend complet (PR #69, ADR-013)

**Contexte.** `reporting` est le 8ᵉ et dernier contexte plateforme déclaré
dès ADR-002. Distinction posée avant tout code : le Dashboard existant est
une vue **live** (« maintenant »), un rapport est un **instantané figé**
d'une **période passée** — objet distinct, jamais un doublon.

**Décisions verrouillées (3, via AskUserQuestion).** Jeu de métriques
complet (alerts/incidents/soar bornés à la période + Hunting/MITRE avec
leurs limitations annoncées en clair — voir ADR-013) ; export écran + CSV
**+ PDF** (choix élargi par Imane en cours d'échange) ; notifications par
adaptateur e-mail en mode simulation/live, **même patron exact que
`SMARTSOC_AI_MODE`** (ADR-008).

**Réalisé.** Domaine `com.smartsoc.domain.reporting` : `Report` (immuable
une fois généré — artefact d'audit, aucune suppression, même doctrine
qu'Incidents/Cases), `ReportMetrics` (record plat composé de records
plats — sérialisation JSONB native sans codec, même choix que
`Playbook.steps`). Quatre méthodes `periodMetrics(from, to)` ajoutées aux
repositories **existants** (`AlertRepository`, `IncidentRepository`,
`PlaybookExecutionRepository`) et `countExecutedInPeriod` à
`HuntQueryRepository` — `ReportGenerationService` les compose en lecture
seule, même doctrine cross-contexte que `MitreCorrelationService`.
**`Incident.closedAt` ajouté** (V13, nullable, rétrocompatible) : aucun
module existant n'avait besoin de savoir QUAND un incident se clôture,
nécessaire ici pour le temps moyen de résolution — rempli par
`Incident.transitionTo()` à l'entrée en `CLOSED`. Export CSV/PDF derrière
un port `ReportExporter` unique (rendu déterministe, aucun appel externe,
donc pas de mode simulation/live comme pour les notifications) — PDF via
**OpenPDF** (fork LGPL d'iText 4, première dépendance de génération de
document du backend). Notifications via `ReportNotifier`
(`SimulatedReportNotifier`/`LiveReportNotifier`, `spring-boot-starter-mail`
en mode live). API `/api/v1/reports` (génération SOC_MANAGER+, lecture
tout authentifié — RBAC déjà déterminé par le Javadoc existant du `Role`
enum, pas une nouvelle décision).

**Bug trouvé et corrigé pendant les tests d'intégration.** `avg(extract(epoch
from ...) / 3600.0)` en PostgreSQL rend un `numeric` dès lors que le
diviseur est un littéral décimal — donc un `BigDecimal` côté JDBC, pas un
`Double` : `(Double) row[1]` levait un `ClassCastException` en E2E réel
(jamais vu en test unitaire mocké). Corrigé par `((Number) row[1]).doubleValue()`,
qui encaisse le type SQL réel sans en dépendre.

**Vérification.** 20 tests nouveaux (5 domaine + 5 application + 1
persistance + 3 API E2E Testcontainers, dont un test qui ingère une vraie
alerte, ouvre-et-clôture un vrai incident et termine une vraie exécution
SOAR dans la même fenêtre temporelle pour prouver l'agrégation
inter-contextes réelle, pas supposée). **Suite complète : 328 tests
verts** (128+66+13+121), zéro régression sur les 314 tests préexistants,
ArchUnit vert (le nouveau contexte `reporting` respecte les frontières de
couches et reste framework-free côté domaine).

---

## 2026-07-26 — Jalon Rapports — frontend complet (PR #70)

**Contexte.** Deuxième et dernière PR du module (backend fusionné en #69,
ADR-013), démarrée seulement après merge + CI verte du backend. Demande
explicite d'Imane : « un parfait frontend de ce module ».

**Réalisé.** `reportsApi.ts` (types alignés sur `ReportDtos`, y compris
l'export blob — première fonction de téléchargement de fichier de la
plateforme : `responseType: 'blob'` + `URL.createObjectURL` + ancre
temporaire). `ReportsPage.tsx` (liste, génération réservée SOC_MANAGER+,
lien profond `?selected=`). `GenerateReportDialog.tsx` (titre + période
`datetime-local`, pré-rempli sur les 7 derniers jours). `ReportDetailDrawer.tsx` :
tableau de bord complet du rapport — cartes KPI (miroir du style déjà
établi en MITRE M3), donut de sévérité et répartition par statut
**réutilisant** `SeverityChip`/`StatusChip` d'Alertes (zéro duplication),
panneaux Incidents/SOAR/Hunting/MITRE avec les limitations Hunting/MITRE
**affichées en clair dans l'UI elle-même** (pas seulement dans l'ADR),
techniques MITRE cliquables → `/mitre?selected=` (même lien profond que
le tiroir d'alerte), boutons Export CSV/PDF.

**Deux bugs réels trouvés en vérification E2E (aucun test automatisé ne
les couvrait) :**

1. **`/actuator/health` restait DOWN en permanence en mode simulation**,
donc le `HEALTHCHECK` Docker du backend n'atteignait jamais `healthy`.
Cause : `spring-boot-starter-mail` enregistre automatiquement un
`MailHealthIndicator` qui tente une vraie connexion SMTP — sans hôte
configuré (le défaut), il échoue et fait chuter le statut agrégé, alors
que la plateforme fonctionne normalement (même esprit que le classifieur
IA, disponible sans service IA réel). Corrigé par
`management.health.mail.enabled=false`.

2. **Export CSV corrompait tous les accents** (« Sévérité » devenait
« SÃ©vÃ©ritÃ© ») : `produces = "text/csv"` sans charset explicite retombe
en ISO-8859-1 côté HTTP, réinterprétant les octets UTF-8 réels. Corrigé
par `text/csv;charset=UTF-8` sur le contrôleur. **Nouveau test dédié**
`ReportExporterAdapterTest` (3 tests, dont un qui aurait attrapé cette
régression — l'assertion E2E existante ne testait que l'en-tête ASCII,
pas les valeurs accentuées).

**Vérification.** 2 tests frontend verts (`ReportsPage`,
`ReportDetailDrawer` — `EChart` mocké comme dans `DashboardPage.test.tsx`,
jsdom n'a pas de canvas). `tsc`, `oxlint`, `npm run build` propres.
**Vérif E2E réelle contre la stack Docker reconstruite deux fois**
(V13 appliquée, puis la correction santé/CSV) : génération d'un rapport
réel sur les 7 derniers jours, agrégation confirmée exacte — les
compteurs SOAR affichés (2 démarrées, 1 terminée, 1 annulée) correspondent
**exactement** aux exécutions réalisées dans le navigateur lors de la
vérification E2E du jalon SOAR — export CSV relu octet par octet avec
accents corrects, export PDF avec en-tête `%PDF` valide, lien profond
MITRE vérifié (`T1110` → `/mitre?selected=T1110`, technique retrouvée
avec le même compte d'alertes), console propre à froid. **MODULE RAPPORTS
INTÉGRALEMENT TERMINÉ (backend #69 + frontend #70).**

---

## 2026-07-26 — Ergonomie globale et rebranding ISIX (PR #71)

**Contexte.** Hors de la feuille de route des 9 modules (Investigations →
… → Rapports → Assistant IA) : chantier transverse demandé par Imane,
touchant toutes les pages. Deux décisions structurantes verrouillées
avant tout code : (1) le futur Dashboard (PR suivante) doit tirer ses
KPI **directement des modules**, jamais du module Rapports — Rapports
reste une source secondaire, historique/comparative uniquement, le
Dashboard doit rester utilisable même sans aucun rapport généré ; (2) le
rebranding SmartSOC → **ISIX** est **interface uniquement** — packages
Java, dépôt Git, namespace, ADR, migrations, Docker, CI restent
`smartsoc`.

**Contrôle de réalité avant conception.** Croisement systématique des 12
blocs demandés pour le futur Dashboard avec les endpoints et champs
réellement existants (aucune nouvelle API autorisée). Résultat : une
**carte mondiale géographique est impossible** — zéro champ pays/IP
publique dans tout le domaine (`Alert`, `Asset`, `Observable`) —
remplacée par un panneau « Surface d'attaque » sur des données 100%
réelles (exposition, criticité, hostnames). SLA incidents, vulnérabilités
d'actifs, tendance des sources, campagnes/groupes APT, historique de
chasse : également absents de tout endpoint, annoncés comme non
construits plutôt que simulés.

**Réalisé (cette PR — ergonomie + branding, la refonte du Dashboard suit
en PR séparée).** `AppLayout.tsx` réécrit : sidebar repliable (248 ↔
64 px, transition 200 ms, `localStorage`, tooltips avec nom accessible
préservé même repliée — vérifié par lecture de l'arbre d'accessibilité,
pas supposé), header Enterprise (fil d'Ariane dérivé de la carte de
navigation existante — **aucune donnée par page à maintenir**, horloge
locale + UTC clientes, raccourci Ctrl+K). `GlobalSearch.tsx` : filtre de
navigation statique + **recherche réelle d'actifs par hostname**
(`GET /assets?search=`, seul endpoint du périmètre à supporter un texte
libre — aucune autre recherche simulée). `NotificationsBell.tsx` : badge
= `byStatus.NEW` de `/alerts/stats` (même clé de cache que le Dashboard),
**un seul chiffre affiché** — `/alerts/stats` ne croise pas sévérité et
statut, afficher une répartition par sévérité des « nouvelles » aurait
été une donnée inventée. `useAlertsRealtime()` monté globalement dans
`AppLayout` (pas seulement Alertes/Dashboard) pour que le badge reste à
jour sur tout le site. `scrollbars.css` global (fines, transparentes au
repos, couleur du thème au survol) + anneau de focus visible
(`:focus-visible`, WCAG 2.4.7). `AlertsPage` : lit `?status=` en valeur
initiale (lien profond depuis les notifications), sans changer son
fonctionnement existant. Branding : logo ISIX fourni par Imane détouré
(dépremultiplication alpha, fond noir supprimé sans toucher au dégradé
cyan→bleu), dérivés WebP optimisés (**133 Ko embarqués au lieu des
870 Ko d'origine** — le fichier déposé dans `public/` aurait sinon été
copié tel quel dans le bundle de production), favicon, `<title>`, page de
connexion. Palette du thème (`#2f81f7`/`#39c5cf`) **non touchée** : déjà
quasi identique aux couleurs du logo, un changement aurait risqué une
régression visuelle sur tous les composants existants pour un gain nul.

**Vérification.** Suite complète 32 tests verts (mock de
`useAlertsRealtime` et `getAlertStats` dans `AppLayout.test.tsx`, même
patron que `DashboardPage.test.tsx` — sans quoi le test aurait tenté une
vraie connexion WebSocket en environnement jsdom). `tsc`, `oxlint`,
`npm run build` propres. **Vérif E2E réelle** (stack Docker + Vite dev
proxy) : connexion réelle, badge de notifications confirmé identique aux
« Nouvelles (à trier) » du Dashboard (20 = 20), recherche Ctrl+K sur
`srv-web-01` retrouvant le vrai actif en base et naviguant vers son
tiroir, **zéro scroll horizontal de page vérifié à 2560×1440, 1366×768 et
768×1024** (y compris sur `/mitre`, page qui en avait historiquement),
persistance du repli de sidebar après rechargement complet, console
propre à froid à chaque étape.

**Complément (même PR, retour d'Imane sur maquette) : profil en pied de
sidebar et mode clair/sombre.** Sur retour visuel (référence externe,
sidebar façon « widelab ») : haut de sidebar = logo ISIX + rôle de
l'utilisateur (« Administrateur », etc.) ; bas de sidebar = identité
complète (`UserMenu.tsx` déplacé du header vers un bloc pied-de-page
avatar + nom complet + rôle, repliable comme le reste de la sidebar).
**Le nom complet n'était pas exposé par `/auth/me`** (dérivé uniquement
des claims JWT — username/userId/role) : ajout minimal et honnête
(`MeResponse.fullName`, résolu par un lookup `UserRepository` sur le
username du token, **pas** un claim figé au login, pour refléter un
changement de nom immédiatement) — champ existant sur un endpoint
existant, zéro nouvelle route. Mode clair/sombre : `buildTheme(mode)`
remplace le thème statique (surfaces/texte inversés, **sévérités et
accents identiques dans les deux modes** — ce sont des couleurs de
statut sémantique, pas des couleurs de surface), `ThemeModeProvider`
(contexte + `localStorage`), `EChart` suit le mode (thème ECharts nommé
'dark' vs défaut clair). L'export `theme` historique (dark statique) est
conservé tel quel pour ne toucher **aucun** des 20+ fichiers de test
existants qui l'utilisent.

**Deux bugs réels trouvés, aucun par les vérifications habituelles.**
(1) `tsc --noEmit` et Vitest passaient tous les deux verts alors que le
**vrai** `npm run build` (celui que le Dockerfile exécute, `tsc -b`,
mode projet composite) échouait : une prop MUI7 invalide
(`Switch.inputProps` → `slotProps.input`, même famille de rupture que
`InputLabelProps`/`primaryTypographyProps` déjà rencontrées) et un
fixture de test (`authSlice.test.ts`) non mis à jour avec le nouveau
champ `fullName` — Vitest transpile sans type-checker les fichiers de
test, `tsc --noEmit` seul n'a pas la même portée que `tsc -b`. **Leçon
retenue : toujours vérifier avec `npm run build` exact, jamais
`tsc --noEmit` seul, avant un rebuild Docker.** (2) Accessibilité :
`aria-label="Menu utilisateur"` fixe sur le bouton de profil masquait le
nom affiché aux lecteurs d'écran — corrigé en
`` `Menu utilisateur — ${displayName}` ``, sur les deux variantes
(repliée et dépliée).

**Vérification du complément.** Suite complète re-vérifiée verte (deux
lectures bruitées par contention machine — fichiers différents à chaque
fois, système déjà chargé par le rebuild Docker en parallèle — confirmées
non reproductibles, troisième lecture 32/32 propre). `npm run build`
réel (celui de Docker) propre après correction. **E2E réel contre la
stack Docker reconstruite** : haut de sidebar affichant « ISIX » +
« Administrateur », bas affichant **« Platform Administrator »** (le
nouveau champ backend, valeur réelle du compte bootstrap) + rôle,
bascule clair/sombre vérifiée par la couleur de fond calculée
(`rgb(246,248,250)` = `#f6f8fa` exact) sur deux pages dont une avec
graphique ECharts (`/mitre`), persistance après rechargement complet,
console propre à froid à chaque étape.

---

## 2026-07-27 — Refonte du Dashboard ISIX (PR #72)

**Contexte.** Deuxième PR du chantier ergonomie/branding (PR A = #71,
mergée). Consigne explicite d'Imane, corrigeant le plan initial : le
Dashboard doit tirer ses KPI **directement des modules**, jamais du
module Rapports — Rapports ne doit servir qu'en secours (historique/
comparaison), le Dashboard doit rester utilisable sans aucun rapport
généré. Contrôle de réalité déjà fait en amont (entrée PR #71) : carte
géographique impossible, SLA/vulnérabilités/campagnes APT absents de
tout endpoint — non construits ici non plus.

**Réalisé.** `useDashboardData.ts` : compose **9 requêtes parallèles**
directes (alerts, incidents, assets, playbooks, hunts, iocs, techniques
MITRE, couverture MITRE) — **aucune n'appelle `/reports`** ; les rapports
n'entrent que via `listReports` pour l'activité récente secondaire.
Chaque compteur affiché dérive de `totalElements` (vrai total serveur,
indépendant de la page) ; les répartitions détaillées (statut, criticité,
top techniques) sont calculées côté client sur les `items` chargés —
exactes tant que le volume réel reste sous le plafond de page (200, le
max autorisé par l'API), **annoncé en commentaire, pas masqué**.
`DashboardPage.tsx` réécrit en 4 zones : **A** — 6 tuiles KPI 100% réelles
(alertes totales/critiques, incidents ouverts, actifs critiques exposés,
taux de faux positifs, couverture MITRE — remplace un « score IA »/« score
de posture » qui aurait nécessité une formule inventée, écartée
délibérément) ; **B** — activité 7 jours + alertes récentes ; **C** — 6
panneaux (MITRE, **Surface d'attaque** remplaçant la carte géographique
impossible, Sources SOC, Incidents & Réponse, Posture des actifs, Threat
Intelligence) ; **D** — activité récente fusionnant alertes/incidents/
rapports triés par date, chaque ligne cliquable vers son module. **Chaque
widget est un point d'entrée réel** (clic → route ou lien profond), pas
un mur d'affichage statique. **`IncidentsPage.tsx` complété** : lien
profond `?selected=` ajouté (même patron qu'Assets/MITRE/Rapports,
absent jusqu'ici) — nécessaire pour que le Dashboard puisse pointer vers
un incident précis, cohérence apportée à toute la plateforme au passage.

**Vérification.** 4 tests frontend verts (mock de `useDashboardData`
dans son ensemble plutôt que 8 modules séparés — plus simple, même
patron que les autres tests de composition). `tsc`, `oxlint`,
**`npm run build` réel** (leçon de la PR précédente appliquée dès le
départ) propres. **E2E réelle contre la stack Docker reconstruite** :
6 KPI vérifiés avec des valeurs réelles (couverture MITRE 29% —
**identique** à la valeur affichée sur `/mitre`, même calcul, preuve de
cohérence inter-pages), les 6 panneaux de la zone C peuplés de données
réelles (0 actif critique exposé — honnête, l'unique actif de démo est
MEDIUM, pas fictif), activité récente correctement fusionnée et triée
(rapport du jour en tête, incident le plus ancien en fin), lien profond
MITRE (`/mitre?selected=T1110`) et lien profond incident nouvellement
ajouté (`/incidents?selected={id}`, tiroir vérifié ouvert) tous deux
vérifiés fonctionnels, **zéro scroll horizontal à 2560×1440, 1366×768 et
768×1024**, console propre à froid à chaque étape.

---

## 2026-07-27 — Assistant IA A1 — pont plateforme vers l'agent conversationnel (PR #73, ADR-008)

**Réalisé.** Port `SocAssistant` (module application) et deux adaptateurs
(`simulation` par défaut, `live` via client Feign implémentant exactement
`docs/integration/ai-assistant-api.yaml`), circuit breaker et timeout 30 s
dédiés — même patron que le classifieur TP/FP (PR #44). Endpoint
`POST /api/v1/assistant/chat` (JWT, tout utilisateur authentifié) : le
backend reste **sans état**, le frontend est propriétaire de l'historique
et le renvoie intégralement à chaque appel, conformément au contrat.
Dégradation gracieuse : 503 `AI_UNAVAILABLE` (réutilise l'exception déjà
générique du classifieur). Nouvelle clé d'API de **lecture dédiée**
(`AiToolsApiKeyFilter`/`AiToolsProperties`, même patron que l'ingestion) :
permet au service IA externe d'appeler des endpoints GET existants
(alertes, MITRE…) sans compte analyste, strictement en lecture — le filtre
reste inactif hors GET, aucune règle de sécurité supplémentaire nécessaire.

**Vérification.** 10 nouveaux tests (unitaires + intégration
Testcontainers/WireMock), 358 tests backend verts.

---

## 2026-07-27 — Assistant IA A2 — module frontend (PR #75, ADR-008)

**Réalisé.** Le `PageStub` de `/assistant` remplacé par un vrai module
(même patron que les autres features : axios partagé, pas de RTK Query) :
`assistantApi.ts` (`chatWithAssistant`, timeout client 35 s — volontairement
> 30 s côté Feign pour laisser le 503 dégradé arriver avant un abandon
client) ; `AssistantPage.tsx` (fil de conversation, indicateur « en train
d'écrire », suggestions de questions au démarrage, gestion d'erreurs dédiée
503/timeout/403/réseau). Historique **et** contexte conservés en
`sessionStorage` (survit à un rechargement, jamais envoyé au backend qui
reste sans état). État géré par un reducer pensé pour le streaming futur
sans refonte des composants, même si l'appel actuel est un JSON unique.
Bouton **« Demander à l'assistant »** dans `AlertDetailDrawer` et
`IncidentDetailDrawer` : construit le contexte **uniquement** à partir de
champs réels déjà chargés (titre, sévérité, hôte, techniques MITRE),
jamais inventé.

**Vérification.** 39 tests frontend verts (7 nouveaux) ; E2E réelle contre
le backend en mode simulation : round-trip complet, contexte réel transmis
et confirmé par le réseau, persistance `sessionStorage` à la navigation et
au rechargement, clair/sombre, console propre.

---

## 2026-07-28 à 2026-07-30 — Assistant IA : trois ajustements de délai en conditions réelles (PR #76, #77, #78)

**Contexte.** Une fois l'assistant branché à un vrai modèle Ollama local
(CPU sans GPU dédié), trois problèmes réels sont apparus successivement en
usage, chacun corrigé et vérifié avant le suivant — illustration directe
de la doctrine du projet : les délais et hypothèses posés sur documentation
(ADR-008) sont révisés dès qu'un fait réel les contredit.

**PR #76 — délai relevé à 60 s.** Vérifié en réel contre Ollama (modèle
léger CPU) : une réponse détaillée peut légitimement dépasser les 30 s
documentés dans l'ADR-008, qui n'était qu'une estimation. Délai relevé
côté Feign, contrat (`ai-assistant-api.yaml` v1.0.1) et ADR-008 ; délai
client frontend ajusté à 65 s (doit rester strictement supérieur au délai
serveur pour laisser la dégradation 503 arriver en premier). 6/6 tests
d'intégration assistant revalidés.

**PR #77 — bug réel trouvé en usage : une bulle d'erreur renvoyée comme
message.** Une bulle d'erreur affichée à l'écran (« assistant
indisponible ») était renvoyée comme si elle faisait partie de la
conversation au message suivant. Le service IA détectait le `;` de ce
texte d'erreur comme une tentative d'injection SQL et rejetait **toute**
la requête (400) — y compris le nouveau message utilisateur, parfaitement
anodin. Correctif : les messages marqués `failed` sont désormais exclus de
l'historique envoyé au backend, ce sont des artefacts d'affichage client,
jamais du contenu réel de conversation.

**PR #78 — délai relevé à 120 s : le modèle 7b choisi en connaissance de
cause.** Décision explicite de repasser sur un modèle plus intelligent
(qwen2.5:7b) plutôt que le modèle léger (1.5b), au prix d'une latence
60-90 s+ sur ce CPU. Délai Feign/contrat/ADR-008/frontend relevé en
conséquence (60 s → 120 s), vérifié en réel (73 s pour une réponse simple,
sous la nouvelle limite). 6/6 tests d'intégration revalidés.

---

## 2026-07-30 — Classifieur IA — zone, dérogation et justifications complémentaires (PR #79, #80, ADR-008)

**Contexte.** Extension **rétrocompatible** du contrat classifieur TP/FP
(`ai-classifier-api.yaml` v1.0.0 → v1.1.0) pour brancher un classifieur
réel plus riche (moteur ML multi-outils, routage en 3 zones) sans rien
casser du modèle existant (`aiScore`/`aiVerdict`, déjà intégré au module
Alertes depuis PR #44).

**Réalisé (backend, PR #79).** `AiZone` (nouveau domaine) :
`SOAR_ESCALATION` / `ANALYST_REVIEW` / `ARCHIVE`. `Alert.applyAiEnrichment
(zone, hardOverride, justifications)` : méthode **séparée** de
`applyAiAssessment`, purement additive, jamais requise. Migration V14
(colonnes `ai_zone`/`ai_hard_override`/`ai_justifications` — même doctrine
que `ai_score`/`ai_verdict` : dernière classification connue, pas de table
d'historique séparée). `LiveAlertClassifier` : les 3 nouveaux champs sont
optionnels côté service IA réel (v1.0.0 reste pleinement conforme), une
zone inconnue est ignorée sans faire échouer la classification.
`SimulatedAlertClassifier` : mêmes seuils que le classifieur réel, jamais
de dérogation forcée en simulation (aucune preuve réelle ne la
justifierait).

**Réalisé (frontend, PR #80).** `AiZoneChip` (badge doux, couleur par
zone) ; section « Zone recommandée » dans `AlertDetailDrawer`, juste après
le score IA existant, affichée **seulement** si une zone ou des
justifications sont présentes (masquée proprement pour les alertes non
classifiées ou classifiées par un fournisseur v1.0.0 sans enrichissement) ;
dérogation forcée signalée par un badge distinct.

**Vérification.** Backend : 352 tests verts. Frontend : vérifié en réel
contre la stack Docker reconstruite + SocAI en mode live local — alerte
classifiée affichant la vraie zone ARCHIVE et les 6 justifications réelles
du moteur (XGBoost/FAISS/MITRE/etc.), alerte non classifiée masquant
correctement la section, console propre.

---

## 2026-07-31 — Module Paramètres — console d'administration Enterprise (PR #81, #82)

**Contexte.** Consigne explicite : pas une simple page de réglages, mais
une véritable console d'administration digne d'une plateforme SOC
professionnelle (Sentinel, Cortex XSOAR, QRadar…). Décision d'équipe issue
d'un audit préalable réel-vs-fabriqué : périmètre restreint aux sections
avec de **vraies** données derrière — connecteurs SOC et mode maintenance
laissés en « à venir » honnête, aucune donnée inventée.

**Réalisé (backend, PR #81).** Nouveau bounded context `domain.audit`
(`AuditAction`, `AuditLogEntry` immuable). `AuditRecorder` :
`@Transactional(REQUIRES_NEW)` — une entrée d'audit survit toujours à la
transaction appelante (même correctif que la détection de réutilisation de
refresh token, PR #14, généralisé une fois pour toutes) — et n'échoue
jamais bruyamment. Audit branché sur `AuthService.login()`
(LOGIN_SUCCEEDED/FAILED avec IP) et `UserManagementService` (CRUD complet).
V15 + `GET /api/v1/audit-logs` (ADMIN, filtres). **Sauvegarde réelle** :
`PgDumpBackupAdapter` (`pg_dump --format=custom`, mot de passe via
`PGPASSWORD` jamais en argument), `GET /api/v1/settings/backup` télécharge
un vrai dump et trace `BACKUP_EXPORTED` — restauration explicitement hors
scope (action destructrice distincte). `GET /api/v1/settings/{security,
ai,notifications,about}` : exposition en lecture seule de la configuration
réelle (clés configurées sans jamais exposer leur valeur, ping réel des
services IA, version/uptime réels). `POST /settings/notifications/test` :
envoi réel d'un e-mail de diagnostic en mode live, échec remonté (contraire
à la doctrine `ReportNotifier` — c'est le seul but du bouton).

**Difficulté (gate SonarCloud).** `new_reliability_rating` en échec (S6218 :
`BackupResult` exposait un `byte[]` sans `equals`/`hashCode`/`toString`
adaptés) et `new_coverage` à 70,5 % (< 80 %). Corrigés sans changement de
comportement : méthodes explicites sur `BackupResult`, et surtout des tests
**réels** plutôt que des tests de complaisance — `AiHealthCheckerTest` via
un `HttpServer` JDK embarqué, `LiveNotificationTestSenderTest` via un vrai
`JavaMailSenderImpl` pointé vers un port fermé (exerce le vrai chemin
d'échec SMTP). 381 tests backend verts au final.

**Réalisé (frontend, PR #82).** Composant réutilisable `SettingsCard`,
sous-navigation par catégorie (12 sections, deep-link `?section=`), chaque
section branchée sur ses propres données réelles : Vue générale, RBAC,
Sécurité, IA (+ santé réelle), SOAR, Notifications (+ test d'envoi réel),
Journal d'audit (table filtrable/paginée), Sauvegarde (téléchargement réel,
restauration explicitement absente avec l'explication en clair), Santé
(`/actuator/health` via nouveau proxy Vite), À propos. Connecteurs et
Maintenance : état « à venir » honnête. `/settings` déplacé sous
`RequireRole(ADMIN)`.

**Bug réel trouvé en vérification navigateur (jamais vu en test Vitest).**
`SettingsRow` enveloppait sa valeur dans un `<Typography>` (rendu `<p>`),
invalide dès qu'un `Chip` (`<div>`) y était passé — erreur console réelle
(« cannot be a descendant of `<p>` »). Corrigé en `<Box>`.

**Vérification.** E2E réelle contre le dev server + backend Docker
reconstruit : les 12 sections parcourues avec de vraies données (dont une
sauvegarde réellement déclenchée et téléchargée), clair/sombre, mobile
(zéro débordement horizontal), console propre à froid. 43 tests frontend
verts, lint et build réels propres.

---

## 2026-08-04 — Fiabilité du module Paramètres et de l'infrastructure IA locale (PR #83 + travaux hors dépôt)

**Difficulté réelle en usage (PR #83).** Le ping de santé de l'assistant
(relayé à Ollama) mesure en pratique 2,6 à 4,4 s sur ce CPU sans GPU
dédié — largement au-dessus des 2 s fixées à la création
d'`AiHealthChecker` (PR #81) sans donnée réelle disponible à l'époque.
Conséquence concrète : `/api/v1/settings/ai` et la console Paramètres
affichaient l'assistant « Indisponible » alors qu'il répondait
correctement. Délai relevé à 8 s (marge au-delà du pire cas observé),
vérifié par redémarrage réel des deux services IA.

**Exploration hors dépôt : vitesse de l'assistant vs qualité du modèle.**
Les deux services IA (classifieur TP/FP et assistant, développés
séparément par l'équipe IA — ADR-005) ont été rendus démarrables en un
clic (scripts `start.bat`/`start.ps1` dans chaque dépôt externe, plus un
lanceur unique sur le Bureau qui attend la disponibilité réelle des deux
services avant de rendre la main). Mesures réelles effectuées directement
sur l'API Ollama de la machine (CPU Intel Iris Xe intégré, sans GPU dédié,
12 threads) : qwen2.5:7b ≈ 3,1 tokens/s, qwen2.5:1,5b ≈ 13 tokens/s.
Optimisations appliquées côté service assistant (`num_predict`/`num_ctx`/
`num_thread` sur l'appel Ollama, prompt système resserré, préchauffage du
modèle au démarrage) : temps de réponse réel mesuré via l'endpoint
`/api/v1/chat` du contrat, passé de 60-90 s+ à 26-29 s typique avec le 7b.
Constat rapporté explicitement plutôt que masqué : les 10-15 s visés et la
qualité du 7b sont physiquement incompatibles sur ce matériel CPU seul.
Décision (après mesure) de repasser sur qwen2.5:1,5b (22,9 s mesurés en
réel sur l'endpoint contractuel) — au prix d'une qualité de réponse
dégradée constatée sur un exemple réel (explication imprécise du
credential stuffing), compromis assumé par l'équipe en attendant une
éventuelle bascule vers 7b ou 3b.

**Difficulté technique notable (lanceur Bureau).** Le script batch combinant
attente Docker Desktop et attente des deux services IA échouait
systématiquement (« … était inattendu. », code de sortie 255) : un `goto`
à l'intérieur d'un bloc parenthésé `if ( ... )` combiné à une boucle casse
le parseur de `cmd.exe` — un piège classique du langage batch, non détecté
par une simple relecture, isolé par bissection (fichiers de test minimaux
reproduisant puis excluant chaque construction) puis corrigé en réécrivant
les deux boucles concernées avec des `if ... goto ...` en ligne simple.

**Vérification.** PR #83 : 6 tests backend revalidés (`AiHealthCheckerTest`,
`SettingsControllerIntegrationTest`) + rebuild Docker réel confirmant les
deux services affichés « UP ». Lanceur Bureau : exécuté pour de vrai
(code de sortie 0), a réellement lancé les deux fenêtres de service et
détecté leur disponibilité.

---

## 2026-08-08 — Intégration SOC, phase 0 et phase 1.1 — premier connecteur réel (ADR-014, ADR-015)

**Contexte.** Baseline d'architecture d'intégration SOC figée (ADR-014,
v1.1) après audit complet du dépôt et validation en trois passes avec
Imane. Documentation d'architecture d'entreprise produite en parallèle
(C4, Context Map DDD, événements de domaine, diagrammes de séquence,
chapitres IA et SOAR, référence des connecteurs — 9 documents, 22
diagrammes Mermaid), aucune décision structurante modifiée depuis.
Chantier lancé dès le tunnel WireGuard monté entre le poste de
développement et le hub SOC (`vm-siem`, 10.100.0.1).

**Phase 0 — validation réseau (ADR-015).** Le risque R1 identifié dans la
baseline — un conteneur sur un réseau Docker *bridge* peut-il joindre un
tunnel WireGuard monté sur l'hôte ? — est **levé sans aucun aménagement** :
Docker Desktop route via WSL2 vers l'hôte, qui possède la route. Les
quatre outils SOC (Wazuh API, OpenSearch, MISP, Shuffle) sont confirmés
joignables **depuis un conteneur** du réseau `smartsoc-net`, ainsi que
VirusTotal via le second chemin (Internet, hors WireGuard). Risque R2
(certificats auto-signés) confirmé et qualifié plus finement que prévu :
sur les quatre certificats SOC, un seul (OpenSearch) couvre son adresse
WireGuard en SAN — les trois autres (Wazuh API, MISP, Shuffle, ce dernier
étant le certificat par défaut du produit, clé privée publique) ont été
régénérés côté SOC avec l'IP WireGuard ajoutée en SAN, sans retirer
`localhost` pour ne pas casser les accès locaux existants. Deux défauts de
configuration réseau relevés au passage et corrigés : le peer Kali actif
à chaud mais absent du fichier `wg0.conf` (aurait disparu au redémarrage),
et `wg-quick@wg0` non activé au démarrage sur au moins une VM spoke.

**Phase 1.1 — alertes Wazuh en push, bout en bout réel.** Script
d'intégration Wazuh (`custom-smartsoc.py`) écrit et déployé sur `vm-siem`,
poussant vers `POST /api/v1/ingest/alerts` (endpoint déjà construit,
zéro code plateforme). Seuil de déclenchement niveau ≥ 7 initialement
retenu (relevé à 10 par la suite, aligné sur les intégrations MISP/Shuffle
déjà en place sur cette VM).

**Cinq bugs réels trouvés et corrigés en vérification, aucun par
supposition.**
1. `<name>custom-smartsoc</name>` sans l'extension `.py` dans
   `ossec.conf` : Wazuh n'invoquait jamais le script, silence total, sans
   la moindre erreur nulle part — trouvé par comparaison avec le bloc
   `custom-misp.py` fonctionnel, dont le nom incluait l'extension.
2. Ordre des arguments Wazuh mal supposé initialement
   (`hook_url`=argv[3], `api_key`=argv[4]) : l'ordre réel documenté par
   Wazuh est `alert_file`=argv[1], `api_key`=argv[2], `hook_url`=argv[3].
   Diagnostiqué en ajoutant une trace explicite de chaque argument
   (longueur + 6 derniers caractères, jamais la valeur complète) plutôt
   que de re-deviner un troisième ordre au hasard.
3. Clé d'API corrompue lors d'un copier-coller manuel (68 caractères
   collés au lieu de 64, terminée par un fragment de chemin de fichier
   `.env`) — trouvé en comparant les longueurs des deux côtés plutôt
   qu'en supposant la valeur correcte.
4. Champ `detectedAt` (`@NotNull Instant`, obligatoire côté
   `IngestAlertRequest`) jamais envoyé par le script initial : 400
   `VALIDATION_FAILED`, diagnostiqué en relisant le contrat publié
   (`docs/integration/alert-ingestion.md`) et le DTO source côte à côte.
5. Format d'horodatage Wazuh (`+0000`, décalage sans deux-points) non
   garanti compatible avec le désérialiseur Jackson du backend :
   normalisé côté script avant envoi (regex d'insertion du deux-points,
   conversion UTC, suffixe `Z`) plutôt que transmis tel quel.

**Bug pré-existant trouvé dans `custom-misp.py`, hors périmètre mais
signalé et corrigé à la demande d'Imane.** `KeyError: 'misp'` à chaque
exécution : la fonction `merge_enrichment()` (qui transforme la sortie de
`enrich_observables()` — clé `intel` — vers le format attendu par
`build_enriched_alert()` — clé `misp`) existait dans le fichier mais
n'était jamais appelée dans `main()`. Une ligne manquante, systématique,
indépendante de la disponibilité de MISP. Corrigé par insertion `sed`
ciblée (fichier trop long et rendu inégal du terminal pour une édition
manuelle fiable), validé par `py_compile` avant et après.

**Vérification réelle, chaîne complète.** Attaque brute-force SSH
authentique (bots Internet déjà actifs sur `vm-siem`, puis déclenchement
manuel contrôlé pour cadrer le test) → règle Wazuh 5712 (niveau 10,
technique `T1110`) → `custom-smartsoc.py` → webhook SmartSOC (`HTTP 201`)
→ PostgreSQL (16 → 19 alertes `source=wazuh` sur la session, mapping
sévérité/hostname/ruleId/technique MITRE vérifié exact) → classification
IA asynchrone. **Dégradation gracieuse démontrée sur ses deux états
réels, pas supposée** : classifieur SocAI éteint → alerte persistée avec
`ai_verdict` vide, immédiatement exploitable ; classifieur rallumé →
alerte suivante classifiée en ~15 s par le vrai moteur (XGBoost + FAISS +
MITRE + graphe de nouveauté), verdict `FALSE_POSITIVE`, zone `ARCHIVE`,
score 0.0656, six justifications détaillées par outil.

**Méthode.** Chaque hypothèse d'échec vérifiée par preuve directe
(longueurs comparées, arguments tracés, requêtes SQL, logs backend, DTO
source) avant correctif, jamais par supposition relancée au hasard —
cinq itérations de diagnostic pour un seul webhook, cohérent avec la
doctrine du projet établie sur l'IA et les certificats.

---

## 2026-08-08 — Phase 1.2 : premier connecteur bidirectionnel — agents, santé et inventaire système Wazuh (PR #85)

**Contexte.** Après le flux push (alertes, phase 1.1), la phase 1.2
construit le premier connecteur *pull* : la plateforme interroge
activement l'API de gestion Wazuh (distincte du flux d'événements) pour
peupler et enrichir le contexte `assets`. Trois VM réelles allumées côté
SOC pour ce chantier (Ubuntu-Sensor, Windows endpoint, Win10-client),
avec des agents `never_connected`/`disconnected` volontairement laissés
dans Wazuh comme bruit réaliste plutôt que nettoyés avant capture.

**Réalisé — socle `connectors` (domaine).** Bounded context dédié
(ADR-014) : `SocConnector` (entité riche, statuts NOT_CONFIGURED /
DISABLED / CONNECTED / DEGRADED / DISCONNECTED, gardes empêchant tout
`recordSuccess`/`recordFailure` d'écraser NOT_CONFIGURED ou DISABLED),
`ConnectorDescriptor` (version et capacités détectées), `SyncRun`
(cycle de vie d'une synchronisation). Extension additive d'`Asset`
(operatingSystem, lastSeenAt, externalId/externalSource pour la
réconciliation, puis hardwareSummary) — **jamais de champ retiré ni de
contrat cassé**, conformément à la clause « additive-only » de
l'ADR-014.

**Réalisé — connecteur agents → actifs.** `AgentInventoryPort` (ACL
neutre côté application) ; trois adaptateurs simulation/live/disabled
sélectionnés par `smartsoc.connectors.wazuh.mode` (le mode `disabled`
est **obligatoire**, pas un simple raccourci : sans lui, Spring ne
trouve aucun bean pour le port et le contexte refuse de démarrer) ;
authentification Wazuh en **deux temps** (Basic Auth → JWT, validité
~15 min, mis en cache 13 min côté plateforme) — modèle différent des
clés d'API statiques des services IA, découvert en pratique plutôt que
supposé identique. Réconciliation en **transaction par élément**
(`AgentReconciliationService`, un bean séparé de l'orchestrateur non
transactionnel `AgentSyncService`, même schéma que
`IndicatorFeedIngestionService`) : un agent rejeté n'empoisonne jamais
la synchronisation des suivants. Scheduler configurable
(`@EnableScheduling`, absent du projet jusqu'ici).

**Réalisé — santé du gestionnaire.** `ManagerStatsPort` interroge
`/manager/status` (10 démons critiques identifiés parmi la liste
complète — 6 autres, comme `clusterd` ou `maild`, sont légitimement
arrêtés sur un déploiement mono-nœud sans faire échouer la santé) : un
démon critique arrêté fait passer le connecteur en **DEGRADED**, une
valeur du domaine définie dès le départ mais encore jamais déclenchée
en pratique avant cette PR.

**Réalisé — inventaire système (syscollector).** Troisième capacité :
`SystemInventoryPort` interroge `/syscollector/{id}/os` et
`/hardware` par agent pour enrichir `Asset` d'une description OS plus
riche et d'un résumé matériel, en **best-effort** — un échec syscollector
ne bloque jamais la synchronisation de base, et `hardwareSummary` ne
s'efface jamais sur une valeur `null` (contrairement à `operatingSystem`,
toujours écrasé), car cet appel est distinct du reste et peut légitimement
rater un cycle sans perte réelle de donnée.

**Cinq bugs réels trouvés dans mes propres tests, tous par preuve plutôt
que par supposition.**
1. Deux `Instant.now()` distincts générés pour le « même » objet
   `AgentSnapshot` (record) construit deux fois cassaient le
   *stub-matching* Mockito par égalité — corrigé en réutilisant la même
   instance pour le stub et l'assertion.
2. Faux positif Mockito « strict stubbing argument mismatch » quand
   `doThrow().when(mock).methode(argSpécifique)` coexistait avec d'autres
   appels non stubbés de la même méthode — corrigé avec `lenient()`.
3. Test écrit avec une hypothèse de domaine fausse (`recordFailure`
   sensé faire passer un connecteur NOT_CONFIGURED à DISCONNECTED) alors
   que la garde du domaine — déjà correcte et déjà testée — interdit
   précisément cela : le test a été corrigé, pas le code.
4. Assertion d'authentification WireMock fragile : `WazuhTokenCache` est
   un singleton Spring partagé entre méthodes de test du même contexte,
   donc un jeton mis en cache par un test antérieur rend un second appel
   `/authenticate` absent dans le test suivant — comportement correct
   (le cache fonctionne), assertion corrigée pour vérifier la présence du
   Bearer plutôt que la fraîcheur de l'authentification.
5. Fixture Wazuh figée (5 agents) mais test écrit sur la base d'une
   capture d'écran postérieure montrant un 6ᵉ agent apparu depuis :
   compteurs corrigés à 5 agents / 4 actifs non-manager, avec commentaire
   explicite sur le caractère versionné (non live) de la fixture.

**Réalité des données Wazuh, découverte en capturant de vraies réponses
plutôt que supposée.** Agent `never_connected` sans objet `os` ni champ
`lastKeepAlive` (absent, pas `null`) ; `ip` parfois littéralement
`"any"` ; le Manager lui-même (id `"000"`) porte un `lastKeepAlive`
sentinelle `9999-12-31T23:59:59+00:00`, exclu de la synchronisation des
actifs (c'est le SIEM, pas un poste supervisé) ; deux agents distincts
peuvent partager la même IP (réconciliation par id Wazuh uniquement) ;
`board_serial` peut être la chaîne littérale `"None"` (fuite Python de
l'API Wazuh) ; RAM syscollector exprimée en kilo-octets.

**Vérification.** Domaine 158, application 85, infrastructure 39,
api 166 — **448 tests verts**, zéro régression sur `clean verify`
4 modules. Test d'intégration WireMock dédié prouvant l'enrichissement
syscollector de bout en bout avec les valeurs réelles capturées sur
l'agent 004 (WIN10-CLIENT, Windows 10 Home 22H2 build 19045.3803, i5-
12450H, 1 cœur, 2,0 Go RAM) : la description syscollector l'emporte
bien sur celle, plus pauvre, de la liste d'agents de base. Trois
commits sur la branche `feature/connectors-wazuh-agents-backend`
(PR #85) : agents (6bf19a8), santé du manager (09606bb), inventaire
système (dbd1d71 + fixtures 3bb8e48).

**Complément le même jour — section « Connecteurs » de la console
(PR #85, même branche).** Le backend étant terminé et tournant en réel
(scheduler en mode simulation par défaut), la console Paramètres
affichait encore le placeholder « à venir » — dernière pièce
manquante de la phase 1.2. `GET /api/v1/connectors` (déjà construit)
alimente une carte par connecteur SOC : **seul Wazuh a un adaptateur
backend aujourd'hui**, donc seule sa carte affiche de vraies données
(statut, dernière sonde/synchronisation, capacités) — les quatre
autres (OpenSearch, MISP, VirusTotal, Shuffle) restent des cartes
honnêtes « phase à venir » plutôt que des `NOT_CONFIGURED` trompeurs,
même doctrine que le reste du module Paramètres.

**Constat fait en lisant le code plutôt que supposé.** `detectedVersion`
et `capabilities` de `ConnectorDescriptor` sont conçus depuis l'ADR-014
v1.1 mais **jamais réellement peuplés** : `WazuhAgentMapper.
detectManagerVersion()` existe et est testé isolément, mais n'est
appelé nulle part dans `AgentSyncService` — celui-ci réutilise
`connector.getDescriptor()` tel quel à chaque cycle. Vérifié en
navigateur contre le backend réel reconstruit : la carte Wazuh affiche
bien « Non détectée » / « Aucune capacité confirmée », jamais une
valeur inventée. Écart de câblage réel, signalé pour un futur lot
plutôt que corrigé ici (hors périmètre de ce PR frontend).

**Vérification réelle.** Backend Docker reconstruit depuis le code du
jour (le conteneur tournant datait de la veille, sans ce PR) ; section
testée dans le navigateur contre l'API réelle après connexion admin :
Wazuh « Connecté », dernier cycle « Succès · 3 traités · 0 rejetés » ;
navigation par clic entre sections vérifiée. `tsc --noEmit` propre,
3 tests Vitest verts, lint propre, build de production réussi.

**Reste en phase 1.** Phase 1.3 — vulnérabilités Wazuh, un module
d'ampleur comparable à ce qui vient d'être livré ; revalidation MISP
avant la phase 2, explicitement différée par Imane le temps de
stabiliser la connectivité de cet outil. Écart signalé ci-dessus
(câblage du `CapabilityProbe`) à traiter en tâche séparée.

---

## 2026-08-09 — Phase 1.3 : vulnérabilités Wazuh — de la question au module complet (PR #86, #87)

**Point de bascule architectural, tranché en réel plutôt que deviné.**
La baseline documentait un fork non résolu pour cette étape : les
vulnérabilités viennent-elles de l'API de gestion Wazuh ou de
l'Indexer (OpenSearch) ? La réponse conditionne toute la conception
(port différent, client différent, ordre des phases). Plutôt que de
suivre l'hypothèse la plus probable (Wazuh v4.12.0, capturé en phase
1.2, est postérieur au retrait de l'API classique de vulnérabilités),
vérification menée en direct avec Imane sur `vm-siem` :
`GET /vulnerability/004` de l'API renvoie `404 Not Found` avec un
jeton confirmé valide (contrôle croisé sur `/agents`, qui répond
normalement) ; l'Indexer, lui, porte l'index
`wazuh-states-vulnerabilities-vm-siem` peuplé de **1994 documents
réels**, schéma capturé en fixture
(`docs/integration/fixtures/wazuh/vulnerabilities-indexer-sample.json`) :
agent, host.os, package, vulnerability (id CVE, sévérité, score CVSS,
description, dates, scanner). **Source confirmée : l'Indexer.**

**Conséquence sur l'ordonnancement, assumée et documentée plutôt que
suivie aveuglément.** Le plan liait cette conditionnelle à la phase 4
(« si Indexer, la 1.3 s'exécute après OpenSearch/Hunting »). Décision
prise avec Imane : construire le client OpenSearch partagé
**maintenant**, dans la 1.3, plutôt que d'attendre la phase 4 — le
schéma des vulnérabilités est déjà mûr et vérifié en réel, alors que
celui des alertes (visé par le futur mapping Hunting) n'a que
quelques heures de recul depuis la phase 1.1. Le chemin critique et le
diagramme de dépendances (`PROJECT-PLANNING.md`) ont été corrigés en
conséquence, pas seulement le code.

**Réalisé — module complet (PR #86, backend).** Contexte domaine
`vulnerabilities` : entité `Vulnerability`, identité immuable
`(source, externalId)` — le doc id du scanner, qui encode déjà
agent + paquet + CVE — cycle `OPEN`/`RESOLVED`, même patron
déclarer/rafraîchir qu'`Indicator` mais `resolve()` **idempotent**
(constat de réconciliation automatique, pas une décision d'analyste
comme `Indicator.revoke`) ; **une vulnérabilité résolue retrouvée
rouvre automatiquement** — une récurrence réelle (ex. rétrogradation
de paquet) ne doit jamais rester masquée. `VulnerabilityFeedPort` :
**un seul appel pour tout le parc** (l'Indexer le permet, contrairement
aux ports Wazuh paginés par agent) ; réconciliation par actif en
transaction par élément (`VulnerabilityReconciliationService`, même
isolement qu'`AgentReconciliationService`), orchestrateur qui regroupe
les trouvailles par agent, résout chaque agent vers son `Asset` déjà
connu (rejet tracé et compté si l'agent est inconnu, jamais un crash),
puis réconcilie présence **et absence** (`resolveMissing`) actif par
actif. Client OpenSearch : Basic Auth à chaque requête — **vérifié en
réel** qu'il n'y a pas de jeton à rafraîchir, contrairement à l'API de
gestion Wazuh. ACL avec parsing défensif de la sévérité (valeur
inconnue → `UNTRIAGED` journalisé, jamais un cycle entier en échec).
Migration V18, `GET /api/v1/vulnerabilities` (filtres
assetId/status/severity/recherche, pagination) et `/{id}`.

**Deux bugs réels trouvés dans mes propres tests, déjà rencontrés sur
le connecteur Wazuh (phase 1.2) — reconnus immédiatement cette fois.**
Faux positif Mockito « strict stubbing » sur un `doThrow` ciblé à côté
d'un appel non stubbé de la même méthode (`lenient()`) ; hypothèse de
règle domaine fausse dans un test (`recordFailure` ne dégrade jamais
un connecteur `NOT_CONFIGURED` — corrigé en semant un connecteur déjà
`CONNECTED`, même remède que sur `AgentSyncServiceTest`).

**Réalisé — fiche d'actif (PR #87, frontend).** Plutôt qu'un nouvel
écran ou item de navigation (les 13 routes du cahier des charges sont
figées depuis la PR #21), section « Vulnérabilités » ajoutée à la
fiche d'actif existante, même patron que « Alertes corrélées » déjà en
place — cohérent avec le critère de fin de la 1.3 : « les
vulnérabilités réelles d'un agent sont visibles et rattachées à son
actif ».

**Vérification réelle, les deux PR.** 476 tests backend verts sur les
4 modules (+28 vs la phase 1.2), zéro régression sur `clean verify` ;
test d'intégration PostgreSQL réel (unicité `(source, external_id)`
vérifiée par une vraie violation de contrainte, réouverture d'une
vulnérabilité résolue, recherche filtrée) ; test d'intégration API
bout en bout (sync agents → sync vulnérabilités → liste HTTP réelle).
Frontend : backend Docker reconstruit depuis `develop` (PR #86
fusionnée), vérifié au navigateur après connexion admin — la fiche de
l'actif `sim-web-01` (connecteur Wazuh simulé, agent `sim-001`)
affiche « Vulnérabilités (1) » avec CVE-2025-3576 / Moyenne /
libgssapi-krb5-2 ; appel réseau réel `GET /api/v1/vulnerabilities?
assetId=...` confirmé 200 OK.

**Méthode.** Le point de bascule architectural (API vs Indexer) a été
vérifié par preuve directe avant d'écrire une seule ligne de domaine —
pas supposé à partir du numéro de version, même si l'hypothèse s'est
révélée juste. Cinq allers-retours de vérification pour trancher un
seul port, cohérent avec la doctrine du projet établie sur l'IA, les
certificats et les alertes Wazuh.

**Reste en phase 1.** Revalidation MISP avant la phase 2, explicitement
différée par Imane. Deux écarts signalés en tâches séparées plutôt que
traités dans ce lot : câblage du `CapabilityProbe` (version/capacités
Wazuh jamais réellement détectées, phase 1.2) et exposition des champs
de synchronisation (`operatingSystem`, `hardwareSummary`, etc.) sur
`AssetResponse`, actuellement invisibles côté API/console malgré leur
présence en base.

---

## 2026-08-09 — Revalidation MISP avant la phase 2

**Contexte.** Différée explicitement par Imane à la fin de la phase 1.2
(« avant de commencer la phase 2 on va retester MISP pour le
valider »), MISP ayant été jugée instable au moment de la baseline.
Relancée côté SOC, testée en réel étape par étape.

**Cinq vérifications réelles, dans l'ordre.**
1. **Réseau** : `ping`/`curl` directs vers `10.100.0.3` depuis le poste
   de travail — joignable, `HTTP 302` sur `/` (redirection de connexion,
   comportement normal d'une appli MISP non authentifiée).
2. **TLS** : `openssl s_client` sur `10.100.0.3:443` — SAN désormais
   `IP:10.100.0.3, DNS:localhost, IP:127.0.0.1` (contre `DNS:localhost`
   seul lors de l'audit ADR-015) : le certificat a bien été régénéré
   côté SOC entre-temps.
3. **API REST** : `GET /servers/getVersion.json` sans clé → `403` avec
   un vrai message MISP (« pass the API key... ») — confirme que
   l'application répond, pas seulement le port.
4. **Authentification** : détour révélateur avant d'aboutir — Imane a
   d'abord collé son **mot de passe web** en clair (changé depuis,
   recommandé immédiatement — jamais reproduit ici) puis le texte
   littéral du paramètre `$mispKey` non substitué. Clé API MISP ≠ mot
   de passe de connexion : rappelé, puis guidé vers Mon Profil → Auth
   keys.
   **Nouvelle clé dédiée créée**, cochée « Read only », plutôt que de
   réutiliser les clés `Wazuh-Integration`/`Wazuh-Integration1` déjà en
   service pour l'enrichissement Wazuh→MISP existant (`custom-misp.py`)
   — éviter de polluer leur suivi d'usage. Testée : `version 2.5.44`.
5. **Données réelles** : `POST /attributes/restSearch` (`limit: 3`) →
   un événement de test **« SmartSOC Test IOC »** avec 3 attributs
   (`ip-dst` 185.220.101.25, `domain` evil-domain.com, `url`
   http://malicious.test), tous `to_ids: true` — capturé en fixture
   (`docs/integration/fixtures/misp/attributes-restsearch-sample.json`)
   pour l'ACL de la future phase 2.

**Écart relevé, pas bloquant mais à corriger avant l'implémentation
réelle.** Le compte porteur de la nouvelle clé est `admin@admin.test`
— le compte administrateur générique, pas un compte MISP dédié en
lecture seule comme `smartsoc-reader` côté Wazuh. Suffisant pour ce
test de connectivité, insuffisant pour la phase 2 elle-même (même
doctrine de moindre privilège que partout ailleurs dans le projet).

**Vérification.** MISP v2.5.44 confirmée, TLS vérifié, authentification
réelle réussie, données réelles lues. Plus rien ne bloque le
démarrage du code de la **phase 2** — reste seulement la création d'un
compte MISP dédié en lecture seule avant l'implémentation.

---

## 2026-08-09 — Phase 2 : connecteur MISP, threat intelligence (PR #90)

**Compte dédié créé avant tout code**, comme prévu à l'issue de la
revalidation ci-dessus : rôle `Read Only` MISP déjà existant (aucune
permission cochée, trouvé tel quel dans la liste des rôles), utilisateur
`smartsoc-reader@smartsoc.local` créé dessus, clé API générée **pour ce
compte depuis le menu déroulant « User » de la fenêtre MISP d'ajout de
clé** — sans jamais se connecter avec lui ni connaître son mot de passe
auto-généré. Revérifiée sur `/servers/getVersion.json` avant tout code.

**Le connecteur le plus simple des cinq, confirmé par les chiffres.**
Zéro modification du domaine : les tests du module `domain` sont restés
à 165 avant et après ce lot. Zéro modification frontend non plus,
constat fait en vérifiant le code existant plutôt que supposé :
`IntelligencePage.tsx`/`IocDetailDrawer.tsx` affichent déjà
`feedSource` (colonne, filtre, tiroir de détail) — construits bien
avant ce chantier, génériques dès l'origine. L'estimation à 1 CÉ
(contre 2,5 pour Wazuh/vulnérabilités) tenait donc, et même en dessous
en réalité côté frontend.

**Réutilisation totale de l'existant plutôt qu'une nouvelle
réconciliation.** `IndicatorFeedIngestionService` — déjà construite
pour le webhook de push `/api/v1/ingest/iocs` (jalon CTI antérieur) —
gère déjà l'upsert (`déclarer`/`rafraîchir`) et la tolérance par
élément. `MispSyncService` (nouvel orchestrateur programmé) ne fait
que traduire « un cycle de synchronisation » en « un lot pour cette
méthode existante » : aucun bean de réconciliation dédié à écrire,
contrairement aux connecteurs Wazuh et OpenSearch.

**Traductions ACL assumées et documentées, jamais des valeurs
inventées.** `ip-src`/`ip-dst` MISP ne distinguent pas IPv4 d'IPv6 :
désambiguïsé par la **forme** de la valeur (présence de « : »), pas
par le type annoncé. Aucun score de confiance natif côté MISP sur un
attribut : dérivé du `threat_level_id` de l'événement (seul signal
analyste disponible sur `restSearch`), traduction documentée plutôt
qu'un nombre sorti de nulle part. TLP laissé `null` faute de tags
fiables dans la réponse observée — le défaut restrictif déjà du
domaine (AMBER) s'applique plutôt qu'un TLP supposé.

**CI rouge deux fois de suite, deux causes réelles distinctes,
diagnostiquées par lecture des logs plutôt que par supposition.**
1. `ConnectorControllerIntegrationTest.aConnectorNeverSynchronizedIsHonestlyReported404NotFabricated`
   supposait qu'aucun service de synchronisation MISP n'existait — vrai
   avant ce lot, plus après : le planificateur MISP tourne dès le
   contexte applicatif (mode simulation par défaut) et peut avoir créé
   sa ligne avant que ce test ne s'exécute (délai initial 45 s,
   dépassé sur un run complet de suite — jamais reproduit en local
   malgré plusieurs `clean verify`, seulement en CI où les exécutions
   sont plus lentes). Corrigé en visant `VIRUSTOTAL`, toujours
   authentiquement jamais synchronisé.
2. Gate SonarCloud : `new_reliability_rating` (règle S5841 —
   `allSatisfy()` sur une liste non vérifiée non-vide au préalable) et
   `new_coverage` à 65,1 % (seuil 80 %) — le mode live MISP
   (adaptateur, client Feign, configuration d'authentification)
   n'était exercé par aucun test, contrairement aux connecteurs
   Wazuh/OpenSearch qui ont chacun leur test WireMock de mode live.
   Corrigé par l'ajout de ce test manquant plutôt que par
   contournement — au passage, retrait d'une méthode `modeOrDefault()`
   ajoutée sur le nouveau record `Misp` mais jamais appelée nulle
   part : code mort constaté en cherchant la source du trou de
   couverture. Le même défaut existe déjà, non testé, sur les records
   `Wazuh`/`OpenSearch` — antérieur à ce lot, signalé en tâche séparée
   plutôt que traité ici.

**Vérification réelle.** 489 tests backend verts sur les 4 modules
(+13 vs la phase 1.3), zéro régression, zéro nouveau test domaine.
Test d'intégration API bout en bout (sync simulée → lecture réelle
via `/api/v1/iocs?feedSource=misp`, connecteur `CONNECTED`,
re-synchronisation prouvée idempotente) et test WireMock de mode live
(authentification vérifiée sur le vrai en-tête envoyé, dégradation
gracieuse). Backend Docker reconstruit depuis `develop` fusionné,
vérifié au navigateur après connexion admin : l'écran Threat
Intelligence affiche bien les deux indicateurs simulés MISP
(`203.0.113.42`, `phishing-simule.test`, `feedSource=misp`,
TLP:AMBER) mêlés aux indicateurs de démonstration existants, sans
aucun changement de code frontend.

**Reste en phase 1/2.** Écarts signalés (câblage de la détection version/
capacités des connecteurs Wazuh et OpenSearch, exposition des champs
de synchronisation sur `AssetResponse`, retrait du code mort
`modeOrDefault()` résiduel) en tâches séparées. Phase 3 (VirusTotal) et

---

## 2026-08-09 — Phase 3 : connecteur VirusTotal, réputation d'observables (PR #93)

**Choisie explicitement par Imane** face à la phase 4 (Hunting OpenSearch,
jugée prématurée — l'index d'alertes est encore jeune) et aux tâches en
attente. Confirmation explicite de posséder une clé API VirusTotal avant
tout code, doctrine des échantillons réels appliquée une fois de plus :
deux `curl` réels exécutés par Imane elle-même avec sa propre clé
(jamais partagée en clair) contre l'API v3 pour l'IP `8.8.8.8` et le
domaine `google.com`, réponses collées intégralement et versionnées en
fixtures (`docs/integration/fixtures/virustotal/`) avant d'écrire la
moindre ligne d'ACL.

**Un connecteur de nature différente des quatre précédents : à la
demande, pas au fil de l'eau.** Wazuh, MISP et (à venir) OpenSearch
synchronisent en tâche de fond ; VirusTotal est interrogé un observable
à la fois, sur demande d'un analyste — le quota gratuit (4 requêtes/min,
500/jour) l'impose. **Cache obligatoire, pas une optimisation** :
`ObservableReputation` (nouvelle entité du domaine, identité
`(source, type, value)`) sert toute lecture dont le dernier relevé a
moins de `cache-ttl-hours` (24 h par défaut), et ne rappelle l'API que
si le cache est absent ou périmé.

**Verdict dérivé, jamais fabriqué.** Les réponses IP/domaine/URL de
VirusTotal ne renvoient aucun champ de verdict unique, seulement des
compteurs par catégorie (`last_analysis_stats`). Règle documentée
« pire cas gagne » (`malicious > 0` → `MALICIOUS`, sinon `suspicious > 0`
→ `SUSPICIOUS`, etc.) implémentée dans `ObservableReputation.deriveVerdict`
— une traduction transparente des compteurs réels, pas une valeur
inventée, conforme à la doctrine du projet.

**Protection de quota réelle, pas symbolique.** Seul connecteur du
projet à empiler `@CircuitBreaker` **et** `@RateLimiter` sur le même
appel — les autres connecteurs n'ont qu'un disjoncteur. Effet de bord
mineur constaté et non corrigé : les deux aspects partageant la même
méthode de repli, un échec loggue l'avertissement deux fois (cosmétique,
sans impact fonctionnel).

**Bug réel trouvé par mon propre test WireMock, de la même famille
qu'un bug déjà documenté ailleurs dans ce dépôt (rotation de refresh
token).** `ObservableReputationService.getReputation()` appelait
`recordFailure()` (écriture du statut `DISCONNECTED`) puis relançait
l'exception `SocConnectorException` — dans une méthode `@Transactional`
simple, cette relance déclenche le rollback Spring par défaut et annule
silencieusement l'écriture de `recordFailure()` qui la précédait dans la
même transaction. Détecté par
`ReputationLiveIntegrationTest.degradesGracefullyWhenVirusTotalIsDown`
qui attendait `DISCONNECTED` et observait `CONNECTED`. Corrigé par
`@Transactional(noRollbackFor = SocConnectorException.class)`, comme
pour `InvalidRefreshTokenException` sur la rotation de jeton.

**CI rouge une fois, deux causes réelles distinctes, jamais reproduites
en local malgré plusieurs `clean verify` complets — diagnostiquées par
lecture des logs CI.**
1. `ConnectorControllerIntegrationTest.aConnectorNeverSynchronizedIsHonestlyReported404NotFabricated`
   ciblait VIRUSTOTAL, authentiquement jamais synchronisé avant ce lot —
   plus vrai après : le nouveau code VirusTotal est désormais exercé par
   d'autres tests du même contexte Spring partagé. Corrigé en retargetant
   sur SHUFFLE, seul connecteur du dépôt sans aucune implémentation
   backend nulle part.
2. `AuditLogControllerIntegrationTest.entriesCanBeFilteredByATimeWindow`
   supposait qu'aucune entrée d'audit n'existait plus de 60 secondes
   avant l'exécution du test — vrai seulement si la classe s'exécute
   bien en dessous de 60 s ; ce lot ajoutant plusieurs classes
   `@SpringBootTest` supplémentaires au même job CI, la charge totale a
   dépassé ce seuil implicite sur le runner. Corrigé en remplaçant la
   borne relative par une borne absolue fixe (`2000-01-01T00:00:00Z`),
   insensible à la durée de la suite.

**Vérification réelle.** 524 tests backend verts sur les 4 modules
(domaine 173, application 101, infrastructure 59, api 191 — +35 vs la
phase 2), zéro régression, `clean verify` complet vert. ACL vérifiée
contre les deux échantillons réels capturés (IP et domaine, compteurs
`malicious`/`suspicious`/`harmless`/`undetected` fidèlement traduits).
Test WireMock de mode live couvrant authentification (en-tête
`x-apikey`), dégradation gracieuse et service du cache périmé en cas de
panne. CI complète verte (CodeQL, SonarCloud, Trivy, build, qualité)
après les deux correctifs.

**Frontend (PR #95), contrairement à MISP.** MISP n'avait nécessité
aucun changement frontend (écran existant déjà générique) ; VirusTotal
est à la demande et n'avait aucune surface UI pour le déclencher.
Vérification par lecture de code (`grep reputation` sur `frontend/src`)
plutôt que supposée : zéro résultat, confirmant le besoin. Bouton
« Vérifier la réputation » ajouté dans `IocDetailDrawer` (Threat
Intelligence uniquement, choix explicite d'Imane — pas le tiroir
d'observable d'alerte), réservé aux mêmes rôles d'écriture que le RBAC
backend (`ADMIN`/`SOC_MANAGER`/`SOC_ANALYST`) : un VIEWER ne voit même
pas la section, cohérent avec le fait qu'il recevrait un 403 de toute
façon. Verdict affiché via un chip dérivé (jamais recalculé côté
client), type EMAIL explicitement signalé comme non supporté plutôt que
proposer un bouton qui échouerait.

**Régression découverte en testant au navigateur, même défaut que MISP
avant son intégration.** La carte VirusTotal de l'écran Connecteurs
(`ConnectorsSection.tsx`) restait figée sur `implemented: false` /
« Phase 3 » malgré le connecteur déjà branché côté backend — capture
d'écran fournie par Imane après un premier passage en revue. Corrigé
en un mot (`implemented: true`), vérifié au navigateur : la carte passe
bien à « Connecté » avec sa sonde réelle.

**Vérification réelle complète.** `npm run build` (tsc + vite) vert ;
47 tests frontend verts dont 2 nouveaux pour le tiroir (déclenchement
+ affichage du verdict, absence du bouton pour un rôle VIEWER) ; image
Docker backend reconstruite depuis `develop` fusionné (le conteneur
tournait encore sur le code d'avant la fusion de la PR #93) ; test de
bout en bout au navigateur connecté en Admin : clic sur un IOC IPv4 réel
→ requête `GET /api/v1/reputation` → 200 → verdict « Inoffensif » et
compteurs réels affichés, aucune erreur console ; écran Connecteurs
revérifié après le correctif.

---

## 2026-08-10 — Passage en mode live des 4 connecteurs SOC (PR #97, #98, #101)

**Contexte : une question simple d'Imane a révélé que rien n'était
réellement branché.** Après la Phase 3, la question posée était directe
— « pourquoi mes actifs Wazuh, Ubuntu, Windows Server n'apparaissent
jamais dans la plateforme ? ». Vérification en base : les seuls actifs
Wazuh présents étaient `sim-*`, des données de simulation. Diagnostic en
trois couches, chacune un vrai bug distinct.

**Bug 1 — `docker-compose.yml` ne transmettait aucune variable
`SMARTSOC_CONNECTORS_*`.** Même un `.env` bien rempli n'aurait eu aucun
effet : les 4 connecteurs restaient forcés en simulation. `.env.example`
listait par ailleurs une section « Intégrations SOC » obsolète
(`WAZUH_API_URL`, `MISP_API_KEY`…) datant d'avant l'implémentation
réelle des connecteurs. Corrigé (PR #97) : variables correctes câblées
avec défaut `simulation` (comportement inchangé tant que `.env` ne les
renseigne pas).

**Vérification des identifiants, un par un, sans jamais afficher de
secret en clair.** Imane a rempli son `.env`, mais avec des URLs encore
sur `localhost` et le compte `admin` Wazuh (dashboard, pas API) au lieu
de `smartsoc-reader`. Diagnostiqué par tests directs depuis le conteneur
(`wget` + `base64` pour l'en-tête `Authorization`, jamais les valeurs
elles-mêmes affichées) contre les vraies adresses `10.100.0.x` :
- MISP et VirusTotal : valides du premier coup (`200 OK`).
- Wazuh : `401`, `admin` étant le compte du Dashboard web — retrouvé et
  réinitialisé le mot de passe de `smartsoc-reader` (compte API dédié,
  créé en phase 1.2) dans **Server Management → Security** du Dashboard
  Wazuh. Revalidé : jeton JWT obtenu, `sub: smartsoc-reader`.
- OpenSearch (Indexer) : troisième système de comptes, distinct de
  Wazuh et MISP. Compte `smartsoc-reader` créé sous **Indexer
  management → Security → Internal users**, rôle `readall` (lecture
  seule sur tous les index, déjà disponible, jamais un compte de
  service partagé). `403` sur les endpoints cluster-level (`_cat/indices`,
  attendu — `readall` ne les couvre pas), `200` sur une vraie recherche
  (`wazuh-states-vulnerabilities-*`, 1994 vulnérabilités réelles lues) :
  ce qui compte pour la plateforme fonctionne.

**Bug 2 — truststore Java jamais construit (ADR-015, prévu comme
livrable de la phase 1.2, oublié).** Passage effectif en mode `live` →
`PKIX path building failed` sur les 3 connecteurs internes (Wazuh,
OpenSearch, MISP) : la JVM ne fait pas confiance à leurs certificats
(deux autorités internes distinctes — `SmartSOC Root CA` pour Wazuh API
et MISP, autorité propre pour l'Indexer). Certificats **publics**
(aucune clé privée) extraits des vrais serveurs, versés dans
`docker/truststore/` (exception dédiée ajoutée au `.gitignore`, sinon
`*.pem` est bloqué globalement). `Dockerfile` : construit un magasin
PKCS12 à partir du `cacerts` par défaut de la JVM (conserve la confiance
publique, ex. VirusTotal) plutôt que de le remplacer (PR #98).

**Résultat, vérifié en réel après reconstruction complète de l'image.**
MISP a synchronisé 1000 indicateurs réels ; Wazuh 5 agents réels (0
rejeté) ; OpenSearch 659 vulnérabilités réelles. L'écran Actifs affiche
désormais **de vraies machines** : `win10-client` (Windows 10 Home
22H2), `windows-endpoin` (Windows Server 2025 Datacenter Azure Edition),
`ubuntu-sensor` (Ubuntu 24.04.4 LTS) — IP réelle, description
« Découvert automatiquement via le connecteur Wazuh », 73 alertes
corrélées et 659 vulnérabilités réelles sur `win10-client` à elle
seule. Nettoyage des données `sim-*` devenues obsolètes (2 assets, leurs
vulnérabilités liées) après confirmation explicite d'Imane — un premier
nettoyage accidentel de deux actifs légitimement décommissionnés par
Imane elle-même a été détecté et corrigé immédiatement (`post-1`,
`srv-web-01` remis en `DECOMMISSIONED`), leçon retenue : ne jamais
modifier un état sans confirmer d'abord à qui appartient le changement
observé.

**Bug 3, trouvé en répondant à une question sur l'écran Connecteurs.**
Même défaut que MISP et VirusTotal avant leur intégration : les cartes
OpenSearch et VirusTotal affichaient un état figé. OpenSearch restait
sur « Phase 4 » alors que son flux vulnérabilités tournait déjà en
réel — `implemented: false` jamais mis à jour (PR #101). VirusTotal
affichait « Déconnecté » à cause d'un disjoncteur ouvert par le quota
dépassé pendant les tests (`wait-duration-in-open-state: 30s` — pas un
bug, la protection prévue ; confirmé par Imane : le prochain vrai appel
« l'allume » de nouveau).

---

## 2026-08-10 — Statut de connexion Wazuh exposé sur les Actifs (PR #99, #100)

**Trouvé en répondant à une observation d'Imane** (« ces 3 réel 004,
005, 007 il faut afficher aussi leur état réel si il sont disconnected
ou connected ») : la console affichait « Actif » pour tous les actifs
synchronisés, y compris un agent Wazuh réellement déconnecté (`WIN10-CLIENT`,
capture d'écran du Dashboard Wazuh à l'appui). Cause : `WazuhAgentMapper`
ignorait silencieusement le champ `status` de l'API Wazuh depuis le
tout début (jamais capturé, donc jamais visible nulle part) —
confusion involontaire entre le cycle de vie de l'inventaire
(`AssetStatus`) et l'état de connexion rapporté par l'agent.

**Exposition complète, jamais partielle.** En creusant, `AssetResponse`
n'exposait non plus **aucun** des champs déjà captés par les connecteurs
(`operatingSystem`, `lastSeenAt`, `hardwareSummary`) — silencieusement
absents de l'API depuis leur introduction, corrigés dans le même lot.

- Domaine : `AgentConnectionStatus` (`ACTIVE`/`DISCONNECTED`/`NEVER_CONNECTED`,
  valeurs réelles observées), champ additif sur `Asset`, threadé dans
  `applySyncMetadata` — reflète l'état COURANT rapporté par la source
  (contrairement à `hardwareSummary`, qui ne s'efface jamais sur une
  absence ponctuelle).
- ACL Wazuh : traduit `dto.status()`, dégradation silencieuse sur une
  valeur non reconnue.
- Frontend : colonne « Connexion » dans la liste Actifs + chip dans le
  tiroir (vert Connecté/rouge Déconnecté/gris Jamais connecté), absente
  pour un actif enregistré à la main.

**Vérification réelle.** 528 tests backend verts (+4), 47 tests
frontend (3 échecs observés lors d'un run en pleine charge machine —
confirmés comme flakiness pure en isolant chaque suite, aucune
régression). Vérifié au navigateur contre le labo SOC réel : `win10-client`
→ Déconnecté, `windows-endpoin`/`ubuntu-sensor` → Connecté, identique
au Dashboard Wazuh réel.

---

## 2026-08-10 — Phase 4 : Threat Hunting live sur l'Indexer OpenSearch (PR #102)

**Le blocage documenté depuis le début, enfin levé.** Le javadoc de
`HuntExecutionPort` était explicite depuis sa création : le mode `live`
était différé « jusqu'à disposer d'un schéma d'index réel plutôt que
deviné ». Avec les 4 connecteurs désormais en live, deux échantillons
réels ont été capturés contre `wazuh-alerts-*` (10 000+ documents
réels) avant d'écrire une ligne de code — même discipline que chaque
connecteur précédent : une recherche filtrée (`agent.name = WIN10-CLIENT`,
3572 correspondances réelles) et une agrégation par bandes de niveau
(`rule.level`, répartition réelle INFO 2105 / LOW 997 / MEDIUM 316 /
HIGH 19 / CRITICAL 135), forme de réponse confirmée avant toute
implémentation.

**Décision de conception validée avec Imane avant tout code : la bande
de sévérité.** Contrairement aux vulnérabilités (champ `severity`
textuel natif côté Wazuh), une alerte brute ne porte que `rule.level`
(numérique, 0-15) — aucune correspondance documentée nulle part dans ce
dépôt puisque la classification réelle se fait hors dépôt (script SOC,
ADR-005). Bandes standard Wazuh retenues sur confirmation explicite :
0-3 INFO, 4-6 LOW, 7-9 MEDIUM, 10-13 HIGH, 14-15 CRITICAL — partagées
entre l'agrégation OpenSearch et la traduction du domaine, une seule
source de vérité.

**`STATUS`/`SOURCE` : une notion qui n'existe simplement pas côté
document brut.** Une alerte OpenSearch n'a jamais de statut de triage
(jamais persistée) ni d'autre source que « wazuh » (seul index
interrogé). Plutôt que d'inventer un filtre OpenSearch inexistant, une
condition qui exige une autre valeur est court-circuitée en résultat
vide côté Java — décision explicite, documentée en javadoc.

**Mapping fidèle, jamais partiel.** `OpenSearchAlertMapper` traduit
chaque hit en `Alert` du domaine via `Alert.ingest()` (jamais persisté
— lecture seule) ; le document **complet** (pas la vue typée partielle
`OpenSearchAlertDocument`) est préservé dans `rawPayload` via le
`JsonNode` brut, pour ne jamais perdre de champ à l'investigation. Un
document individuellement mal formé est dégradé silencieusement (loggué,
écarté) plutôt que d'échouer toute la page de résultats — même doctrine
que `AgentSnapshot`.

**Zéro changement du port, du service, de l'API ou de l'écran** — le
critère de fin de la phase 4 tenu par construction : `LiveHuntExecutionAdapter`
implémente exactement le même `HuntExecutionPort` que la simulation,
`HuntApiMapper` reste générique. Seul changement structurel :
`SimulatedHuntExecutionAdapter`, jusque-là le seul adaptateur sans
garde, a reçu son `@ConditionalOnProperty` (sinon bean ambigu avec le
nouvel adaptateur live) ; `DisabledHuntExecutionAdapter` ajouté pour
compléter le trio (ADR-014 amendement v1.1).

**Vérification réelle.** 543 tests backend verts (+15) : mapper ACL sur
échantillons réels (bande de sévérité, dégradation silencieuse, document
complet préservé), traduction de requête (chaque champ/opérateur,
court-circuit STATUS/SOURCE, pagination, troncature `track_total_hits`).
Aucun test WireMock pour ce connecteur précisément (constat : OpenSearch
n'en a jamais eu, même pour le flux vulnérabilités de la phase 1.3 —
écart pré-existant, pas introduit ici) ; compensé par une vérification
manuelle réelle poussée (requêtes brutes contre l'Indexer avant tout
code) et un test unitaire complet sur le client Feign mocké. Chasse
`SEVERITY = CRITICAL` exécutée au navigateur (par Imane elle-même,
capture d'écran à l'appui) contre le labo SOC réel : **136
correspondances réelles en 1456 ms**, mêmes alertes que celles capturées
en fixture (`WIN10-CLIENT`, « Executable file dropped in folder
commonly used by malware », niveau 15).

**Reste ouvert.** Écart pré-existant signalé, pas introduit ici :
absence de test WireMock live pour OpenSearch (vulnérabilités ET
hunting).

---

## 2026-08-10 — Phase 5 (1/2) : contrôle d'agents Wazuh, premier redémarrage réel (PR #105)

**La première fonctionnalité du projet à AGIR sur le monde réel**, pas
seulement le lire. Portée volontairement réduite à confirmation :
redémarrage d'agent uniquement pour ce lot, l'active-response (exécution
de commande arbitraire sur une machine distante) reportée à un prochain
lot — mérite sa propre conception (liste blanche de commandes), décision
prise avec Imane avant tout code plutôt que d'élargir le périmètre par
défaut.

**Compte dédié créé avec Imane, pas à pas, comme pour chaque connecteur
précédent — mais un cran de rigueur au-dessus.** Avant même de coder,
vérification que le compte de lecture `smartsoc-reader` n'a AUCUNE
permission d'écriture (`GET /security/users/me` : rôle `readonly`, zéro
action au-delà de `*:read`) — confirme qu'un compte séparé est
obligatoire, pas une simple précaution. Rôle Wazuh personnalisé
`agent_control` construit avec Imane dans le Dashboard : une politique
sur mesure `agent_restart` (`agent:restart` sur `agent:id:*`) combinée
à la politique **réservée** Wazuh `agents_commands_agents`
(`active-response:command`) plutôt qu'une politique dupliquée à la
main — Imane a trouvé cette politique existante elle-même, corrigeant
au passage une erreur de nom d'action de ma part (`active:command` au
lieu du vrai `active-response:command`). Compte `smartsoc-actuator`
créé avec ce seul rôle. Authentification revérifiée en réel avant tout
code : jeton JWT obtenu, permissions confirmées **exactement**
`agent:restart` + `active-response:command`, rien de plus.

**Identité complètement séparée de la lecture, jusqu'au bout de la
chaîne.** Pas seulement un compte différent : toute la chaîne
d'authentification Wazuh (client Basic Auth, cache de jeton JWT, client
Bearer) dupliquée sous un nom distinct (`WazuhActions*`), avec son
propre bouton de mode (`wazuh.actions.mode`, indépendant de
`wazuh.mode`) — la lecture peut rester en live pendant que les actions
restent en simulation, et inversement. Décision délibérée : jamais un
compte "lecture" qui se retrouverait silencieusement élargi.

**Garde-fous non négociables du plan d'architecture, tous appliqués
dans un point de passage unique (`SocActionService`), aucun laissé à
l'appelant :**
- Cible confirmée explicitement par son hostname exact (pas seulement
  un UUID d'URL) — un clic sur la mauvaise ligne échoue plutôt que
  d'agir sur la mauvaise machine.
- Motif obligatoire, même doctrine que la révocation d'un IOC.
- Plafond de 3 tentatives/heure sur le même actif — `AuditLogQuery`
  étendu d'un filtre `targetType`/`targetId` (additif), le journal
  d'audit existant réutilisé plutôt qu'une table dédiée au rate-limiting.
- **Aucun retry**, ni dans le service ni dans l'adaptateur infrastructure
  (`@CircuitBreaker` seul, jamais `@Retry`) : rejouer une action, c'est
  agir une seconde fois sur une vraie machine.
- Audit nominatif systématique (acteur, IP, cible), succès ET échec —
  un seul type d'événement d'audit (`WAZUH_AGENT_RESTART_REQUESTED`),
  l'issue portée par `details`, pour que le plafond horaire compte
  toute tentative réelle en un seul filtre.

**Le cas le plus trompeur pour ce type d'appel, anticipé et testé.**
L'API Wazuh peut répondre `HTTP 200` globalement tout en signalant
l'agent VISÉ dans `failed_items` — un succès apparent qui masque un
échec réel sur la cible précise. `LiveAgentControlAdapter` vérifie
explicitement cette liste avant de déclarer un succès, couvert par un
test WireMock dédié à ce scénario précis.

**Vérification réelle, jamais de vrai redémarrage déclenché.** 560
tests backend verts (+17, dont un test WireMock live couvrant
l'authentification dédiée, l'échec partiel masqué, et l'absence de
retry après panne). Identifiants `smartsoc-actuator` authentifiés
contre l'API Wazuh réelle. Endpoint vérifié de bout en bout en Docker
reconstruit, **strictement en mode simulation** — log explicite
« [simulation] Would restart... (no real effect) », audit nominatif
confirmé en base (acteur, IP, cible, motif, issue). Le mode live n'a
jamais été déclenché pour de vrai dans cette session : décision
délibérée, laissée à un prochain test explicite avec confirmation en
temps réel — cohérent avec la doctrine du plan d'architecture
(« accorder un pouvoir d'action exige que toute la chaîne soit
éprouvée »).

---

## 2026-08-10 — Phase 5 (1/2, suite) : bouton de redémarrage d'agent (PR #107)

**Déclencheur ajouté dans le tiroir Actif**, visible uniquement pour un
actif Wazuh-managé (`externalSource === 'wazuh'`), en écriture
(ANALYST+), non décommissionné. Confirmation par **saisie exacte du
hostname** (pas un simple clic) — même garde-fou que le serveur
applique de toute façon (`SocActionService` revalide tout), ce contrôle
côté client évite juste d'attendre un refus serveur pour une erreur
évidente. Motif obligatoire.

**Bug réel trouvé en écrivant le test, pas en production —
heureusement.** `EditAssetDialog` et le nouveau `RestartAgentDialog`
partageaient la MÊME clé React au premier rendu (`${asset.id}-false`,
puisque leurs deux états `open` initiaux valent tous les deux `false`)
— React avertit sur les clés dupliquées entre frères, comportement non
garanti. Corrigé en préfixant chaque clé par le nom du composant. Un
faux départ de diagnostic instructif au passage : la piste d'abord
suivie (clé dépendante de l'état `open`, forçant un remount à chaque
ouverture) n'était PAS la cause réelle — la vraie cause, révélée
seulement en dumpant les labels réellement rendus dans un fichier
plutôt que de deviner, était un problème d'assertion de test (MUI
ajoute un astérisque au texte du label pour un champ `required` :
`getByLabelText('Motif')` en correspondance exacte ne matche jamais
`"Motif *"`, `getByLabelText(/Motif/)` si).

**Vérification réelle complète.** 48 tests frontend verts (+3). Backend
Docker reconstruit, vérifié de bout en bout au navigateur en **mode
simulation uniquement** : bouton désactivé tant que le hostname ne
correspond pas exactement, activé une fois exact, soumission réelle →
`204`, dialogue fermé, log `[simulation] Would restart... (no real
effect)`, audit nominatif confirmé en base (acteur `admin`, cible,
motif, issue `SUCCESS`). **Mode live jamais déclenché pour de vrai.**

**Reste ouvert.** Active-response (exécution de commande, conception à
part) reportée. Shuffle (2ᵉ connecteur de la phase 5 : déclenchement de
workflow + callback, 3ᵉ filtre de clé d'API) reste à construire.
