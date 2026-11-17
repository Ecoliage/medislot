# MediSlot multi-cabinet : base partagée, `clinic_id` et RLS

MediSlot sert deux cabinets fictifs dans une seule base PostgreSQL : `cabinet-a` (Cabinet des Tilleuls, Paris) et `cabinet-b` (Maison de santé du Canal, Lyon). Ce document justifie le modèle, décrit le trajet du cabinet du navigateur à la base, liste les preuves et dit ce que le code ne traite pas.

Le kit de départ du TP n'a pas été utilisé : le multi-cabinet a été ajouté au projet MediSlot existant. Les noms diffèrent donc un peu de l'énoncé.

| Énoncé du TP | Dans ce dépôt |
| --- | --- |
| `V2__clinics.sql`, `V3__row_level_security.sql` | `V5__clinics.sql`, `V6__row_level_security.sql` (`V2` à `V4` étaient déjà prises) |
| `stores/agenda.ts` | caches des composables `useAppointments`, `useNotifications`, `usePractitionerSearch` et de `rdvService`, vidés par `tenantCache.ts` avant chaque bascule |
| `PostgresTestBase`, `TenantFilterTest`, `TenantIsolationTest`, `RlsDirectSqlTest`, `StatsIsolationTest` | mêmes noms, dans `backend/src/test/java/fr/medislot` |

## 1. Le modèle : une base partagée, une colonne `clinic_id`

### Pourquoi ce modèle

| Critère | Base partagée + `clinic_id` (retenu) | Un schéma par cabinet | Une base par cabinet |
| --- | --- | --- | --- |
| Isolation | Logique : une requête sans filtre fuit. Compensée par la RLS en base | Forte, par `search_path` | La plus forte |
| Coût par cabinet | Nul : une ligne dans `clinic` | Un jeu de tables de plus | Une instance ou une base de plus |
| Migrations | Une seule exécution Flyway | Une par schéma, à garder alignées | Une par base |
| Ouvrir un cabinet | `INSERT` | Créer le schéma et rejouer les migrations | Provisionner une base |
| Comptes partagés entre cabinets | Naturel : `app_user` et `membership` sont communs | Demande un schéma commun en plus | Demande un service d'identité à part |
| Sauvegarde ou restauration d'un seul cabinet | Difficile : il faut extraire ses lignes | Possible par schéma | Simple |
| Voisin bruyant | Tous les cabinets partagent pool, index et cache | Idem | Isolé |

MediSlot a peu de cabinets, de petite taille, et un même patient peut consulter dans les deux : le compte partagé pèse plus que la restauration par cabinet. Le risque du modèle (un `WHERE` oublié) est couvert deux fois : Hibernate ajoute le filtre tout seul, et PostgreSQL refuse les lignes des autres cabinets même si le SQL les demande.

### Tables propres et tables partagées

| Table | Nature | Pourquoi |
| --- | --- | --- |
| `professional`, `slot`, `appointment`, `audit_event`, `notification` | Propres : `clinic_id NOT NULL`, RLS activée et forcée | L'agenda d'un cabinet ne regarde que lui |
| `clinic` | Partagée, en lecture seule pour l'application | C'est la liste des cabinets |
| `app_user` | Partagée | Un compte, une adresse e-mail, quel que soit le nombre de cabinets |
| `membership` | Partagée | Dit quel compte appartient à quel cabinet : `patient@medislot.test` est dans A et B, `autre@medislot.test` dans A seulement |

### Contraintes de `V5` qui contiennent `clinic_id`

- Unicités : `membership (user_id, clinic_id)` empêche d'appartenir deux fois au même cabinet ; `professional (clinic_id, id)`, `slot (clinic_id, id)` et `appointment (clinic_id, id)` servent de cible aux clés composites.
- Clés étrangères composites : `slot (clinic_id, professional_id) → professional (clinic_id, id)`, `appointment (clinic_id, slot_id) → slot (clinic_id, id)`, `audit_event (clinic_id, appointment_id) → appointment (clinic_id, id)`. Un rendez-vous du cabinet B ne peut pas pointer un créneau du cabinet A, même par une écriture SQL directe : la base refuse la ligne.
- `clinic.slug` est unique et contraint par `^[a-z0-9]+(-[a-z0-9]+)*$`, `clinic.color` par `^#[0-9a-f]{6}$`.
- Les lignes qui existaient avant la migration sont rattachées à `cabinet-a`, puis `clinic_id` passe en `NOT NULL`.

