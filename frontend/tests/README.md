# Tests du front

Les tests sont regroupés ici et séparés du code applicatif dans `src/`.

- `unit/` : tests des modules isolés. Les sous-dossiers `components/`, `composables/` et `services/` suivent le rangement de `src/`.
- `integration/` : tests des parcours qui assemblent plusieurs composants et leur état. Les appels HTTP sont simulés ; aucun backend n'est requis.
- Les tests de bout en bout Playwright restent dans `frontend/e2e/` et utilisent leur configuration dédiée.

Un fichier de test unitaire reprend le nom du module testé : `NearbyBar.test.ts`, `usePractitionerSearch.test.ts`, etc. Un test d'intégration porte le nom du parcours testé.

Depuis `frontend/` :

```bash
npm test
npm test -- tests/unit
npm test -- tests/integration
npm run build
```

Vitest découvre les fichiers `*.test.ts` dans ces deux dossiers. TypeScript vérifie également les tests lors de la compilation.
