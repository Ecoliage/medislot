# MediSlot

MediSlot permet de rechercher un praticien (par nom, spécialité, ville ou proximité géographique) et de réserver, déplacer ou annuler un rendez-vous. Le front est une application Vue 3 servie par Nginx, qui relaie `/api` vers un monolithe Spring Boot 4 (Java 25) adossé à PostgreSQL. La confirmation de réservation envoie un SMS vers une passerelle de démonstration, et les traces partent vers un collecteur OpenTelemetry puis Jaeger. Aucun prestataire réel n'est appelé et tous les praticiens sont fictifs.

Auteurs : Alban, Ibrahim, Aymeric, Alexis.

## Prérequis

Pour lancer l'application, il suffit de :

- Docker avec le plugin Compose v2 (`docker compose`). Les versions testées sont Docker 29 et Compose 5 ; la syntaxe `!override` utilisée plus bas demande un Compose récent.
- un accès réseau au premier build (images de base, dépendances Maven et npm, agent OpenTelemetry téléchargé depuis GitHub).

Le build se fait entièrement dans les conteneurs : il n'y a ni JDK ni Node à installer pour la démarrer.

Pour développer ou tester hors conteneurs, il faut en plus :

- JDK 25 ou plus récent. Le projet compile avec `release 25` : avec un JDK 17 ou 21, `./mvnw verify` s'arrête sur `release version 25 not supported`. La suite a été exécutée avec succès sous OpenJDK 27 (le JDK 25 est la version utilisée dans l'image Docker et en CI). Maven n'est pas à installer, le wrapper `backend/mvnw` s'en charge.
- Node 24 (version de l'image Docker et de la CI) avec npm. Avec Node 22.22.0, `npm ci` affiche des avertissements `EBADENGINE` (jsdom demande 22.22.2 ou plus) mais les tests passent.
- Docker démarré, pour les tests d'intégration du back (Testcontainers).

## Démarrage rapide

Depuis la racine du dépôt :

```
docker compose up -d --build
docker compose ps
```

Le premier build prend environ une minute (téléchargement des dépendances compris), le démarrage des conteneurs quelques secondes. Le service `db` doit être `healthy` avant que le back démarre ; compte une dizaine de secondes ensuite pour que le back réponde. Vérifie :

```
curl http://localhost:18080/actuator/health
```

La réponse attendue est `{"groups":["liveness","readiness"],"status":"UP"}`. Ouvre ensuite http://localhost:8088.

Au premier démarrage sur une base vide, Flyway applique `V1`, `V2` et `V4` (la `V3` n'existe que pour PostGIS), puis les données de démonstration sont créées : 11 praticiens, leurs créneaux des dix prochains jours et deux comptes.

Pour lire les journaux du back :

```
docker compose logs --tail 100 backend
```

Pour arrêter sans perdre la base : `docker compose down`.

## URLs et ports

Tous les ports sont publiés sur 127.0.0.1 uniquement.

| Service | URL sur la machine hôte | Rôle |
| --- | --- | --- |
| Front (Nginx) | http://localhost:8088 | Application ; relaie `/api`, `/oauth2` et `/login/oauth2` vers le back et `/otlp/` vers le collecteur |
| Back (Spring Boot) | http://localhost:18080 | API directe, `GET /actuator/health` |
| PostgreSQL | `localhost:55432` | Base `medislot`, utilisateur `medislot` (et non 5432) |
| Jaeger | http://localhost:16686 | Traces, service `medislot-api` |

Le collecteur OpenTelemetry et la passerelle SMS n'exposent aucun port sur l'hôte. Dans le navigateur, passe toujours par le port 8088.

## Comptes de démonstration

Deux comptes patient sont créés au premier démarrage :

| E-mail | Mot de passe |
| --- | --- |
| `patient@medislot.test` | `MediSlot-demo-2026!` |
| `autre@medislot.test` | `MediSlot-demo-2026!` |

Ils sont affichés dans la fenêtre de connexion parce que l'image du front est construite avec `VITE_DEMO=true` (argument de build dans `compose.yaml`). Ils sont créés parce que `compose.yaml` fixe `DEMO_SEED=true` pour le back.

Pour travailler sans données de démonstration :

1. Dans `compose.yaml`, passe `DEMO_SEED` à `"false"` (le back, lancé seul, vaut `false` par défaut) et `VITE_DEMO` à `"false"` (valeur par défaut du `Dockerfile` du front).
2. Repars d'une base vide si les comptes existent déjà : `docker compose down -v`, puis `docker compose up -d --build`.

Sans seed, la base ne contient aucun praticien ni compte : il faut s'inscrire depuis l'interface.

## Variables d'environnement

Compose lit un fichier `.env` placé à la racine (ignoré par Git). `.env.example` en donne un modèle :

```
cp .env.example .env
```

Attention : `.env.example` contient les valeurs `CLIENT_KEY` et `SECRET_KEY`. Elles ne sont pas vides, donc le back active la connexion Google avec un identifiant client invalide et le bouton apparaît sans pouvoir fonctionner. Remplace-les par de vraies valeurs (voir plus bas) ou supprime ces deux lignes.

Variables lues par `compose.yaml` :

| Variable | Rôle | Valeur par défaut |
| --- | --- | --- |
| `DB_PASSWORD` | Mot de passe de PostgreSQL et du back | `medislot-local-only` |
| `GOOGLE_CLIENT_ID` | Identifiant client OAuth Google | vide (connexion Google désactivée) |
| `GOOGLE_CLIENT_SECRET` | Secret client OAuth Google | vide |
| `COOKIE_SECURE` | Attribut `Secure` du cookie de session | `false` dans Compose |

Variables fixées par `compose.yaml` pour le back (non modifiables par `.env`, à éditer dans le fichier) :

| Variable | Valeur dans Compose | Rôle |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://db:5432/medislot` | URL JDBC |
| `DEMO_SEED` | `true` | Crée les praticiens, créneaux et comptes de démo au démarrage |
| `SMS_URL` | `http://sms-passerelle:8089/sms` | Passerelle SMS |
| `JAVA_TOOL_OPTIONS`, `OTEL_*` | voir `compose.yaml` | Agent OpenTelemetry, export gRPC vers `collector:4317` |

Variables lues par le back lui-même (`backend/src/main/resources/application.yml`), utiles surtout hors Compose :

| Variable | Rôle | Valeur par défaut |
| --- | --- | --- |
| `PORT` | Port d'écoute | `8080` |
| `DATABASE_URL` | URL JDBC | `jdbc:postgresql://localhost:5432/medislot` |
| `DB_USER` | Utilisateur PostgreSQL | `medislot` |
| `DB_PASSWORD` | Mot de passe PostgreSQL | `medislot-local-only` |
| `DEMO_SEED` | Données de démonstration | `false` |
| `COOKIE_SECURE` | Cookie de session `Secure` | `true` (à laisser tel quel derrière HTTPS) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Connexion Google | vides |
| `SMS_URL` | Passerelle SMS | `http://localhost:8089/sms` |
| `ADMIN_PASSWORD` | Mot de passe exigé par les routes `/api/debug/**` (réservées au rôle `ADMIN`) | vide (routes inutilisables) |

Variables lues par le build et par les outils :

| Variable | Rôle | Valeur par défaut |
| --- | --- | --- |
| `VITE_DEMO` | À la construction du front : affiche les comptes de démo dans la connexion | `false` (`true` dans Compose) |
| `API_TARGET` | Cible du proxy de `npm run dev` | `http://127.0.0.1:18080` |
| `APP_URL` | URL du front visée par Playwright et par `tests/*.sh` | `http://localhost:8088` |
| `JAEGER_URL` | URL de Jaeger visée par `e2e/trace.spec.ts` et `tests/verifier_traces.sh` | `http://localhost:16686` |

Le cookie de session n'est envoyé que sur HTTPS quand `COOKIE_SECURE=true`. Avec Compose en HTTP sur `localhost`, laisse `false`. Derrière un domaine public en HTTPS, passe-le à `true`.

## Connexion avec Google

La connexion Google est facultative. Le back la configure seulement si `GOOGLE_CLIENT_ID` n'est pas vide (`SecurityConfig`). Le front interroge `GET /api/login/google` et n'affiche le bouton « Se connecter avec Google » que si la réponse est `{"enabled":true}`. Sans identifiants, l'application démarre normalement, la réponse est `{"enabled":false}`, le bouton est absent et `/oauth2/authorization/google` répond 500.

### 1. Console Google Cloud

Ces étapes se font dans https://console.cloud.google.com et dépendent de l'interface de Google, qui change ; elles n'ont pas été rejouées pour ce document.

1. Crée un projet (ou choisis-en un existant).
2. Configure l'écran de consentement OAuth (menu « API et services », « Écran de consentement OAuth », ou « Google Auth Platform »). Choisis le type d'utilisateurs « Externe » pour un compte Google personnel, renseigne le nom de l'application et les adresses de contact. Les portées utilisées sont `openid`, `profile` et `email`, que Google accorde sans validation particulière.
3. Tant que l'application est en statut « Test », ajoute les comptes Google autorisés dans la liste des utilisateurs de test : les autres sont refusés par Google.
4. Dans « Identifiants », crée un « ID client OAuth » de type « Application Web ».
5. Dans « Origines JavaScript autorisées », ajoute `http://localhost:8088`.
6. Dans « URI de redirection autorisés », ajoute exactement :

```
http://localhost:8088/login/oauth2/code/google
```

7. Enregistre, puis note l'ID client (`xxxx.apps.googleusercontent.com`) et le secret client.

L'URI de redirection a la forme `{schéma}://{hôte}/login/oauth2/code/google`. Le chemin `/login/oauth2/code/google` est celui que Spring Security attend pour le client `google`. Le schéma et l'hôte sont ceux que le back déduit des en-têtes que Nginx lui transmet. Si tu changes le port du front ou le domaine, l'URI à déclarer chez Google change avec eux.

### 2. Reporter les identifiants dans `.env`

```
GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=le-secret-fourni-par-google
COOKIE_SECURE=false
```

Ne commite jamais ce fichier (il est dans `.gitignore`).

### 3. Redémarrer

```
docker compose up -d
```

Compose recrée le back avec les nouvelles variables ; il n'est pas nécessaire de reconstruire les images. Attends que `curl http://localhost:18080/actuator/health` réponde `UP`.

### 4. Vérifier

```
curl http://localhost:8088/api/login/google
curl -s -o /dev/null -w '%{http_code}\n%{redirect_url}\n' http://localhost:8088/oauth2/authorization/google
```

La première commande doit répondre `{"enabled":true}`. La seconde doit afficher `302` et une adresse `https://accounts.google.com/o/oauth2/v2/auth?...` contenant `client_id=` suivi de ton identifiant et un paramètre `redirect_uri=`. Compare ce `redirect_uri` à l'URI déclarée dans la console Google : elles doivent être identiques au caractère près, sinon Google répond `redirect_uri_mismatch`. Ouvre enfin http://localhost:8088, clique sur « Se connecter » : le bouton Google doit apparaître.

### Comportement du compte

- Une première connexion Google crée un compte avec le rôle `PATIENT` (nom et adresse repris du profil Google). Les connexions suivantes retrouvent le compte par l'identifiant Google (`google_sub`).
- Si l'adresse e-mail Google correspond déjà à un compte local (créé par inscription ou par le seed), la connexion Google est refusée : il n'y a pas de fusion automatique. L'utilisateur est renvoyé sur l'application avec un message lui demandant de se connecter avec son mot de passe.
- Toute autre erreur Google (consentement refusé, état invalide) renvoie vers `/?error=google` avec un message générique.
- Changer son adresse e-mail dans le profil demande le mot de passe actuel.

### En production

- Sers l'application en HTTPS et passe `COOKIE_SECURE=true`.
- Déclare chez Google l'origine et l'URI de redirection du domaine public, par exemple `https://medislot.example.org/login/oauth2/code/google`, et passe l'écran de consentement en production si les utilisateurs ne sont pas tous des comptes de test.
- Le proxy devant le back doit transmettre `X-Forwarded-Proto` et `X-Forwarded-Host` (c'est ce que fait `frontend/nginx.conf`), sinon le back construirait une URI de redirection avec son adresse interne.
- Désactive les données de démonstration (voir plus haut).

## Variante PostGIS

Pour calculer la recherche par proximité avec PostGIS plutôt qu'avec une boîte englobante :

```
docker compose -f compose.yaml -f compose.postgis.yaml up -d --build
```

Ce fichier remplace l'image de la base par `postgis/postgis:18-3.6` (déclarée en `linux/amd64`, donc émulée sur un Mac à puce Apple) et active le profil Spring `postgis`, qui ajoute la migration `V3__postgis.sql` (colonne `location` indexée en GiST). Sur une base vide, Flyway applique alors `V1`, `V2`, `V3` puis `V4`. Un volume créé avec l'image PostgreSQL standard ne doit pas être réutilisé : lance d'abord `docker compose down -v`.

## Développement local

Les commandes de cette section supposent la pile Compose démarrée pour la base et les services annexes.

**Front seul**, avec rechargement à chaud, contre le back de Compose :

```
cd frontend
npm ci
npm run dev
```

Le serveur Vite écoute sur http://127.0.0.1:5173 et relaie `/api`, `/oauth2` et `/login/oauth2` vers `http://127.0.0.1:18080`. Pour viser un autre back : `API_TARGET=http://127.0.0.1:28090 npm run dev`. Pour afficher les comptes de démo dans ce mode, ajoute `VITE_DEMO=true`.

**Back hors conteneur**, avec un JDK 25 ou plus récent. La base de Compose est sur le port 55432 (le défaut `localhost:5432` ne convient pas) :

```
cd backend
DATABASE_URL=jdbc:postgresql://localhost:55432/medislot COOKIE_SECURE=false ./mvnw spring-boot:run
```

Ajoute `PORT=28090` (par exemple) si le port 8080 est pris, et `DEMO_SEED=true` pour un jeu de données sur une base vide. `COOKIE_SECURE=false` est nécessaire en HTTP, puisque le défaut du back est `true`. Arrête avant cela le service `backend` de Compose si tu veux garder le port 18080 pour ton propre processus.

Pour appliquer une modification du code à la pile Compose : `docker compose up -d --build backend frontend`.

## Tests

**Back.** JDK 25 ou plus récent et Docker démarré (les tests d'intégration lancent PostgreSQL et PostGIS avec Testcontainers) :

```
cd backend
./mvnw verify
```

La suite compte 76 tests, dont un volontairement désactivé (`@Disabled` dans `BookingIntegrationTest`), et dure une vingtaine de secondes une fois les images de test téléchargées. Le JAR est produit dans `backend/target/medislot.jar`.

**Front, tests unitaires et build :**

```
cd frontend
npm ci
npm test
npm run build
```

`npm test` lance Vitest (57 tests réussis, 1 ignoré, c'est le test de panne réseau de `api.test.ts` en `it.skip`). `npm run build` vérifie les types avec `vue-tsc` puis construit `frontend/dist`.

**Bout en bout (Playwright).** La pile Compose doit tourner ; les tests visent http://localhost:8088 et Jaeger sur http://localhost:16686 :

```
cd frontend
npm run test:e2e
```

Playwright a besoin d'un navigateur Chromium installé (`npx playwright install chromium` si ce n'est pas déjà fait). Les quatre scénarios couvrent la réservation, le contrat OpenAPI, l'inscription et la propagation des traces. Si tu as remappé les ports, passe les adresses : `APP_URL=http://localhost:28088 JAEGER_URL=http://localhost:26686 npm run test:e2e`.

**Scripts de `tests/`.** Ils visent la pile Compose démarrée et se lancent depuis la racine :

```
tests/verifier_traces.sh
tests/verifier_convocation.sh
```

`verifier_traces.sh` contrôle la chaîne navigateur, Nginx, collecteur, Jaeger (trois points, `Bilan : 3 OK, 0 ÉCHEC`). `verifier_convocation.sh` réserve un créneau avec les deux comptes de démo et vérifie la convocation PDF, y compris le refus au second patient (404) et l'accès sans session (401). Ils lisent `APP_URL` et `JAEGER_URL`, et appellent `docker compose` sans option : avec un autre nom de projet, exporte aussi `COMPOSE_PROJECT_NAME`.

La CI (`.github/workflows/ci.yml`) enchaîne ces mêmes étapes : `./mvnw verify` avec JDK 25, `npm ci`, `npm test` et `npm run build` avec Node 24, puis Compose et Playwright.

## Réinitialiser la base

```
docker compose down -v
docker compose up -d --build
```

`down -v` supprime le volume `pgdata` : tous les comptes, rendez-vous et créneaux sont perdus, et le seed est rejoué au démarrage suivant. Sans `-v`, la base survit à un `down`.

## Dépannage

**Un port est déjà utilisé** (`Bind for 127.0.0.1:8088 failed: port is already allocated`, ou le 55432, le 18080, le 16686). Soit tu arrêtes ce qui l'occupe, soit tu remappes les ports hôtes dans un fichier d'override à côté de `compose.yaml`, par exemple `compose.override.local.yaml` :

```
services:
  db:
    ports: !override ["127.0.0.1:25432:5432"]
  backend:
    ports: !override ["127.0.0.1:28080:8080"]
  frontend:
    ports: !override ["127.0.0.1:28088:80"]
  jaeger:
    ports: !override ["127.0.0.1:26686:16686"]
```

```
docker compose -p medislot-bis -f compose.yaml -f compose.override.local.yaml up -d --build
```

Le front est alors sur http://localhost:28088. Le nom de projet différent (`-p`) évite de partager conteneurs et volumes avec une autre pile MediSlot. Pense à adapter l'URI de redirection Google à ce port (voir plus haut).

**`release version 25 not supported`** pendant `./mvnw verify` : le JDK utilisé est trop ancien. Vérifie avec `java -version` puis pointe `JAVA_HOME` vers un JDK 25 ou plus récent, par exemple `JAVA_HOME=/chemin/du/jdk ./mvnw verify`.

**Le back ne démarre pas** : `docker compose logs --tail 100 backend`. Vérifie que `db` est `healthy` ; depuis un conteneur, la base est `db:5432`, depuis ta machine `localhost:55432`.

**Le bouton Google est absent** : `curl http://localhost:8088/api/login/google` doit répondre `{"enabled":true}`. Sinon `GOOGLE_CLIENT_ID` n'est pas arrivé jusqu'au back : vérifie le `.env`, puis `docker compose up -d` pour recréer le conteneur.

**`redirect_uri_mismatch` côté Google** : l'URI de la console diffère de celle envoyée par le back, voir « Vérifier » plus haut.

**`429` à la connexion, à l'inscription ou à « mot de passe oublié »** : ces routes sont limitées en débit, par Nginx (30 requêtes par minute et par adresse, avec une rafale de 20) et par le back (30 requêtes par minute et par adresse, et 10 échecs de connexion par e-mail sur 5 minutes, avec un en-tête `Retry-After`). Attends la fin du délai, ou redémarre le back pour vider les compteurs en mémoire.

**401 sur une page chargée** : la session expire après 30 minutes d'inactivité. **403 sur une écriture** : jeton CSRF expiré, rappelle `/api/csrf`. Le détail des incidents classiques est dans `docs/runbook.md`.

## Structure du dépôt

```
backend/            API Spring Boot (fr.medislot : api, application, domain, repository, security, realtime, geo, document)
  src/main/resources/db/migration   migrations Flyway V1, V2, V4
  src/main/resources/db/postgis     migration V3 (profil postgis)
frontend/           application Vue 3 (Vite), nginx.conf, tests unitaires (tests/), e2e Playwright (e2e/)
observabilite/      configuration du collecteur OpenTelemetry
sms-passerelle/     passerelle SMS de démonstration (Python)
tests/              scripts de vérification de la pile (traces, convocation PDF)
docs/               architecture, runbook, connexion Google, ADR, diagrammes, contrat OpenAPI, énoncé du TP
.github/workflows/  CI (ci.yml) et construction des images (release.yml)
compose.yaml        pile complète ; compose.postgis.yaml la variante PostGIS
```

Pour aller plus loin : `docs/architecture.md` (découpage et règles métier), `docs/runbook.md` (exploitation et diagnostic), `docs/openapi.yaml` (contrat de l'API), `docs/adr/` (décisions) et `docs/TP.md` (progression des séances).
