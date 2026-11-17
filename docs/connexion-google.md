# Connexion Google

La connexion avec Google est optionnelle. Sans identifiants, l'application démarre normalement et le bouton n'est pas affiché.

Pour l'activer :

1. Crée un client OAuth « Application Web » dans la console Google Cloud.
2. Ajoute `http://localhost:8088/login/oauth2/code/google` dans les URI de redirection autorisées.
3. Copie `.env.example` vers `.env` et renseigne `GOOGLE_CLIENT_ID` et `GOOGLE_CLIENT_SECRET`.
4. Relance `docker compose up -d --build`.
