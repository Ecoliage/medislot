# Exploiter et diagnostiquer le laboratoire

## Démarrer

Depuis la racine du dépôt :

```
docker compose up -d --build
docker compose ps
```

Attends que le service `db` soit `healthy` (le backend ne démarre qu'à ce moment-là). Teste ensuite `curl http://localhost:18080/actuator/health`, puis ouvre le front sur http://localhost:8088. Pour lire les journaux du back : `docker compose logs --tail 100 backend`.

Les ports exposés, tous sur 127.0.0.1 : front 8088, back 18080, PostgreSQL 55432 (et non 5432), Jaeger 16686. Le back et le front tournent dans des conteneurs distincts : dans le navigateur tu passes toujours par le 8088, qui relaie `/api` vers le back.

Pour tester la recherche par proximité avec PostGIS à la place de la boîte englobante :

```
docker compose -f compose.yaml -f compose.postgis.yaml up -d --build
```

L'image `postgis/postgis` est déclarée en `linux/amd64`, ça peut être lent sur un Mac à puce Apple.

Les comptes de démo (`patient@medislot.test` et `autre@medislot.test`, mot de passe `MediSlot-demo-2026!`) et les créneaux des dix prochains jours sont créés au premier démarrage, parce que `DEMO_SEED` vaut `true` dans `compose.yaml`. Si tu repars d'une base propre, supprime le volume : `docker compose down -v`.

## Incidents à reproduire

1. **401.** Regarde `/api/me` : tu as peut-être une page chargée mais plus de session (elle expire après 30 minutes d'inactivité). La page qui s'affiche ne dit rien sur la session.
2. **403 sur une écriture.** Rappelle `/api/csrf` après la connexion et vérifie que l'en-tête et le cookie partent bien avec la requête. Le front garde le jeton dans `localStorage` (clé `medislot_csrf`), pense à le vider si tu bricoles. Ne désactive pas le CSRF pour faire disparaître l'erreur.
3. **409.** Le créneau est pris ou passé. Recharge les créneaux et laisse l'utilisateur en choisir un autre. Ne relance pas la réservation automatiquement.
4. **SSE silencieux.** Regarde le `Content-Type` (`text/event-stream`), le buffering du proxy (`proxy_buffering off` dans `frontend/nginx.conf`), les timeouts (le back coupe un flux après 120 s, Nginx après 150 s sans donnée, et un `heartbeat` part toutes les 20 s) et les listeners côté navigateur. Vérifie que l'événement `ready` déclenche bien un rechargement des données. Avec `curl -N http://localhost:8088/api/events`, tu dois voir `ready` tout de suite.
5. **Base indisponible.** Regarde si le service `db` tourne, et que le back vise `db:5432`. Depuis un conteneur, `localhost` désigne le conteneur lui-même et pas ta machine. Depuis ta machine, c'est le port 55432.
6. **Lenteur.** Mesure d'abord : latence de l'endpoint, nombre de requêtes SQL, appels faits par le navigateur. Les traces sont dans Jaeger (http://localhost:16686, service `medislot-api`), le script `tests/verifier_traces.sh` vérifie que la chaîne navigateur, Nginx, collecteur, Jaeger est en place. Pour une réservation, pense à la passerelle SMS : elle attend 1,2 s avant de répondre (`LATENCE_SECONDES` dans `sms-passerelle/serveur.py`).

## Sauvegarde et restauration d'essai

Utilise `pg_dump` et `pg_restore` sur une base à part, jamais sur la base qui tourne. Par exemple :

```
docker compose exec db pg_dump -U medislot -Fc medislot > medislot.dump
```

Restaure dans une base vide créée pour l'occasion et compte les lignes de `appointment` des deux côtés. Tant que tu n'as pas fait cette restauration, ta sauvegarde est un fichier dont tu ignores s'il est bon. Note la version du schéma Flyway, la date, l'emplacement du fichier et le résultat de la restauration.

## Mise en service et retour arrière

Le workflow `release.yml` (déclenché à la main) rejoue la CI puis construit les images `medislot-backend` et `medislot-frontend`, étiquetées par le hash du commit, et les archive. Aucun déploiement distant n'est configuré dans ce dépôt. Avant une vraie mise en service, relis les migrations et leur compatibilité avec la version précédente, les secrets, les checks de santé et le parcours de réservation de bout en bout. Si une migration détruit des données, revenir à l'image précédente ne les rendra pas : il faut prévoir autre chose avant de l'appliquer.
