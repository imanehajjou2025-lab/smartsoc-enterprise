# ADR-009 — Modèle des indicateurs CTI et ingestion des flux

- **Statut** : Accepté
- **Date** : 2026-07-20
- **Décideurs** : Équipe SmartSOC

## Contexte

Le SOC produit des alertes ; le renseignement sur la menace (CTI) dit
lesquelles portent des observables déjà connus comme malveillants. La
plateforme a donc besoin d'un référentiel d'indicateurs de compromission
(IOC) et d'un moyen de l'alimenter.

L'outil de CTI de l'équipe est **MISP**, administré hors de ce dépôt
(ADR-005, ADR-006) et relié par le tunnel WireGuard/Cloudflare. La
plateforme doit rester démontrable seule, sans MISP, et le branchement
final doit être une pure opération de configuration.

Un constat a orienté toute la conception : **une alerte SmartSOC ne
contient aujourd'hui aucun observable.** `Alert` porte `hostname`,
`ruleId`, `mitreTechniques` et `rawPayload`, mais ni IP, ni hash, ni
domaine. Sans observable, un référentiel d'IOC n'a rien à corréler.

## Décision

### 1. Les flux POUSSENT ; la plateforme n'interroge jamais MISP

L'alimentation se fait par un webhook `POST /api/v1/ingest/iocs`,
authentifié par la même clé `X-API-Key` que l'ingestion d'alertes, et
non par un client MISP embarqué.

Raisons, par ordre d'importance :

- **Découplage réel.** SmartSOC ne détient aucun identifiant vers les
  outils SOC, n'ouvre aucune connexion sortante vers eux, et ne dépend
  d'aucune de leurs API ni de leur disponibilité. Un MISP éteint ne
  dégrade rien ; c'est la promesse de l'ADR-005 tenue littéralement.
- **Surface d'attaque et secrets.** Un client MISP imposerait de stocker
  une clé d'API MISP dans la plateforme et d'autoriser du trafic
  sortant. Le sens push supprime les deux.
- **Cohérence avec l'existant.** Les alertes entrent déjà ainsi. Un seul
  mécanisme d'authentification, un seul modèle mental pour l'équipe SOC,
  un seul filtre à maintenir (`/api/v1/ingest/**`).
- **Shuffle est déjà le pont.** L'équipe CTI/SOAR opère Shuffle, dont le
  métier est précisément de pousser vers des systèmes tiers. Aucun
  développement d'intégration spécifique n'est nécessaire.
- **Démontrable seule.** Un script d'injection suffit à peupler le
  référentiel pour une démonstration, sans SOC.

**Conséquence assumée** : la plateforme ne peut pas déclencher une
synchronisation ; c'est le flux qui décide de sa fréquence. Si un besoin
de rattrapage à la demande apparaissait, l'évolution serait un port
`ThreatIntelFeed` avec adaptateurs `simulation`/`live`, sur le patron
exact de l'intégration IA (ADR-008) — sans remettre en cause le sens
push par défaut.

### 2. L'identité d'un IOC est le couple (type, valeur normalisée)

L'identité est **finale** ; les métadonnées CTI (confiance, TLP, source,
tags, fenêtre de validité, dates d'observation) sont volatiles. Un
upsert de flux rafraîchit les secondes et ne touche jamais la première —
`refreshFrom()` vérifie l'identité et lève `INDICATOR_IDENTITY_MISMATCH`
plutôt que d'écraser silencieusement une clé de corrélation.

La corrélation compare toujours le **couple**, jamais la valeur seule :
`45.83.12.7` peut légitimement exister comme `IPV4` et comme `DOMAIN`
sans être le même renseignement. La contrainte `ux_indicators_identity`
grave cette règle en base.

La normalisation **dépend du type** (un hash se met en minuscules sans
réserve, un chemin d'URL non) et commence toujours par le **refangage** :
un IOC stocké sous sa forme défangée `1.2.3[.]4` ne correspondrait à
aucun observable réel et n'alerterait jamais — sans qu'aucune erreur ne
le signale.

### 3. L'expiration est déduite, la révocation est un fait

`EXPIRED` n'est **pas** une colonne : il se calcule à partir de
`validUntil` au moment de la lecture. Une colonne de statut exigerait un
batch de péremption ; le jour où ce batch prend du retard ou tombe, des
IOC périmés continuent d'enrichir les alertes en se déclarant actifs.
Déduire supprime le batch **et** cette classe de bug.

`REVOKED` est en revanche un fait stocké : c'est une décision d'analyste
(« cet indicateur est un faux positif »), pas une conséquence du temps.
Elle **survit aux ré-observations** du flux qui continue de le pousser.

Un IOC ne se supprime jamais — expiré ou révoqué, il reste consultable.

### 4. Les lots sont traités ÉLÉMENT PAR ÉLÉMENT

Un lot poussé par un flux est ingéré avec **tolérance par élément** :
les indicateurs valides entrent, les fautifs sont rejetés nommément
(index, valeur, code), et le traitement continue. La réponse est
toujours `200` avec un compte rendu `received/created/updated/rejected`.

**Pourquoi pas tout-ou-rien** : un flux CTI réel contient des entrées
malformées. Refuser un lot de 1000 indicateurs parce que trois sont
mauvais priverait le SOC des 997 autres — un renseignement perdu est un
angle mort de détection. Nommer les rejets permet au producteur de
corriger sa source sans bloquer la chaîne.

**Pourquoi cela impose une architecture particulière** — deux
contraintes techniques se cumulent :

1. en PostgreSQL, une violation de contrainte rend la transaction
   courante **irrécupérable** : si tous les éléments partageaient une
   transaction, le premier fautif condamnerait tous les suivants. Il
   faut donc **une transaction par élément** ;
2. or un appel interne à une méthode `@Transactional` du même bean
   **court-circuite le proxy Spring** et n'ouvrirait aucune transaction.

L'orchestration du lot vit donc dans un bean séparé
(`IndicatorFeedIngestionService`), délibérément **non transactionnel**,
qui appelle `IndicatorService.ingest()` — un autre bean, donc un vrai
passage par le proxy, donc une transaction réellement isolée par
élément. Ce n'est pas un détail d'implémentation : c'est ce qui rend la
tolérance par élément possible.

Le lot est plafonné à **1000 éléments** : au-delà, une requête ne peut
plus être ni validée ni journalisée proprement. Les flux volumineux
paginent.

### 5. Les observables d'alerte seront DÉCLARÉS, pas devinés

Pour que l'enrichissement ait lieu, le contrat d'ingestion des alertes
sera étendu d'un champ `observables` optionnel et typé, déclaré par le
producteur — qui, seul, connaît le format de son outil.

L'alternative — extraire les observables de `rawPayload` par expressions
régulières — est **rejetée** : une regex d'IP attrape aussi bien la
version d'un agent qu'un identifiant de règle, et produirait de faux
rattachements. C'est la doctrine déjà posée sur les actifs (ADR
implicite du jalon Actifs, limitation FQDN) : *un faux rattachement est
pire qu'une absence*. En SOC, une corrélation fausse coûte plus cher
qu'une corrélation manquante, parce qu'elle consomme du temps d'analyste
et érode la confiance dans l'outil.

