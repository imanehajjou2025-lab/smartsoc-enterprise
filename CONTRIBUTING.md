# Guide de contribution

Merci de contribuer à SmartSOC Enterprise ! Ce document définit les règles de
travail de l'équipe. Elles sont **obligatoires** pour garder un historique Git
propre et une CI fiable.

## 🔀 Git Flow

| Branche     | Rôle                                      | Créée depuis | Fusionnée vers        |
| ----------- | ----------------------------------------- | ------------ | --------------------- |
| `main`      | Versions stables (production)             | —            | —                     |
| `develop`   | Intégration continue (branche par défaut) | `main`       | `release/*`           |
| `feature/*` | Nouvelles fonctionnalités                 | `develop`    | `develop`             |
| `release/*` | Préparation d'une version                 | `develop`    | `main` + `develop`    |
| `hotfix/*`  | Correctifs urgents en production          | `main`       | `main` + `develop`    |

### Nommage des branches

```
feature/<scope>-<description-courte>     ex: feature/backend-alert-crud
release/<version>                        ex: release/1.2.0
hotfix/<description-courte>              ex: hotfix/jwt-expiration
```

### Règles

1. **Jamais de commit direct sur `main`** — uniquement des merges de `release/*` ou `hotfix/*`.
2. Le travail quotidien part de `develop` et y revient via **Pull Request**.
3. Une PR = une fonctionnalité cohérente, revue par au moins un autre membre.
4. La CI doit être verte avant tout merge.

## ✍️ Conventional Commits

Format : `<type>(<scope>): <description>` (description à l'impératif, en anglais, sans majuscule initiale ni point final).

| Type       | Usage                                             |
| ---------- | ------------------------------------------------- |
| `feat`     | Nouvelle fonctionnalité                           |
| `fix`      | Correction de bug                                 |
| `docs`     | Documentation uniquement                          |
| `style`    | Formatage, sans changement de logique             |
| `refactor` | Refactoring sans changement de comportement       |
| `perf`     | Amélioration de performance                       |
| `test`     | Ajout ou correction de tests                      |
| `build`    | Build, dépendances, Docker                        |
| `ci`       | Pipelines CI/CD                                   |
| `chore`    | Maintenance diverse                               |

Scopes usuels : `backend`, `frontend`, `ai`, `soar`, `db`, `docker`, `docs`, `ci`.

Exemples :

```
feat(backend): add alert ingestion endpoint
fix(frontend): prevent duplicate websocket subscriptions
ci: add trivy image scan to docker workflow
```

Un changement cassant s'indique par `!` (ex : `feat(backend)!: rename alert status enum`)
et un paragraphe `BREAKING CHANGE:` dans le corps du commit.

## 🏷️ Versioning

[Semantic Versioning](https://semver.org/lang/fr/) : `MAJOR.MINOR.PATCH`.
Les versions sont taguées sur `main` (`v1.2.0`) lors des releases.

## ✅ Checklist avant Pull Request

- [ ] La branche part de `develop` et est à jour (`git pull --rebase origin develop`)
- [ ] Les commits respectent Conventional Commits
- [ ] Les tests passent localement
- [ ] Pas de secret, mot de passe ou clé API dans le code (`.env` jamais commité)
- [ ] La documentation est mise à jour si nécessaire
- [ ] Le CHANGELOG est mis à jour pour les changements notables
