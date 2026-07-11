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

---

*Prochaines entrées : A4 WebSocket temps réel, A5-A6 module frontend
Alertes, puis connecteurs SOC, moteur SOAR, contrats IA, déploiement
Azure.*
