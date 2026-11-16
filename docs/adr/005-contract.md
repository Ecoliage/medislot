# DTO et contrat partagé

## Décision

Les réponses publiques sont des DTO (des `record` Java comme `ProfessionalDto`, `PageDto`, `SlotDto` dans `DirectoryController`), pas les entités JPA. Le contrat se trouve dans `docs/openapi.yaml`. Malgré l'extension, c'est du JSON valide, ce qui permet à `frontend/e2e/contract.spec.ts` de le lire avec `JSON.parse` et de valider avec Ajv les vraies réponses de `/api/professionals`, `/api/professionals/1` et `/api/professionals/1/slots`.

## Alternatives écartées

- **Renvoyer les entités.** Plus court à écrire, mais la forme de la base devient celle de l'API : un renommage de colonne casse le front, et on risque d'exposer un champ qui ne devait pas sortir (le mot de passe de `AppUser`, par exemple).
- **Générer le contrat depuis le code (springdoc).** Il n'est pas dans le `pom.xml`. Le contrat serait toujours à jour, mais le but pédagogique ici est justement de l'écrire et de le confronter au code en séance 4.
- **Générer le code depuis le contrat d'abord (contract first).** Une bonne pratique en équipe, mais lourde pour un TP : génération de clients et de serveurs à configurer.
- **Pas de contrat, on lit le code.** Ça marche tant que le front et le back sont écrits par la même personne.

## Conséquences

Il faut maintenir à la main le mapping entité vers DTO et le fichier de contrat, et rien ne force les deux à rester d'accord à part le test e2e, qui ne couvre que trois routes. Ajouter une propriété facultative est compatible : c'est ce qui a été fait pour `latitude`, `longitude` et `distanceKm` dans `Professional`. Renommer ou supprimer un champ casse les clients existants et se fait en deux temps : d'abord ajouter le nouveau, migrer le front, ensuite retirer l'ancien.

Le schéma n'interdit pas les propriétés supplémentaires (pas de `additionalProperties: false`), c'est pour cela qu'un champ ajouté seulement côté Java ne fait pas échouer le test. Il faut donc penser à le déclarer soi-même.

## Vérification

Lance la pile, puis dans `frontend/` : `npx playwright test e2e/contract.spec.ts`. Pour voir le test servir à quelque chose, retire `fee` de `ProfessionalDto` ou renomme `name` dans le record : la validation Ajv échoue sur le champ obligatoire manquant. Pour valider le fichier lui-même : `python3 -c "import json;json.load(open('docs/openapi.yaml'))"`.