L'extension est **additive et rétrocompatible** : les producteurs
actuels, qui n'envoient pas ce champ, ne subissent aucune régression.

> **Portée — à lire avant de relire le code.** Cette décision est prise
> ici, mais **volontairement PAS implémentée dans la PR CTI-1**, qui se
> limite au référentiel IOC et à son ingestion. Le champ `observables`,
> la migration correspondante et la mise à jour de
> `docs/integration/alert-ingestion.md` arrivent en **PR CTI-2**, avec
> l'enrichissement (section 6). Ce n'est donc pas un oubli si le contrat
> d'ingestion des alertes n'en parle pas encore : les deux PR ont des
> responsabilités strictement séparées — CTI-1 ne touche à aucun contrat
> existant, CTI-2 fait évoluer celui des alertes.

### 6. L'enrichissement sera calculé à la lecture

La correspondance observables ↔ indicateurs se fera par jointure au
moment de la consultation, sans table de correspondances persistée.

- Le **retro-hunt est gratuit** : un IOC qui arrive aujourd'hui remonte
  les alertes d'hier, sans job de rattrapage.
- Le filtre d'activité s'évalue **en SQL, au même instant que le
  comptage** — la liste et son total ne peuvent pas diverger.

Une table `alert_ioc_matches` deviendra nécessaire le jour où il faudra
*notifier* sur un match (déclencher un playbook SOAR à l'arrivée d'un
IOC). C'est une évolution, pas un manque.

## Conséquences

**Positives.** La plateforme reste autonome et démontrable sans MISP.
L'équipe CTI intègre par configuration, avec un contrat écrit
(`docs/integration/cti-ioc-ingestion.md`) et un seul mécanisme
d'authentification. Aucun secret d'outil SOC n'entre dans la plateforme.
Trois classes de bugs silencieux sont fermées par construction :
normalisation divergente, IOC défangé qui n'alerte jamais, IOC périmé
qui se croit actif.

**Négatives.** La plateforme subit la fréquence de rafraîchissement du
flux. Un IOC dont la valeur a été mal typée par le producteur est
rejeté plutôt que rattrapé — le prix de la validation stricte. La
recherche par tag reposant sur le containment JSONB a exigé une fonction
Hibernate rendue par motif pour que l'index GIN s'applique (mesures dans
le journal de bord).

**Limitations actées.** Pas de rapprochement flou : `evil.com` et
`www.evil.com` sont deux indicateurs distincts, comme `srv-web-01` et
son FQDN côté actifs. Un IOC de type `URL` n'est pas rapproché du
`DOMAIN` qu'il contient. Ces rapprochements, s'ils s'avèrent
nécessaires, seront explicites et non devinés.

## Alternatives écartées

| Alternative | Raison du rejet |
| --- | --- |
| Client MISP dans la plateforme (pull) | Couplage à un outil SOC, secret MISP à stocker, trafic sortant, plateforme non démontrable seule |
| Extraction des observables depuis `rawPayload` | Faux rattachements ; dépendant du format de chaque outil |
| Lot tout-ou-rien | Trois entrées malformées priveraient le SOC de tout un flux |
| Colonne `status` maintenue par batch | Un batch en retard laisse des IOC périmés se déclarer actifs |
| Table de correspondances alerte ↔ IOC | Machinerie inutile aujourd'hui, et perte du retro-hunt gratuit |
| Unicité sur la valeur seule | Confondrait des renseignements de types différents |

## Références

- ADR-005 — plateforme autonome et contrats d'intégration
- ADR-008 — architecture d'intégration des services IA (patron des ports
  et adaptateurs, si un mode pull devenait nécessaire)
- `docs/integration/cti-ioc-ingestion.md` — le contrat côté producteurs
- `docs/rapport-pfe/journal-de-bord.md` — mesures et incidents du jalon
