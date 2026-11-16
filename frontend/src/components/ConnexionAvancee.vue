<script setup lang="ts">
import {ref, onMounted} from 'vue';
import {useSession} from '../stores/session';
import {api} from '../api';

const session = useSession();
const email = ref('');
const motDePasse = ref('');
const jeton = ref('');
const nouveau = ref('');
const message = ref('');
const EMAIL_VALIDE = /^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\.[a-zA-Z0-9-]+)*\.[a-zA-Z]{2,}$/;

function suivant(): string {
  const next = new URLSearchParams(window.location.search).get('next') || '/';
  return next.startsWith('/') && !next.startsWith('//') ? next : '/';
}

async function seConnecter() {
  try {
    await session.login(email.value, motDePasse.value);
    window.location.href = suivant();
  } catch (e) {
    message.value = e instanceof Error ? e.message : String(e);
  }
}

async function oublie() {
  if (!EMAIL_VALIDE.test(email.value)) {
    message.value = 'Adresse invalide';
    return;
  }
  const res = await api<{message: string}>('/compte/oubli', {
    method: 'POST',
    body: JSON.stringify({email: email.value}),
  });
  message.value = res.message;
}

async function reinitialiser() {
  try {
    const res = await api<{message: string}>('/compte/reinitialiser', {
      method: 'POST',
      body: JSON.stringify({jeton: jeton.value, nouveauMotDePasse: nouveau.value}),
    });
    message.value = res.message;
    nouveau.value = '';
  } catch (e) {
    message.value = e instanceof Error ? e.message : String(e);
  }
}

onMounted(() => {
  jeton.value = new URLSearchParams(window.location.search).get('jeton') || '';
});
</script>
<template>
  <div>
    <h2>Connexion avancée</h2>
    <form class="auth-form" @submit.prevent="seConnecter">
      <label>Adresse e-mail<input v-model="email" type="email" autocomplete="email" /></label>
      <label>Mot de passe<input v-model="motDePasse" type="password" autocomplete="current-password" /></label>
      <button class="primary" type="submit">Se connecter puis continuer</button>
    </form>
    <h3>Mot de passe oublié</h3>
    <button class="text-button" type="button" @click="oublie">Recevoir un lien de réinitialisation</button>
    <form class="auth-form" @submit.prevent="reinitialiser">
      <label>Jeton<input v-model="jeton" autocomplete="off" /></label>
      <label
        >Nouveau mot de passe<input
          v-model="nouveau"
          type="password"
          minlength="12"
          maxlength="72"
          autocomplete="new-password"
      /></label>
      <button class="primary" type="submit">Changer le mot de passe</button>
    </form>
    <p class="subtle" role="status">{{ message }}</p>
  </div>
</template>
