<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {loadClinics} from '../../services/adminService';
import {clinicPath} from '../../tenant';
import {clinicsToCsv, downloadCsv} from '../../clinicCsv';
import type {PlatformClinic} from '../../types';

const clinics = ref<PlatformClinic[]>([]);
const search = ref('');
const city = ref('');
const loading = ref(true);
const error = ref('');

const cities = computed(() => [...new Set(clinics.value.map(c => c.city))].sort((a, b) => a.localeCompare(b, 'fr')));
const visible = computed(() => {
  const term = search.value.trim().toLocaleLowerCase('fr');
  return clinics.value.filter(
    c =>
      (!city.value || c.city === city.value) &&
      (!term || c.name.toLocaleLowerCase('fr').includes(term) || c.slug.includes(term)),
  );
});

const emit = defineEmits<{create: []; edit: [slug: string]; calendar: [slug: string]}>();

function exportCsv() {
  downloadCsv(clinicsToCsv(visible.value), `cabinets.csv`);
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('fr-FR', {timeZone: 'Europe/Paris'});
}

onMounted(async () => {
  try {
    clinics.value = await loadClinics();
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="clinics" aria-labelledby="clinics-title">
    <div class="head">
      <h2 id="clinics-title">Cabinets ({{ clinics.length }})</h2>
      <div class="head-actions">
        <button class="export-button" type="button" :disabled="loading || visible.length === 0" @click="exportCsv">
          Exporter en CSV
        </button>
        <button class="create-button" type="button" @click="emit('create')">+ Créer un cabinet</button>
      </div>
    </div>
    <div class="filters">
      <input v-model="search" type="search" placeholder="Rechercher un cabinet…" aria-label="Rechercher un cabinet" />
      <select v-model="city" aria-label="Ville">
        <option value="">Ville : toutes</option>
        <option v-for="name in cities" :key="name" :value="name">{{ name }}</option>
      </select>
    </div>
    <p v-if="loading" class="loading">Chargement des cabinets…</p>
    <p v-else-if="error" class="error-text" role="alert">{{ error }}</p>
    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">Cabinet</th>
            <th scope="col">Ville</th>
            <th scope="col">Praticiens</th>
            <th scope="col">RDV (30 j)</th>
            <th scope="col">Créé le</th>
            <th scope="col"><span class="sr-only">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in visible" :key="c.slug">
            <td>
              <strong>{{ c.name }}</strong>
              <span class="slug">{{ c.slug }}</span>
            </td>
            <td>{{ c.city }}</td>
            <td>{{ c.practitioners }}</td>
            <td>{{ c.appointments30d }}</td>
            <td>{{ formatDate(c.createdAt) }}</td>
            <td class="row-actions">
              <button class="open" type="button" :aria-label="'Modifier ' + c.name" @click="emit('edit', c.slug)">Modifier</button>
              <button class="open" type="button" :aria-label="'Rendez-vous de ' + c.name" @click="emit('calendar', c.slug)">
                Rendez-vous
              </button>
              <a class="open" :href="clinicPath(c.slug)">Ouvrir</a>
            </td>
          </tr>
          <tr v-if="visible.length === 0">
            <td colspan="6" class="empty">Aucun cabinet ne correspond.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.clinics {
  margin-bottom: 40px;
}
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.export-button {
  background: white;
  color: #1e2a33;
  border: 1px solid #1e2a33;
  border-radius: 6px;
  padding: 10px 18px;
  font-weight: 500;
  font-size: 15px;
}
.export-button:hover:not(:disabled) {
  background: #f0f3f5;
}
.export-button:disabled {
  opacity: 0.6;
  cursor: default;
}
.create-button {
  background: #1e2a33;
  color: white;
  border-radius: 6px;
  padding: 11px 20px;
  font-weight: 500;
  font-size: 15px;
}
.create-button:hover {
  background: #2d3d49;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 16px 0;
}
.filters input,
.filters select {
  border: 1px solid #bfd5e4;
  border-radius: 8px;
  padding: 10px 12px;
  background: white;
  min-width: 0;
}
.filters input {
  flex: 0 1 240px;
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
  vertical-align: middle;
}
.slug {
  display: block;
  font-size: 13px;
  color: #5b6b78;
}
.row-actions {
  white-space: nowrap;
}
.row-actions .open + .open {
  margin-left: 8px;
}
.open {
  display: inline-block;
  background: white;
  font: inherit;
  cursor: pointer;
  border: 1px solid #26465c;
  border-radius: 6px;
  padding: 5px 10px;
  color: #26465c;
  text-decoration: none;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
