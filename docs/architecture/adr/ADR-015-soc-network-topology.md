# ADR-015 — Topologie d'accès réseau de la plateforme au SOC

- **Statut** : Accepté
- **Date** : 2026-08-05
- **Décideurs** : Équipe SmartSOC
- **Contexte d'origine** : phase 0 de la
  [baseline d'intégration](../soc-integration-plan.md), risques **R1** (accès au
  tunnel depuis le conteneur) et **R2** (certificats auto-signés)

## Contexte

La baseline d'intégration identifiait R1 comme le seul risque capable
d'invalider toute la direction sortante : le backend s'exécute dans un réseau
Docker **bridge**, tandis que WireGuard est une interface de l'**hôte**. Rien ne
garantissait que le conteneur puisse joindre `10.100.0.0/24`.

La phase 0 avait pour objet de trancher par la mesure, avant tout
développement. Ce document consigne les résultats réels.

## Mesures effectuées

Tunnel WireGuard monté depuis le poste de développement (`10.100.0.10/24`) vers
le hub `vm-siem` (`10.100.0.1`, endpoint `68.221.141.141:51820`). Handshake
confirmé, ping bidirectionnel à ~55 ms.

### Joignabilité

| Cible | Adresse | Depuis l'hôte | Depuis un conteneur | Verdict |
| --- | --- | --- | --- | --- |
| Wazuh API | `10.100.0.1:55000` | HTTP 401 | **HTTP 401** | ✅ joignable |
| OpenSearch | `10.100.0.1:9200` | HTTP 401 | **HTTP 401** | ✅ joignable |
| MISP | `10.100.0.3:443` | ouvert | **HTTP 302** | ✅ joignable |
| Shuffle | `10.100.0.4:3443` | ouvert | **HTTP 200** | ✅ joignable |
| VirusTotal | Internet | HTTP 401, **TLS validé** | HTTP 401, **TLS validé** | ✅ joignable |

Les codes 401 et 302 signifient « service vivant, authentification requise » :
c'est la preuve que la requête a atteint l'application, pas seulement le port.

Ports ouverts relevés : MISP `443` et `80` ; Shuffle `3443` (HTTPS), `3001` et
`5001` (HTTP).

### Certificats TLS — le résultat déterminant

| Service | Sujet | Émetteur | SAN | Couvre son IP ? | Validité |
| --- | --- | --- | --- | --- | --- |
| **OpenSearch** | `CN=wazuh-indexer` | AC interne Wazuh | **`IP:10.100.0.1`** | ✅ **oui** | 08/2026 → 08/2036 |
| Wazuh API | `CN=wazuh.com` | lui-même | `DNS:localhost` | ❌ non | 07/2026 → 07/2027 |
| MISP | `CN=localhost` | lui-même | `DNS:localhost`, `IP:127.0.0.1`, `IP:::1` | ❌ non | 07/2026 → 07/2027 |
| Shuffle | `CN=Shuffle` | lui-même | **aucun SAN** | ❌ non | 05/2020 → 05/2030 |

Le certificat de Shuffle appelle une remarque particulière : `O=Shuffle`,
`emailAddress=frikky@shuffler.io`, émis en 2020 — c'est le **certificat par
défaut livré avec le produit**. Sa clé privée est donc publiquement
disponible. Le TLS du port 3443 apporte aujourd'hui du **chiffrement sans
aucune garantie d'identité**.

## Décision

### 1. Réseau : le bridge Docker suffit — aucune modification

**R1 est levé.** Un conteneur attaché au réseau `smartsoc-net` joint le réseau
WireGuard sans aucun aménagement : Docker Desktop route via WSL2 vers l'hôte,
qui possède la route du tunnel.

Les trois solutions de repli envisagées dans la baseline — route explicite avec
`ip_forward`, `extra_hosts` vers l'IP de l'hôte, et surtout `network_mode: host`
— sont **écartées**. L'isolation réseau du `docker-compose.yml` est conservée
telle quelle.

### 2. Les deux chemins réseau sont confirmés distincts

Le trafic vers le SOC emprunte WireGuard ; le trafic vers VirusTotal sort par
Internet et **valide déjà une chaîne TLS publique sans aménagement**. La
contrainte 3 de la baseline est vérifiée en pratique, y compris depuis le
conteneur.

### 3. TLS : un seul service est exploitable en l'état

**R2 est confirmé, et plus étendu que prévu.** Sur les quatre services, **un
seul** présente un certificat couvrant l'adresse par laquelle on l'atteint.

