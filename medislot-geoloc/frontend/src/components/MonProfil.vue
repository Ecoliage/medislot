<script setup lang="ts">
import {ref, onMounted} from 'vue';
import {useSession} from '../stores/session';
import {api} from '../api';

interface Profil {
  id: number;
  name: string;
  email: string;
  role: string;
}

const session = useSession();
const profil = ref<Profil | null>(null);
const nom = ref('');
const email = ref('');
const siteWeb = ref('https://exemple.test/mon-blog');
const message = ref('');
const avatarUrl = ref('');

function lienSite(): string {
  try {
    const url = new URL(siteWeb.value);
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : '';
  } catch {
    return '';
  }
}

async function charger() {
  if (!session.user) return;
  profil.value = await api<Profil>('/patients/' + session.user.id);
  nom.value = profil.value.name;
  email.value = profil.value.email;
}

async function enregistrer() {
  if (!session.user) return;
  profil.value = await api<Profil>('/patients/' + session.user.id, {
    method: 'PUT',
    body: JSON.stringify({name: nom.value, email: email.value}),
  });
  message.value = 'Profil enregistré pour ' + profil.value.name;
}

async function envoyerAvatar(e: Event) {
  const input = e.target as HTMLInputElement;
  if (!session.user || !input.files || input.files.length === 0) return;
  const donnees = new FormData();
  donnees.append('fichier', input.files[0]);
  const res = await api<{url: string}>('/patients/' + session.user.id + '/avatar', {method: 'POST', body: donnees});
  avatarUrl.value = res.url;
  message.value = 'Avatar enregistré';
}

onMounted(() => {
  charger();
});
</script>
<template>
  <div>
    <h2>Mon profil</h2>
    <p v-if="profil">Bonjour {{ profil.name }} ({{ profil.role }})</p>
    <form class="auth-form" @submit.prevent="enregistrer">
      <label>Nom<input v-model="nom" maxlength="120" autocomplete="name" /></label>
      <label>E-mail<input v-model="email" type="email" autocomplete="email" /></label>
      <label>Site web<input v-model="siteWeb" type="url" /></label>
      <button class="primary" type="submit">Enregistrer</button>
    </form>
    <p v-if="lienSite()"><a :href="lienSite()" target="_blank" rel="noopener noreferrer">Voir mon site</a></p>
    <label>Photo de profil <input type="file" accept="image/*" @change="envoyerAvatar" /></label>
    <p v-if="avatarUrl"><img :src="avatarUrl" alt="Avatar" width="80" /></p>
    <p class="subtle">{{ message }}</p>
  </div>
</template>