### Le rôle `medislot_app`

`V5` crée `medislot_app` (`NOSUPERUSER`, `NOBYPASSRLS`) et ne lui donne que `SELECT, INSERT, UPDATE, DELETE` sur les tables de données et `SELECT` sur `clinic`. L'application se connecte avec ce rôle ; Flyway garde `medislot`, propriétaire du schéma. C'est ce qui rend la RLS effective : PostgreSQL ne l'applique ni aux super-utilisateurs ni, sans `FORCE`, au propriétaire des tables.

## 2. Le cabinet courant, du navigateur à la base

1. **Adresse.** `frontend/src/tenant.ts` lit le cabinet dans le chemin (`/c/cabinet-b/`), sinon dans le sous-domaine (`cabinet-b.medislot.test`), sinon prend `cabinet-a`. Chaque slug est validé par `SLUG`. `clinicFromHost` teste la fin du nom d'hôte : `cabinet-a.medislot.test.pirate.example` rend `null`.
2. **En-tête.** `api.ts` ajoute `X-Clinic` à chaque appel. Là où un en-tête ne peut pas partir (flux `EventSource`, liens de téléchargement), `apiUrl` ajoute le paramètre `clinic`.
3. **Filtre.** `TenantFilter` lit l'en-tête ou le paramètre, répond 400 (`Cabinet manquant ou invalide.`) si le slug est absent, mal formé ou contradictoire, 404 (`Cabinet introuvable.`) si le cabinet n'existe pas ou si la personne connectée n'en est pas membre, pose le cabinet dans `TenantContext`, et le retire dans un `finally`. Un visiteur anonyme passe : l'annuaire est public. Le refus d'un non-membre a le même corps que celui d'un cabinet inconnu, pour ne pas révéler qu'un cabinet existe.
4. **Hibernate.** Les cinq entités propres portent `@TenantId`. `ClinicTenantResolver` donne le cabinet du `TenantContext` : Hibernate ajoute `clinic_id = ?` aux lectures et renseigne la colonne aux écritures.
5. **PostgreSQL.** `TenantTransactionManager` exécute `select set_config('app.current_clinic', ?, true)` au début de chaque transaction. Le troisième argument rend la valeur locale à la transaction : elle disparaît au commit, donc une connexion rendue au pool n'emporte pas le cabinet du précédent.

Réponses aux questions de lecture de l'énoncé :