**OpenSearch — résoluble par truststore seul.** Le certificat porte
`IP:10.100.0.1` en SAN : se connecter par l'adresse IP validera correctement le
nom d'hôte. Il suffit d'ajouter l'autorité interne Wazuh à un truststore Java
dédié. **Aucune intervention côté SOC.**

**Wazuh API, MISP et Shuffle — action requise côté SOC.** Leurs SAN ne couvrent
que `localhost`/`127.0.0.1`, ou sont absents. Puisque les implémentations TLS
modernes ignorent le `CN` dès qu'un SAN existe — et rejettent un certificat sans
SAN — **aucun truststore ne suffira** : la vérification du nom d'hôte échouera
quoi qu'il arrive.

Décision retenue : **régénérer ces trois certificats en incluant l'adresse
WireGuard du service dans les SAN** (`IP:10.100.0.1`, `IP:10.100.0.3`,
`IP:10.100.0.4`), puis ajouter les autorités correspondantes au truststore.

Deux alternatives sont explicitement rejetées :

- *désactiver la vérification du nom d'hôte* — cela annulerait la garantie
  d'identité que le chiffrement est censé apporter, ce que la baseline (§7.2)
  interdit ;
- *faire pointer un alias `localhost` vers ces adresses* dans le conteneur —
  détourner `localhost` casserait toutes les résolutions locales légitimes.

**Cas particulier de Shuffle.** Son certificat est celui livré par défaut avec
le produit : sa clé privée est publiquement disponible, donc le TLS actuel
n'authentifie rien. Deux options honnêtes se présentaient :

| Option | Ce qu'elle apporte réellement |
| --- | --- |
| **Régénérer le certificat** *(retenue)* | Chiffrement **et** identité, cohérent avec §7.2 de la baseline |
| Utiliser le port HTTP 3001 sur WireGuard | Chiffrement assuré par WireGuard seul, sans fausse assurance |

La seconde n'est pas absurde — elle serait plus honnête que le certificat par
défaut, qui donne l'apparence de la sécurité sans la fournir. Elle est écartée
pour rester conforme à la doctrine « TLS applicatif par-dessus WireGuard », mais
elle reste préférable au statu quo si la régénération devait tarder.

### 4. Un truststore dédié, jamais de confiance globale

Les autorités du SOC sont ajoutées à un **truststore applicatif propre**, monté
dans l'image, et non au magasin système. La confiance reste limitée aux
autorités du SOC, pour les seuls connecteurs concernés.

### 5. Routage inter-spokes validé

MISP (`10.100.0.3`) et Shuffle (`10.100.0.4`) résident sur un compte Azure
distinct de celui du hub : les atteindre suppose un relayage par le hub. Les
mesures confirment que la chaîne complète fonctionne — `ip_forward` et les
règles `FORWARD` du hub, et surtout `AllowedIPs = 10.100.0.0/24` déclaré côté
spokes, sans quoi les réponses ne reviendraient jamais.

**Aucun des quatre outils SOC n'est donc inaccessible.** Le maillage
Hub-and-Spoke est opérationnel de bout en bout depuis le conteneur.

## Conséquences

- **La direction sortante de la baseline est validée** : aucune décision
  d'architecture n'est remise en cause, et les quatre outils SOC sont joignables
  depuis le conteneur. La phase 1 peut démarrer.
- **Une dépendance envers l'équipe SOC est créée** : la régénération des
  certificats de l'API Wazuh, de MISP et de Shuffle conditionne respectivement
  les phases 1.2, 2 et 5. À défaut, ces connecteurs ne fonctionneront pas en TLS
  vérifié.
- **Un truststore dédié devient un livrable** de la phase 1.2, enrichi ensuite
  à chaque nouveau connecteur.
- **Aucune capture d'échantillon d'API n'a pu être réalisée** : elle exige des
  identifiants, non fournis à ce stade. Les fixtures d'ACL prévues par la
  phase 0 restent à produire à l'ouverture de chaque phase de connecteur.
- **Échéances de certificats à suivre** : Wazuh API et MISP expirent en
  **juillet 2027**, Shuffle en mai 2030, l'Indexer en août 2036. Les deux
  premières sont proches et provoqueraient une panne de connecteur sans aucun
  changement de code.

## Point de sécurité relevé hors périmètre

Le peer Kali (`10.100.0.7`) était actif à chaud mais absent de
`/etc/wireguard/wg0.conf` : il aurait disparu au premier redémarrage du hub. Il
a été ajouté au fichier lors de cette phase, en même temps que le peer SmartSOC.
