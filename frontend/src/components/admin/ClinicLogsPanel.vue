<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {loadClinicLogs} from '../../services/adminService';
import type {ClinicLog, PlatformClinic} from '../../types';

const props = defineProps<{clinic: PlatformClinic}>();
const emit = defineEmits<{back: []}>();

const logs = ref<ClinicLog[]>([]);
const loading = ref(true);
const error = ref('');

function formatDate(value: string) {
  return new Date(value).toLocaleString('fr-FR', {timeZone: 'Europe/Paris', dateStyle: 'short', timeStyle: 'medium'});
}

onMounted(async () => {
  try {
    logs.value = await loadClinicLogs(props.clinic.slug);
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="logs" aria-labelledby="logs-title">
    <div class="head">
      <div>
        <h2 id="logs-title">Journal — {{ clinic.name }}</h2>
        <p class="place">{{ clinic.city }} · {{ clinic.slug }} · 100 derniers événements</p>
      </div>
      <button class="link" type="button" @click="emit('back')">← Retour aux cabinets</button>
    </div>
    <p v-if="loading" class="loading">Chargement du journal…</p>
    <p v-else-if="error" class="error-text" role="alert">{{ error }}</p>
    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Événement</th>
            <th scope="col">Rendez-vous</th>
            <th scope="col">Praticien</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td class="date">{{ formatDate(log.createdAt) }}</td>
            <td>{{ log.label }}</td>
            <td>n° {{ log.appointmentId }}</td>
            <td>{{ log.practitioner }}</td>
          </tr>
          <tr v-if="logs.length === 0">
            <td colspan="4" class="empty">Aucun événement pour ce cabinet.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.logs {
  margin-bottom: 40px;
}
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}
.head h2 {
  margin: 0;
}
.place {
  margin: 4px 0 0;
  color: #5b6b78;
  font-size: 14px;
}
.link {
  background: white;
  font: inherit;
  cursor: pointer;
  border: 1px solid #26465c;
  border-radius: 6px;
  padding: 5px 10px;
  color: #26465c;
}
.table-wrap {
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th {
  text-align: left;
  font-size: 13px;
  text-transform: uppercase;
  color: #5b6b78;
  padding: 10px;
  border-bottom: 1px solid #8a99a6;
}
td {
  padding: 12px 10px;
  border-bottom: 1px solid var(--line);
}
.date {
  white-space: nowrap;
}
</style>
