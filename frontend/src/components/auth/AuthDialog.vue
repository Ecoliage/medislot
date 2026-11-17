<script setup lang="ts">
import {ref} from 'vue';
import BaseModal from '../ui/BaseModal.vue';
import {registerAccount} from '../../services/authService';
import {useSession} from '../../stores/session';
import {errorMessage} from '../../errors';

const emit = defineEmits<{close: []; authenticated: []}>();
const session = useSession();
const register = ref(false);
const email = ref('');
const password = ref('');
const name = ref('');
const busy = ref(false);
const error = ref('');

async function authenticate() {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    if (register.value) await registerAccount({name: name.value, email: email.value, password: password.value});
    await session.login(email.value, password.value);
    password.value = '';
    emit('authenticated');
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <BaseModal :busy="busy" :focus-key="register ? 'register' : 'login'" @close="emit('close')">
    <p class="eyebrow dark">VOTRE ESPACE MEDISLOT</p>
    <h2 id="modal-title">{{ register ? 'Créer votre compte' : 'Heureux de vous retrouver' }}</h2>
    <p class="subtle">Connectez-vous pour confirmer et retrouver vos rendez-vous.</p>
    <form class="auth-form" @submit.prevent="authenticate">
      <label v-if="register">Votre nom<input v-model="name" required maxlength="120" autocomplete="name" /></label>
      <label>Adresse e-mail<input v-model="email" type="email" required autocomplete="email" /></label>
      <label>
        Mot de passe<input
          v-model="password"
          type="password"
          required
          :minlength="register ? 12 : 1"
          maxlength="72"
          :autocomplete="register ? 'new-password' : 'current-password'"
        />
      </label>
      <p v-if="error" role="alert" class="error-text">{{ error }}</p>
      <button class="primary" :disabled="busy">
        {{ busy ? 'Connexion…' : register ? 'Créer mon compte' : 'Se connecter' }}
      </button>
    </form>
    <button type="button" class="text-button" :disabled="busy" @click="register = !register">
      {{ register ? 'J’ai déjà un compte' : 'Créer un compte' }}
    </button>
    <details class="demo-credentials">
      <summary>Compte de démonstration</summary>
      <p>patient@medislot.test<br />MediSlot-demo-2026!</p>
    </details>
  </BaseModal>
</template>

<style scoped>
.demo-credentials {
  font-size: 12px;
  margin-top: 24px;
  color: var(--muted);
}
</style>
