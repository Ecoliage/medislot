<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {loadPlatformEvents} from '../../services/adminService';
import type {PlatformEvent} from '../../types';

const emit = defineEmits<{back: []}>();

const events = ref<PlatformEvent[]>([]);
const loading = ref(true);
const error = ref('');

function formatDate(value: string) {
  return new Date(value).toLocaleString('fr-FR', {timeZone: 'Europe/Paris', dateStyle: 'short', timeStyle: 'medium'});
}

onMounted(async () => {
  try {
    events.value = await loadPlatformEvents();
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="journal" aria-labelledby="journal-title">
    <div class="head">
      <div>
        <h2 id="journal-title">Journal des administrateurs</h2>
        <p class="place">100 dernières actions sur les cabinets</p>
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
            <th scope="col">Action</th>
            <th scope="col">Cabinet</th>
            <th scope="col">Administrateur</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="event in events" :key="event.id">
            <td class="date">{{ formatDate(event.createdAt) }}</td>
            <td>{{ event.label }}</td>
            <td>
              <strong>{{ event.clinicName }}</strong>
              <span class="slug">{{ event.clinicSlug }}</span>
            </td>
            <td>{{ event.actorEmail }}</td>
          </tr>
          <tr v-if="events.length === 0">
            <td colspan="4" class="empty">Aucune action enregistrée.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.journal {
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
.slug {
  display: block;
  font-size: 13px;
  color: #5b6b78;
}
</style>
