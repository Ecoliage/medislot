<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {loadClinicStats} from '../../services/adminService';
import {clinicPath} from '../../tenant';
import type {ClinicStats, PlatformClinic} from '../../types';

const props = defineProps<{clinic: PlatformClinic}>();
const emit = defineEmits<{back: []}>();

const stats = ref<ClinicStats | null>(null);
const loading = ref(true);
const error = ref('');

const tiles = computed(() => [
  {label: 'Rendez-vous', value: stats.value?.appointments},
  {label: 'RDV sur 30 jours', value: props.clinic.appointments30d},
  {label: 'Praticiens', value: stats.value?.practitioners},
  {label: 'Patients', value: stats.value?.patients},
  {label: 'Créneaux', value: stats.value?.slots},
]);

onMounted(async () => {
  try {
    stats.value = await loadClinicStats(props.clinic.slug);
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="stats" aria-labelledby="stats-title">
    <div class="head">
      <div>
        <h2 id="stats-title">Statistiques — {{ clinic.name }}</h2>
        <p class="place">{{ clinic.city }} · {{ clinic.slug }}</p>
      </div>
      <div class="actions">
        <button class="link" type="button" @click="emit('back')">← Retour aux cabinets</button>
        <a class="link" :href="clinicPath(clinic.slug)">Ouvrir</a>
      </div>
    </div>
    <p v-if="loading" class="loading">Chargement des statistiques…</p>
    <p v-else-if="error" class="error-text" role="alert">{{ error }}</p>
    <dl v-else class="tiles">
      <div v-for="tile in tiles" :key="tile.label" class="tile">
        <dt>{{ tile.label }}</dt>
        <dd>{{ tile.value }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.stats {
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
.actions {
  display: flex;
  gap: 8px;
}
.link {
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
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin: 0;
}
.tile {
  background: white;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 16px;
}
.tile dt {
  font-size: 13px;
  text-transform: uppercase;
  color: #5b6b78;
}
.tile dd {
  margin: 8px 0 0;
  font-size: 30px;
  font-weight: 600;
  color: var(--navy);
}
</style>
