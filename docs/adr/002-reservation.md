# Verrou et unicité pour réserver

## Contexte

Deux patients peuvent cliquer sur le même créneau à la même seconde. Un seul doit obtenir le rendez-vous, l'autre doit recevoir un 409 lisible et pas une erreur 500.

## Décision

Deux protections complémentaires.

1. `BookingService.book` verrouille la ligne du créneau (`SlotRepository.lockById`, `PESSIMISTIC_WRITE`, donc un `SELECT ... FOR UPDATE`), puis `check` refuse le créneau s'il est passé ou s'il a déjà un rendez-vous `CONFIRMED`. Le second arrivant attend la fin de la transaction du premier, voit le rendez-vous et répond 409.
2. L'index unique partiel `one_active_appointment_per_slot` (dans `V1__schema.sql`) interdit en base deux rendez-vous confirmés sur le même créneau, même si un futur code oublie le verrou. Il porte sur `status='CONFIRMED'` pour qu'une annulation libère le créneau.

Pour déplacer un rendez-vous, `reschedule` verrouille les deux créneaux dans l'ordre croissant de leurs identifiants, pour éviter l'interblocage entre deux déplacements croisés.

## Alternatives écartées

- **Verrou optimiste (`@Version`).** Il faudrait une colonne de version sur `slot`, attraper `OptimisticLockException` et décider de réessayer ou non. Sur un créneau très disputé on gaspille du travail, et l'exception doit être convertie en 409 à la main. Reste un choix possible : à toi de le tester en séance 6.
- **L'index unique seul.** Il suffirait à garantir l'invariant, mais la violation remonte en `DataIntegrityViolationException` : il faut la traduire en 409, et les effets de bord déjà faits (SMS, audit) ne sont pas annulés proprement.
- **Isolation `SERIALIZABLE`.** Elle protège tout, mais pour toute l'application et avec des échecs de sérialisation à rejouer partout. Disproportionné pour un seul invariant.
- **Verrou consultatif PostgreSQL (`pg_advisory_xact_lock`).** Ça marcherait, mais ajouterait un mécanisme que les étudiants ne verraient dans aucune requête JPA.

## Conséquences

Un créneau disputé fait attendre les autres. Et ce temps d'attente est plus long qu'il ne faudrait : `book` appelle la passerelle SMS (`SmsGateway`) avant la fin de la transaction, donc le verrou est gardé pendant les 1,2 s que met la passerelle de démonstration. À discuter en séance 6 : sortir l'envoi de la transaction.

## Vérification

`mvn -f backend/pom.xml -Dtest=BookingIntegrationTest test` (Docker doit tourner : Testcontainers lance un `postgres:18`). Le test `concurrentBookingsProduceOneWinner` lance deux réservations simultanées sur le même créneau et attend exactement les codes 201 et 409. Pour voir le rôle du verrou, retire temporairement l'appel à `lock` dans `book` : l'index empêchera toujours le doublon, mais le perdant ne recevra plus un 409 propre. Un autre test de cette classe, `anotherPatientCannotCancelAndCancellationKeepsHistory`, est marqué `@Disabled` dans le dépôt.
