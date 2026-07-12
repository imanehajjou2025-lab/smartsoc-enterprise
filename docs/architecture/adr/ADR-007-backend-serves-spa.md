# ADR-007 — Spring Boot sert la SPA React (un seul service, sans Nginx)

- **Statut** : Accepté
- **Date** : 2026-07-12
- **Décideurs** : Équipe SmartSOC

## Contexte

L'architecture SOC (voir `docs/architecture/soc/SOC-ARCHITECTURE.md`) impose
que **Cloudflare Tunnel** soit l'**unique point d'entrée HTTPS** vers
SmartSOC, en **connexion sortante** depuis le PC d'Imane (aucun port
entrant), et **sans Nginx**.

Or l'implémentation issue du jalon frontend (PR #24) exposait la plateforme
via un conteneur `frontend` faisant tourner **Nginx** pour servir le build
React et relayer `/api` vers le backend. Cloudflare aurait donc pointé sur
Nginx — en contradiction directe avec le schéma cible.

## Décision

**Spring Boot sert lui-même le build React**, embarqué dans ses ressources
statiques (`classpath:/static/`). La plateforme n'expose plus qu'**un seul
service applicatif sur `:8080`** qui rend **à la fois l'UI et l'API**.
**Nginx est supprimé** (`frontend/Dockerfile` et `frontend/nginx.conf`
retirés, service `frontend` retiré du Compose).

- Build : image Docker multi-stage (Node → build React ; Maven → le `dist`
  React est copié dans `smartsoc-api/src/main/resources/static/` avant le
  `package`). Contexte de build = racine du dépôt.
- Service SPA : un `WebMvcConfigurer` sert les fichiers statiques réels et
  fait retomber toute route client (`/alerts`, `/admin/users`…) sur
  `index.html`, en excluant les préfixes techniques (`/api`, `/actuator`,
  `/swagger`, `/api-docs`, `/webjars`, `/ws`).
- Sécurité : l'UI statique est publique (coquille HTML) ; **toute l'API
  reste protégée** (JWT/RBAC) et l'actuator sensible aussi.

Cloudflare Tunnel pointe donc directement sur `http://localhost:8080` —
exactement le schéma cible.

## Justification

- **Cohérence avec l'architecture SOC** : « aucun Nginx », un seul service à
  exposer par le tunnel. Le déploiement final = configurer `cloudflared`, pas
  du code.
- **Simplicité opérationnelle** : un artefact (un jar / une image), un port,
  une politique de sécurité unique. Idéal pour un PC de démo et pour le
  crédit Azure.
- **Même origine** : l'UI et l'API partagent l'origine `:8080` → aucun CORS,
  cookies/headers simples, topologie identique en dev (proxy Vite) et en prod.

## Conséquences

- Le **développement frontend** garde le serveur **Vite** (`npm run dev`,
  port 5173) qui proxifie `/api` et `/ws` vers le backend — inchangé.
- La CI Docker ne construit plus qu'**une image** (Spring Boot + UI),
  scannée par Trivy.
- `FRONTEND_PORT` ne concerne plus que le serveur de dev Vite ; il n'y a plus
  de conteneur frontend.
- Le build de l'image est un peu plus long (étape Node ajoutée) mais reste
  mis en cache par couche.
