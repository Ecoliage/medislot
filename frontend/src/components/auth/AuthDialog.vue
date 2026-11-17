<script setup lang="ts">
import {onMounted, ref} from 'vue';
import BaseModal from '../ui/BaseModal.vue';
import {googleLoginEnabled, registerAccount, signInWithGoogle} from '../../services/authService';
import {useSession} from '../../stores/session';
import {errorMessage} from '../../errors';

const props = defineProps<{initialError?: string}>();
const emit = defineEmits<{close: []; authenticated: []}>();
const session = useSession();
const register = ref(false);
const email = ref('');
const password = ref('');
const name = ref('');
const busy = ref(false);
const error = ref(props.initialError ?? '');
const googleEnabled = ref(false);
const demoEnabled = import.meta.env.VITE_DEMO === 'true';

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

onMounted(() => {
  googleLoginEnabled()
    .then(enabled => { googleEnabled.value = enabled; })
    .catch(() => {});
});
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
    <button v-if="googleEnabled" type="button" class="google" :disabled="busy" @click="signInWithGoogle">
      <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" aria-hidden="true" viewBox="0 0 48 48">
        <path
          fill="#FFC107"
          d="M43.611,20.083H42V20H24v8h11.303c-1.649,4.657-6.08,8-11.303,8c-6.627,0-12-5.373-12-12c0-6.627,5.373-12,12-12c3.059,0,5.842,1.154,7.961,3.039l5.657-5.657C34.046,6.053,29.268,4,24,4C12.955,4,4,12.955,4,24c0,11.045,8.955,20,20,20c11.045,0,20-8.955,20-20C44,22.659,43.862,21.35,43.611,20.083z"
        ></path>
        <path
          fill="#FF3D00"
          d="M6.306,14.691l6.571,4.819C14.655,15.108,18.961,12,24,12c3.059,0,5.842,1.154,7.961,3.039l5.657-5.657C34.046,6.053,29.268,4,24,4C16.318,4,9.656,8.337,6.306,14.691z"
        ></path>
        <path
          fill="#4CAF50"
          d="M24,44c5.166,0,9.86-1.977,13.409-5.192l-6.19-5.238C29.211,35.091,26.715,36,24,36c-5.202,0-9.619-3.317-11.283-7.946l-6.522,5.025C9.505,39.556,16.227,44,24,44z"
        ></path>
        <path
          fill="#1976D2"
          d="M43.611,20.083H42V20H24v8h11.303c-0.792,2.237-2.231,4.166-4.087,5.571c0.001-0.001,0.002-0.001,0.003-0.002l6.19,5.238C36.971,39.205,44,34,44,24C44,22.659,43.862,21.35,43.611,20.083z"
        ></path>
      </svg>
      Se connecter avec Google
    </button>
    <button type="button" class="text-button" :disabled="busy" @click="register = !register">
      {{ register ? 'J’ai déjà un compte' : 'Créer un compte' }}
    </button>
    <details v-if="demoEnabled" class="demo-credentials">
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
