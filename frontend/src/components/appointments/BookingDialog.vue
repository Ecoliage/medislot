<script setup lang="ts">
import BaseModal from '../ui/BaseModal.vue';
import {dateLabel, specialties, timeLabel, type Professional, type Slot} from '../../types';

defineProps<{choice: {professional: Professional; slot: Slot}; moving: boolean; busy: boolean; error: string}>();
const emit = defineEmits<{close: []; confirm: []}>();
</script>

<template>
  <BaseModal :busy="busy" @close="emit('close')">
    <p class="eyebrow dark">DERNIÈRE ÉTAPE</p>
    <h2 id="modal-title">Confirmer votre rendez-vous</h2>
    <div class="booking-summary">
      <h3>{{ choice.professional.name }}</h3>
      <p>{{ specialties[choice.professional.specialty] }}</p>
      <strong>{{ dateLabel(choice.slot.startsAt) }} à {{ timeLabel(choice.slot.startsAt) }}</strong>
      <p>{{ choice.professional.address }}, {{ choice.professional.city }}</p>
    </div>
    <p class="subtle">
      Le créneau sera réservé après confirmation du serveur. Une autre personne peut encore le sélectionner
      entre-temps.
    </p>
    <p v-if="error" class="error-text" role="alert">{{ error }}</p>
    <button class="primary wide" :disabled="busy" @click="emit('confirm')">
      {{ busy ? 'Confirmation…' : moving ? 'Confirmer le déplacement' : 'Confirmer le rendez-vous' }}
    </button>
  </BaseModal>
</template>

<style scoped>
.booking-summary {
  padding: 22px;
  background: #f0f7fb;
  border-radius: 10px;
  margin: 25px 0;
}
.booking-summary strong {
  display: block;
  color: #076b9e;
  margin: 18px 0;
}
.booking-summary p {
  font-size: 14px;
}
</style>
