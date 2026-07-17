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

*Prochaines entrées : frontend Investigations, module Actifs, CTI,
MITRE, hunting, SOAR, rapports, puis assistant IA (backend + frontend).*
