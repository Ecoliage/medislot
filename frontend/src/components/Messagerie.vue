<script setup lang="ts">
import {ref, onMounted} from 'vue';
import {api, apiUrl} from '../api';

interface Message {
  id: number;
  patientId: number;
  auteur: string;
  contenu: string;
  envoyeLe: string;
}

const messages = ref<Message[]>([]);
const contenu = ref('');
const dernier = ref('');

async function charger() {
  messages.value = await api<Message[]>('/messages');
}

async function envoyer() {
  if (contenu.value.trim() === '') return;
  await api('/messages', {method: 'POST', body: JSON.stringify({contenu: contenu.value})});
  dernier.value = 'Dernier message envoyé : ' + contenu.value;
  contenu.value = '';
  charger();
}

onMounted(() => {
  charger();
});
</script>
<template>
  <div>
    <h2>Messagerie avec le cabinet</h2>
    <form class="auth-form" @submit.prevent="envoyer">
      <label>Votre message<textarea v-model="contenu" rows="3" maxlength="2000"></textarea></label>
      <button class="primary" type="submit">Envoyer</button>
    </form>
    <p class="subtle">{{ dernier }}</p>
    <article v-for="m in messages" :key="m.id" class="appointment">
      <div class="appointment-person">
        <strong>{{ m.auteur }}</strong>
        <p>{{ m.contenu }}</p>
      </div>
      <div class="appointment-actions">
        <a :href="apiUrl('/messages/' + m.id + '/apercu')" target="_blank" rel="noopener">Ouvrir</a>
      </div>
    </article>
  </div>
</template>