- **Où est le filtre, et pourquoi pas un `@Component` ?** `SecurityConfig` l'ajoute à la chaîne de sécurité, après `AuthorizationFilter` : l'utilisateur est alors connu, et un 401 passe avant un 404. Déclaré en `@Component`, Spring Boot l'enregistrerait aussi dans la chaîne du conteneur, avant la sécurité : il tournerait deux fois, la première sans utilisateur.
- **Routes sans cabinet.** `/api/csrf`, `/api/login`, `/api/login/google`, `/api/logout`, `/api/me`, `/api/me/clinics`, `/api/clinics/**`, `/api/platform/**`, `/api/compte/**` et `/api/patients/**` ne touchent que des tables partagées. Sur ces routes le `TenantContext` reste vide : une lecture d'une table propre y rendrait zéro ligne.
- **Changer de cabinet.** `ClinicSwitcher` (sous l'en-tête) liste les cabinets de la personne connectée, lus par `GET /api/me/clinics`. `switchTo` refuse un slug invalide ou qui n'est pas dans cette liste, vide les caches (`clearTenantCaches`) **avant** de changer de cabinet, pose le nouveau slug pour `X-Clinic` puis fait `history.pushState` vers `/c/<cabinet>/`, sans rechargement. Chaque cache a un compteur de génération incrémenté au vidage : une réponse demandée avant la bascule et arrivée après est jetée. À la connexion, `afterLogin` charge les appartenances, garde le cabinet courant s'il en fait partie, sinon bascule sur le premier : une personne qui ouvre l'adresse d'un cabinet qui n'est pas le sien est ramenée chez elle. Le bouton « Ouvrir » de la liste Admin passe toujours par l'adresse.

## 3. La RLS

`V6` définit `current_clinic()`, puis pour chacune des cinq tables propres : `ENABLE ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY`, et une politique `clinic_isolation` avec `USING (clinic_id = current_clinic())` et `WITH CHECK (clinic_id = current_clinic())`. `USING` filtre ce qu'on lit, modifie ou supprime ; `WITH CHECK` refuse d'écrire une ligne dans un autre cabinet.

- **Pourquoi `nullif` ?** `current_clinic()` vaut `nullif(current_setting('app.current_clinic', true), '')::bigint`. Après un `set_config(..., true)`, la variable d'une connexion du pool ne redevient pas absente mais vaut `''`, et `''::bigint` lève `invalid input syntax for type bigint`. `nullif` la ramène à `NULL` : `clinic_id = NULL` n'est jamais vrai, donc sans cabinet on ne voit rien, sans erreur.
- **Sans transaction, pas de cabinet.** C'est le piège rencontré pendant le TP. Les requêtes déclarées d'un dépôt Spring Data (`search`, `available`, `findByPatientId...`) ne sont pas transactionnelles par défaut, contrairement à `findById` ou `save`. Hors transaction, `set_config` n'est jamais exécuté et la RLS rend une liste vide alors que les données existent. Les cinq dépôts propres sont donc annotés `@Transactional(readOnly = true)`, comme `NearbyService.search` et la recherche avancée qui passent par du SQL direct.
- **`FORCE`** soumet aussi le propriétaire des tables à la politique. Il ne peut rien contre un super-utilisateur.

Vérifié en retirant `V6` : les quatre tests de `RlsDirectSqlTest` deviennent rouges, ceux de `TenantIsolationTest` restent verts grâce à `@TenantId`. Les deux barrières sont indépendantes.

## 4. Preuves

Résultats du 17 novembre 2026 : back `./mvnw test`, 120 tests, 0 échec, 1 ignoré (un test de réservation déjà désactivé avant ce TP). Front `npm test`, 85 réussis, 1 ignoré (déjà en `it.skip`) ; `npm run build` sans erreur. Bout en bout `npm run test:e2e` : non relancé après le retour du sélecteur.

| Test | Ce qu'il prouve | Résultat |
| --- | --- | --- |
| `TenantFilterTest` (9) | 400 sans cabinet ou slug mal formé, sans requête en base ; 404 pour un cabinet inconnu ; un anonyme passe ; un membre passe ; un non-membre reçoit le même 404 qu'un cabinet inconnu ; le contexte est retiré même si la requête échoue ; le paramètre `clinic` remplace l'en-tête, et un désaccord entre les deux donne 400 ; seules les routes partagées se passent de cabinet | vert |
| `TenantIsolationTest` (12) | Deux annuaires disjoints (4 et 7 praticiens) ; un praticien de A est introuvable depuis B ; un rendez-vous pris dans A est absent de la liste, de l'export CSV, de l'historique, de la fiche, de la convocation et du calendrier de B (404) ; il ne peut être ni annulé ni déplacé depuis B ; un créneau de A ne se réserve pas depuis B ; les notifications restent dans leur cabinet ; la recherche en SQL direct et la recherche par proximité ne rendent que le cabinet courant ; une inscription ne rattache qu'au cabinet demandé | vert |
| `RlsDirectSqlTest` (4) | En JDBC direct avec `medislot_app`, sans Spring ni Hibernate : sans variable (ou variable vide) aucune ligne n'est visible ; avec un cabinet seules ses lignes le sont, et la variable ne survit pas au commit ; insérer ou déplacer une ligne vers un autre cabinet est refusé par la politique ; la RLS est activée et forcée sur les cinq tables, et le rôle n'est ni super-utilisateur ni `BYPASSRLS` | vert (rouge sans `V6`) |
| `StatsIsolationTest` (2) | Le défi, section 6 | vert (rouge avant correction) |
| `tenant.test.ts` (9) | Lecture du cabinet dans le chemin et l'hôte, piège du nom d'hôte, couleur réduite à `#rrggbb` | vert |
| `clinic.test.ts` | Cabinet lu dans l'adresse et envoyé dans l'en-tête ; `switchTo` vide le cache avant de basculer, refuse un cabinet qui n'est pas à soi, met à jour l'adresse ; une réponse arrivée après la bascule est jetée ; `afterLogin` garde le cabinet courant ou bascule sur le premier | vert |
| `tenantCache.test.ts` | Les caches des composables sont vidés à la bascule, et une réponse en retard ne les remplit pas | vert |
| `api.test.ts` (2 ajoutés) | `X-Clinic` part à chaque appel une fois le cabinet posé | vert |
| `e2e/tenant.spec.ts` (3) | Application réelle : deux annuaires et deux couleurs ; réservation dans A, passage dans B par l'adresse, l'adresse devient `/c/cabinet-b/`, « Mes rendez-vous » de B ne montre pas celui de A ; une personne connectée sur un cabinet qui n'est pas le sien reçoit 404 de l'API ; un administrateur voit la liste des cabinets, un patient en est refusé | à relancer |

Fuite forcée, dans `e2e/tenant.spec.ts` avec la session de `patient@medislot.test` : `GET /api/appointments/{id}` du rendez-vous de A répond 200 avec `X-Clinic: cabinet-a` et **404** avec `X-Clinic: cabinet-b` ; la liste de B ne contient pas son identifiant.

## 5. Liste des cabinets pour l'administration

L'écran Admin affiche tous les cabinets (nom, ville, praticiens, rendez-vous créés sur 30 jours, date de création), avec recherche et filtre par ville. Il lit `GET /api/platform/clinics`, réservé au rôle `ADMIN` et sans en-tête `X-Clinic`.

- **La vue `platform_clinic`** (`V7`) agrège par cabinet et ne rend que des comptes. Elle appartient au rôle `medislot_platform` (`NOLOGIN`, `NOBYPASSRLS`). Une vue s'exécute avec les droits et la RLS de son propriétaire : les politiques `platform_read`, `FOR SELECT TO medislot_platform`, lui ouvrent `professional` et `appointment` de tous les cabinets.
- **`medislot_app` n'a que `SELECT` sur la vue.** Ses propres lectures des tables restent soumises à `clinic_isolation` : il ne voit jamais de ligne d'un autre cabinet, seulement des totaux.
- **Compte de démonstration** : `admin@medislot.test` (rôle `ADMIN`, membre des deux cabinets pour que « Ouvrir » fonctionne).
- **Créer et modifier un cabinet** : `ClinicController`, `POST /api/clinics` (identifiant, nom, ville, couleur) et `PUT /api/clinics/{slug}` (nom, ville, couleur), réservés au rôle `ADMIN`, sans en-tête `X-Clinic`. Dans la liste : bouton « Créer un cabinet » et « Modifier » sur chaque ligne. Un identifiant déjà pris donne 409, un cabinet inconnu 404. Le cabinet naît vide et l'administrateur qui le crée en devient membre, pour que « Ouvrir » marche tout de suite.
- **L'identifiant ne change pas** : c'est l'adresse du cabinet (`/c/<slug>/`). `V8` donne à `medislot_app` `INSERT` sur `clinic` et `UPDATE` sur les seules colonnes `name`, `city`, `color` (table partagée, sans RLS) ; l'entité marque `slug` `updatable = false`. `V8` ajoute aussi `color` à la vue `platform_clinic`, pour pré-remplir le formulaire.
- **Tests** : `ClinicAdminTest` (13) : création avec premier administrateur (compte existant obligatoire), 409, 400, lecture, modification sans toucher au slug, 404, logo PNG puis SVG (remplacement, en-têtes), logo refusé (type, taille), patient et anonyme refusés ; `ClinicForm.test.ts` (4).
- **Formulaire** (`components/admin/ClinicForm.vue`, création et modification) : nom, identifiant, ville, adresse, couleur, logo et, à la création, e-mail du premier administrateur du cabinet. Ce compte doit déjà exister (400 sinon) : il devient membre du cabinet ; il n'y a pas de rôle d'administrateur par cabinet. `GET /api/clinics/{slug}` pré-remplit la modification.
- **Logo** : `PUT /api/clinics/{slug}/logo` (multipart `file`, PNG ou SVG, 200 Ko max), lu par `GET /api/clinics/{slug}/logo`. Le type est déduit du contenu (signature PNG, racine `<svg>`). Le fichier est écrit sur disque sous `medislot.logo-dir` (`LOGO_DIR`, volume Docker `logos`), nommé d'après l'id du cabinet ; seule la colonne `logo_file` est en base (`V9`, avec `address`). Un SVG peut contenir un script : il est servi avec une CSP `sandbox` sans script et `nosniff`.
- **Pas encore fait** : supprimer un cabinet ; afficher le logo côté patient (seul l'admin le lit aujourd'hui).

## 6. Défi : la fuite de `GET /api/clinic/stats`

- **La fuite.** `StatsController` rendait quatre compteurs. Trois venaient de tables propres (`professional`, `slot`, `appointment`) et étaient justes sans effort. Le quatrième, `patients`, était `users.count()` : `app_user` est une table partagée, sans `clinic_id` ni RLS, donc le compteur était celui de toute la plateforme. Un cabinet y lisait la croissance de l'autre.
- **Le test.** `StatsIsolationTest.activityInBDoesNotMoveTheCountersReadFromA` lit les compteurs depuis A, inscrit un patient dans B et y réserve un créneau, puis relit depuis A. Sur le code d'origine il a échoué : `expected: <{patients=2, ...}> but was: <{patients=3, ...}>`.
- **La correction.** `patients` devient `memberships.countByClinicId(TenantContext.require())`. Le test passe, et vérifie en plus que le compteur de B a bien augmenté de un.
- **La leçon.** Ni `@TenantId` ni la RLS ne protègent une table partagée. Toute lecture de `app_user` ou `membership` dans une route de cabinet doit passer par l'appartenance. `DebugController` listait lui aussi tous les comptes : il ne liste plus que les membres du cabinet courant.

### Ce que le code ne traite pas

- **Rejoindre un second cabinet.** L'inscription rattache au cabinet courant, mais un compte existant ne peut pas en rejoindre un autre (l'inscription répond 409). Cette réponse révèle d'ailleurs qu'une adresse a un compte, tous cabinets confondus.
- **Connexion Google.** Le retour de Google ne porte pas de cabinet : le compte créé rejoint `DEFAULT_CLINIC`.
- **Rôles.** `ADMIN` est un rôle global, pas un rôle par cabinet. Les compteurs de `/api/clinic/stats` sont lisibles par tout membre.
- **Super-utilisateur.** Avec Compose, `medislot` est super-utilisateur : les migrations, un `psql` ou une sauvegarde contournent la RLS. Elle ne protège que ce qui passe par `medislot_app`.
- **Mot de passe de `medislot_app`.** Il est fixé par `V5` ; le changer ensuite demande un `ALTER ROLE` à la main.
- **État en mémoire.** La messagerie, les abonnés aux flux d'événements et le limiteur de connexion vivent dans le processus : ils sont cloisonnés par cabinet mais ne survivent ni à un redémarrage ni à une seconde instance.
- **Exploitation.** Pas de sauvegarde, de restauration, d'export ni de suppression par cabinet ; pas de quota ni de limite de débit par cabinet (un cabinet très actif ralentit l'autre) ; le cabinet n'apparaît ni dans les journaux ni dans les traces OpenTelemetry.
- **Sous-domaines.** `clinicFromHost` est testé, mais l'application n'a été essayée que par le chemin `/c/<cabinet>/` : le mode sous-domaine demande un DNS et un certificat génériques.
- **Navigation.** Le sélecteur bascule sans rechargement, mais le retour arrière du navigateur entre deux cabinets recharge la page.
- **Bases existantes.** Une base créée avant `V5` garde ses 11 praticiens dans `cabinet-a`, et `cabinet-b` reçoit ses 7 praticiens de démonstration : les praticiens de Lyon y figurent alors dans les deux cabinets. Une base neuve (`docker compose down -v`) donne la répartition 4 et 7.

Aucune donnée réelle : uniquement les deux cabinets et les comptes fictifs de démonstration.
