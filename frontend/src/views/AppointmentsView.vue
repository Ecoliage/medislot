<script setup lang="ts">
import {apiUrl} from '../api';
import CalendarButton from '../components/CalendarButton.vue';
import ConvocationButton from '../components/ConvocationButton.vue';
import {specialties, dateLabel, timeLabel, type Appointment} from '../types';

defineProps<{appointments: Appointment[]}>();
const emit = defineEmits<{
  move: [appointment: Appointment];
  cancel: [id: number];
  history: [id: number];
  search: [];
}>();
</script>

<template>
  <main id="main" class="main">
    <slot name="feedback" />
    <div class="section-title">
      <div>
        <p class="eyebrow dark">VOTRE ESPACE</p>
        <h1>Mes rendez-vous</h1>
      </div>
      <span
        ><a class="text-button" :href="apiUrl('/appointments/export')">Exporter en CSV</a>
        <button class="primary" @click="emit('search')">Prendre rendez-vous</button></span
      >
    </div>
    <p v-if="!appointments.length" class="empty">Vous n’avez pas encore de rendez-vous.</p>
    <article v-for="a in appointments" :key="a.id" class="appointment">
      <div class="appointment-date">
        <strong>{{ dateLabel(a.startsAt) }}</strong
        ><span>{{ timeLabel(a.startsAt) }}</span>
      </div>
      <div class="appointment-person">
        <h2>{{ a.professionalName }}</h2>
        <p>{{ specialties[a.specialty] }} · {{ a.city }}</p>
        <span class="status" :class="{cancelled: a.status === 'CANCELLED'}">{{
          a.status === 'CONFIRMED' ? 'Confirmé' : 'Annulé'
        }}</span>
      </div>
      <div class="appointment-actions">
        <button v-if="a.status === 'CONFIRMED'" @click="emit('move', a)">Déplacer</button
        ><button v-if="a.status === 'CONFIRMED'" class="danger-link" @click="emit('cancel', a.id)">Annuler</button
        ><CalendarButton v-if="a.status === 'CONFIRMED'" :id="a.id" /><ConvocationButton v-if="a.status === 'CONFIRMED'" :id="a.id" /><span v-else class="subtle"
          >Convocation indisponible : rendez-vous annulé</span
        ><button class="text-button" @click="emit('history', a.id)">Historique</button>
      </div>
    </article>
  </main>
</template>

<style scoped>
.appointment-date {
  min-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-transform: capitalize;
  color: #076b9e;
}
.appointment-date span {
  font-size: 25px;
  font-weight: 700;
}
.status {
  font-size: 12px;
  border-radius: 15px;
  background: #e7f5ec;
  color: #266947;
  padding: 5px 10px;
}
.status.cancelled {
  background: #f3eeeb;
  color: #78675d;
}
.danger-link {
  color: #9e4433 !important;
}
@media (max-width: 1050px) {
  .appointment-date {
    min-width: 180px;
  }
}
</style>
