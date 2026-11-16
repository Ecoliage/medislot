# Exercice : un quality gate SonarQube

Cet exercice demande un serveur SonarQube et un jeton de projet, que le dépôt ne fournit pas. Rien n'est lancé en local par la validation du TP, et la CI livrée (`.github/workflows/ci.yml`) ne contient aucune étape Sonar : c'est toi qui l'ajoutes. De même, le `pom.xml` du back n'a ni plugin JaCoCo ni scanner, tu devras les configurer.

1. Crée un projet privé de démonstration sur ton serveur SonarQube.
2. Définis `SONAR_HOST_URL` et `SONAR_TOKEN` dans l'environnement du scanner, ou dans les secrets GitHub si tu passes par la CI. Ne les écris jamais dans un fichier du dépôt.
3. Lance d'abord `mvn -f backend/pom.xml verify` et `npm test` dans `frontend/`, pour savoir que tout est vert avant d'analyser. Configure ensuite un scanner Java adapté à la version de Java du projet (25) en lui indiquant `backend/target/classes`, et fais analyser aussi `frontend/src`.
4. Ajoute les rapports de couverture JaCoCo (Java) et LCOV (front, via `@vitest/coverage-v8`, déjà présent dans les dépendances de dev), mais seulement quand tu les as réellement générés : vérifie que les fichiers existent avant de les déclarer à Sonar, sinon la couverture affichée sera de 0 % ou fausse.
5. Fixe un seuil sur le nouveau code, introduis volontairement une duplication, constate que le quality gate échoue, puis corrige.
6. Note le résultat, l'URL de l'analyse et le commit analysé. Compare ensuite ce que Sonar te dit avec ce que disent les tests de comportement (`BookingIntegrationTest`, Playwright) : Sonar repère des défauts de code, pas une règle de réservation violée, et inversement.

Référence : https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/scanners/sonarscanner-for-maven
