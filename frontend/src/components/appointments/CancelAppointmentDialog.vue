<script setup lang="ts">
import BaseModal from '../ui/BaseModal.vue';

defineProps<{busy: boolean; error?: string}>();
const emit = defineEmits<{close: []; confirm: []}>();
</script>

<template>
  <BaseModal :busy="busy" @close="emit('close')">
    <h2 id="modal-title">Annuler ce rendez-vous ?</h2>
    <p>Le créneau sera remis à disposition. Vous pourrez prendre un autre rendez-vous quand vous le souhaitez.</p>
    <p v-if="error" class="error-text" role="alert">{{ error }}</p>
    <button class="primary wide" :disabled="busy" @click="emit('confirm')">Confirmer l’annulation</button>
    <button type="button" class="text-button" :disabled="busy" @click="emit('close')">Conserver mon rendez-vous</button>
  </BaseModal>
</template>
