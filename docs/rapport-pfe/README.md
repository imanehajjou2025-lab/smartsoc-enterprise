# Matière première du rapport de PFE

Ce répertoire alimente le rapport de fin d'études au fil du développement,
afin que le document final reflète **fidèlement** le travail réalisé.

## Principe

- [`journal-de-bord.md`](journal-de-bord.md) reçoit une entrée à chaque
  jalon significatif (généralement une PR mergée) : objectif, choix
  techniques et justifications, difficultés réellement rencontrées et
  solutions apportées, avec liens vers les PRs, commits et ADR.
- Les entrées sont rédigées **au moment des faits**, jamais reconstruites
  de mémoire : le rapport final ne contiendra rien qui ne soit traçable
  dans l'historique Git.
- Les décisions structurantes restent dans les [ADR](../architecture/adr/) ;
  le journal les référence sans les dupliquer.

## Sources de vérité pour le rapport final

| Contenu du rapport | Source |
| --- | --- |
| Décisions d'architecture | `docs/architecture/` + ADR-001 à 005 (et suivants) |
| Chronologie et difficultés | `journal-de-bord.md` |
| Détail des changements | Pull Requests et commits (Conventional Commits) |
| Qualité et sécurité | SonarCloud, CodeQL, Trivy, Gitleaks (onglet Security) |
| Tests | Rapports JaCoCo / Vitest, descriptions des PRs |
