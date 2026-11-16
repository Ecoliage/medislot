# Sessions de même origine

## Contexte

Le front et l'API sont servis depuis la même origine : Nginx répond sur le port 8088 et relaie `/api` vers Spring Boot. Il faut identifier le patient pour les rendez-vous, les notifications et le flux SSE privé, sans qu'il puisse se faire passer pour un autre.

## Décision

L'identité reste côté serveur, dans une session Spring Security (30 minutes d'inactivité, voir `application.yml`). Le navigateur ne détient qu'un cookie `HttpOnly`, `SameSite=Lax`, que `EventSource` envoie lui aussi (c'est un point qui compte pour `/api/notifications/events`). Les écritures exigent un jeton CSRF : le front l'obtient sur `/api/csrf` et le relit après connexion et déconnexion (`refreshCsrf` dans `frontend/src/api.ts`). Le patient est toujours déduit de la session, jamais du corps de la requête.

## Alternatives écartées

- **JWT dans le navigateur.** Où le ranger ? Dans `localStorage` il devient lisible par n'importe quel script de la page, dans un cookie on retrouve le problème CSRF sans les avantages de la session. Il faut en plus gérer l'expiration, le renouvellement et surtout la révocation, que la session règle en supprimant une entrée côté serveur. Ça se justifierait avec des clients mobiles ou plusieurs services à authentifier, ce qui n'est pas le cas ici.
- **Session partagée (Spring Session avec JDBC ou Redis).** Utile dès qu'il y a plusieurs instances du back. Il n'y en a qu'une, la session en mémoire du conteneur suffit, et redémarrer le back déconnecte les utilisateurs, ce qu'on accepte pour un laboratoire.
- **OAuth2/OIDC avec un fournisseur d'identité.** Ce serait le choix pour un produit réel, mais il faudrait un serveur d'identité à fournir aux étudiants, et la séance 7 porte justement sur le fonctionnement de la session et du CSRF.
- **Ouvrir CORS et séparer les origines.** Aucune configuration CORS n'existe dans le code, et c'est voulu : avec une seule origine il n'y en a pas besoin. CORS d'ailleurs ne protège pas d'une requête forgée et ne remplace aucune autorisation.

## Conséquences

Le front doit gérer l'expiration de session (le 401 renvoyé en JSON `Connexion requise`) et le jeton CSRF périmé (403 `Action refusée ou jeton CSRF expiré`). Passer à plusieurs instances obligerait à externaliser la session.

## Vérification

À la main : `curl -i http://localhost:8088/api/appointments` sans cookie doit donner 401 (`Connexion requise`). Un POST sur la même route sans jeton CSRF donne 403, parce que le contrôle CSRF passe avant l'autorisation ; connecté, le même POST sans l'en-tête donne aussi 403. `tests/verifier_convocation.sh` enchaîne connexion, jeton CSRF et appels avec les cookies des deux comptes de démo, y compris l'appel sans session. Côté navigateur, `frontend/e2e/booking.spec.ts` (`npm run test:e2e`) passe par la vraie connexion.
