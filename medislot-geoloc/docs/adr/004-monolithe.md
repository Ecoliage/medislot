# Un déploiement pour le métier

## Décision

MediSlot est un seul service Spring Boot (`backend/`), un front statique derrière Nginx et une base PostgreSQL. La réservation, l'annulation, le déplacement et l'historique s'exécutent dans une même transaction locale, sans protocole entre services. Autour, quelques services annexes de laboratoire : le collecteur OpenTelemetry avec Jaeger, et la passerelle SMS de démonstration.

## Alternatives écartées

Découper en services (annuaire, réservation, notifications, identité) n'apporte rien à ce volume : ce sont une dizaine de tables et un petit nombre de classes par package. En revanche cela ferait apparaître des problèmes qui n'ont rien à faire dans ce TP : un appel réseau entre la réservation et l'annuaire, des contrats versionnés à maintenir des deux côtés, une cohérence qui devient éventuelle (un rendez-vous créé, une notification perdue), plusieurs déploiements et plusieurs jeux de logs à suivre.

Un monolithe n'oblige pas pour autant à tout mélanger. Les packages `api`, `application`, `domain`, `repository`, `security`, `realtime`, `geo` et `document` posent déjà des frontières, et un éventuel découpage en modules `directory`, `booking`, `identity` et `notification` est l'objet de la séance 9.

## Conséquences

On profite des transactions de la base (le verrou de l'ADR 002 serait bien plus compliqué à réaliser entre deux services). On accepte qu'une panne du back arrête tout, et qu'on ne puisse pas monter en charge une partie seulement. Le jour où une partie justifie son propre déploiement, ce sera sans doute `realtime` ou l'envoi des SMS, qui sont déjà reliés au reste par un événement (`BookingChanged`) ou une classe à part (`SmsGateway`).

## Vérification

`docker compose ps` : un seul conteneur `backend`, un seul `frontend`, une seule `db`. `mvn -f backend/pom.xml verify` compile et teste tout le back d'un coup. Pour voir que c'est bien une transaction unique, ouvre une réservation dans Jaeger (http://localhost:16686, service `medislot-api`) : le span `medislot.reservation` de `BookingService.book` englobe la lecture du créneau, l'insertion et l'appel à la passerelle SMS.
