# Onboarding SonarCloud (5 minutes, à faire une fois)

Le workflow [`ci-sonarcloud.yml`](../../.github/workflows/ci-sonarcloud.yml)
est prêt mais **saute l'analyse tant que le secret `SONAR_TOKEN` n'existe pas**.
Pour l'activer :

## 1. Créer le compte et importer le projet

1. Aller sur <https://sonarcloud.io> → **Log in** → **With GitHub** ;
2. Autoriser SonarCloud sur le compte `imanehajjou2025-lab` ;
3. **+ → Analyze new project** → sélectionner `smartsoc-enterprise` ;
4. Choisir le plan **Free** (dépôts publics) ;
5. Quand SonarCloud demande la méthode d'analyse : choisir
   **With GitHub Actions** (ne pas activer l'analyse automatique — elle
   entrerait en conflit avec notre analyse CI pilotée par Maven).

## 2. Vérifier les identifiants du projet

Dans SonarCloud : **Information** (menu du projet). Les valeurs attendues
par le workflow sont :

- Organization : `imanehajjou2025-lab`
- Project key : `imanehajjou2025-lab_smartsoc-enterprise`

Si SonarCloud a généré d'autres valeurs, mettre à jour les deux `-Dsonar.*`
dans `ci-sonarcloud.yml`.

## 3. Créer le token et l'ajouter à GitHub

1. SonarCloud → **My Account → Security → Generate Token** (type : *Global
   Analysis Token* ou token projet) ;
2. GitHub → dépôt `smartsoc-enterprise` → **Settings → Secrets and
   variables → Actions → New repository secret** ;
3. Nom : `SONAR_TOKEN` — valeur : le token copié.

## 4. Vérifier

Relancer le workflow **SonarCloud** (onglet Actions → SonarCloud →
*Run workflow*) : l'analyse doit s'exécuter et le dashboard apparaître sur
sonarcloud.io (qualité, couverture JaCoCo, code smells, duplication,
hotspots de sécurité).

## Badge README (optionnel)

Une fois la première analyse passée, ajouter au README :

```markdown
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=imanehajjou2025-lab_smartsoc-enterprise&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=imanehajjou2025-lab_smartsoc-enterprise)
```
