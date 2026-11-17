<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import {loadClinicPractitioners, loadClinics, loadPractitionerWeek} from '../../services/adminService';
import type {CalendarEntry, ClinicPractitioner, PlatformClinic} from '../../types';

const props = defineProps<{
  slug?: string;
}>();
const emit = defineEmits<{clinics: []}>();

const PARIS = 'Europe/Paris';
const WEEKS_AROUND = 8;
const STATUSES: Record<string, {label: string; tone: string}> = {
  CONFIRMED: {label: 'Confirmé', tone: 'confirmed'},
  CANCELLED: {label: 'Annulé', tone: 'cancelled'},
};

const clinics = ref<PlatformClinic[]>([]);
const practitioners = ref<ClinicPractitioner[]>([]);
const entries = ref<CalendarEntry[]>([]);
const clinicSlug = ref('');
const practitionerId = ref<number | null>(null);
const week = ref(mondayOf(new Date()));
const status = ref('');
const loading = ref(true);
const error = ref('');

const clinic = computed(() => clinics.value.find(c => c.slug === clinicSlug.value));
const practitioner = computed(() => practitioners.value.find(p => p.id === practitionerId.value));
const visible = computed(() => entries.value.filter(e => !status.value || e.status === status.value));

/** Weeks as Mondays (YYYY-MM-DD), around the current one. */
const weeks = computed(() => {
  const current = new Date(mondayOf(new Date()) + 'T12:00:00');
  return Array.from({length: 2 * WEEKS_AROUND + 1}, (_, i) => {
    const day = new Date(current);
    day.setDate(day.getDate() + 7 * (i - WEEKS_AROUND));
    return isoDay(day);
  });
});

function isoDay(date: Date) {
  return date.toLocaleDateString('sv-SE', {timeZone: PARIS});
}

function mondayOf(date: Date) {
  const day = new Date(isoDay(date) + 'T12:00:00');
  day.setDate(day.getDate() - ((day.getDay() + 6) % 7));
  return isoDay(day);
}

function weekLabel(monday: string) {
  const [, month, day] = monday.split('-');
  return `Semaine du ${day}/${month}`;
}

function dayLabel(date: string) {
  return new Date(date).toLocaleDateString('fr-FR', {weekday: 'short', day: '2-digit', month: '2-digit', timeZone: PARIS});
}

function timeLabel(date: string) {
  return new Date(date).toLocaleTimeString('fr-FR', {hour: '2-digit', minute: '2-digit', timeZone: PARIS});
}

async function run(work: () => Promise<void>) {
  loading.value = true;
  error.value = '';
  try {
    await work();
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}

async function loadPractitioners() {
  practitioners.value = [];
  practitionerId.value = null;
  entries.value = [];
  if (!clinicSlug.value) return;
  await run(async () => {
    practitioners.value = await loadClinicPractitioners(clinicSlug.value);
    practitionerId.value = practitioners.value[0]?.id ?? null;
  });
}

async function loadWeek() {
  entries.value = [];
  if (!clinicSlug.value || practitionerId.value === null) return;
  const slug = clinicSlug.value;
  const id = practitionerId.value;
  const monday = week.value;
  await run(async () => {
    const result = await loadPractitionerWeek(slug, id, monday);
    if (slug === clinicSlug.value && id === practitionerId.value && monday === week.value) entries.value = result;
  });
}

watch(clinicSlug, loadPractitioners);
watch([practitionerId, week], loadWeek);

onMounted(() =>
  run(async () => {
    clinics.value = await loadClinics();
    const wanted = clinics.value.find(c => c.slug === props.slug) ?? clinics.value[0];
    clinicSlug.value = wanted?.slug ?? '';
  }),
);
</script>

<template>
  <section class="calendar" aria-labelledby="calendar-title">
    <div class="pickers">
      <label>
        <span>Cabinet</span>
        <select v-model="clinicSlug">
          <option v-for="c in clinics" :key="c.slug" :value="c.slug">{{ c.name }}</option>
        </select>
      </label>
      <label>
        <span>Praticien</span>
        <select v-model="practitionerId" :disabled="practitioners.length === 0">
          <option v-for="p in practitioners" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
      </label>
    </div>

    <nav class="breadcrumb" aria-label="Fil d’Ariane">
      <button type="button" class="crumb" @click="emit('clinics')">Cabinets</button>
      <template v-if="clinic">
        <span aria-hidden="true">›</span>
        <span>{{ clinic.name }}</span>
      </template>
      <template v-if="practitioner">
        <span aria-hidden="true">›</span>
        <span aria-current="page">{{ practitioner.name }}</span>
      </template>
    </nav>

    <div class="head">
      <h2 id="calendar-title">Rendez-vous<template v-if="practitioner"> · {{ practitioner.name }}</template></h2>
      <div class="filters">
        <select v-model="week" aria-label="Semaine">
          <option v-for="monday in weeks" :key="monday" :value="monday">{{ weekLabel(monday) }}</option>
        </select>
        <select v-model="status" aria-label="Statut">
          <option value="">Statut : tous</option>
          <option v-for="(s, key) in STATUSES" :key="key" :value="key">{{ s.label }}</option>
        </select>
      </div>
    </div>

    <p class="notice">
      <span class="notice-icon" aria-hidden="true">i</span>
      Vue minimisée : les patients sont pseudonymisés. Pour toute question sur un patient, s’adresser au cabinet.
    </p>

    <p v-if="error" class="error-text" role="alert">{{ error }}</p>
    <div v-else class="table-wrap" :aria-busy="loading">
      <table>
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Heure</th>
            <th scope="col">Patient</th>
            <th scope="col">Statut</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="e in visible" :key="e.id">
            <td>{{ dayLabel(e.startsAt) }}</td>
            <td>{{ timeLabel(e.startsAt) }}</td>
            <td>{{ e.patient }}</td>
            <td>
              <span class="badge" :class="STATUSES[e.status]?.tone">{{ STATUSES[e.status]?.label ?? e.status }}</span>
            </td>
          </tr>
          <tr v-if="!loading && visible.length === 0">
            <td colspan="4" class="empty">Aucun rendez-vous cette semaine.</td>
          </tr>
          <tr v-if="loading && visible.length === 0">
            <td colspan="4" class="empty">Chargement…</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.calendar {
  margin-bottom: 40px;
}
.pickers {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
}
.pickers label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--muted);
}
select {
  border: 1px solid #a9b6c0;
  border-radius: 6px;
  padding: 10px 12px;
  background: white;
  font-size: 15px;
  color: #4d5b66;
  min-width: 0;
}
.breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--muted);
}
.crumb {
  background: none;
  padding: 0;
  color: var(--muted);
  font: inherit;
  cursor: pointer;
}
.crumb:hover {
  color: var(--navy);
  text-decoration: underline;
}
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 14px 0 16px;
}
.head h2 {
  margin: 0;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.notice {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 16px;
  padding: 12px 14px;
  background: #edf5fc;
  border-left: 4px solid var(--blue);
  color: #1e2a33;
  font-size: 15px;
}
.notice-icon {
  flex: none;
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--blue);
  color: white;
  font-weight: 700;
  font-size: 14px;
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
  border-bottom: 1px solid #8a99a6;
}
.empty {
  color: var(--muted);
}
.badge {
  display: inline-block;
  padding: 2px 9px;
  border: 1px solid currentColor;
  border-radius: 999px;
  font-size: 13px;
}
.badge.confirmed {
  color: #1e2a33;
}
.badge.cancelled {
  color: #6b7780;
}
</style>
