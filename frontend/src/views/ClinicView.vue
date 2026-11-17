<script setup lang="ts">
import {ref} from 'vue';
import CalendarList from '../components/admin/CalendarList.vue';
import ClinicForm from '../components/admin/ClinicForm.vue';
import ClinicList from '../components/admin/ClinicList.vue';
import ClinicLogsPanel from '../components/admin/ClinicLogsPanel.vue';
import PlatformJournalPanel from '../components/admin/PlatformJournalPanel.vue';
import ClinicStatsPanel from '../components/admin/ClinicStatsPanel.vue';
import type {PlatformClinic} from '../types';

type Tab = 'clinics' | 'calendar';

const tab = ref<Tab>('clinics');
/** null: the list; '' : creating a clinic; a slug: editing that clinic. */
const editing = ref<string | null>(null);
const inspected = ref<PlatformClinic | null>(null);
const journal = ref<PlatformClinic | null>(null);
const platformJournal = ref(false);
/** Clinic the calendar opens on. */
const calendarClinic = ref<string | undefined>();

function show(next: Tab) {
  tab.value = next;
  editing.value = null;
  inspected.value = null;
  journal.value = null;
  platformJournal.value = false;
}

function openCalendar(slug?: string) {
  calendarClinic.value = slug;
  show('calendar');
}
</script>

<template>
  <main id="main" class="main">
    <div class="section-title">
      <div>
        <p class="eyebrow dark">ADMINISTRATION</p>
        <h1>{{ tab === 'clinics' ? 'Cabinets' : 'Rendez-vous' }}</h1>
      </div>
    </div>
    <div class="tabs" role="group" aria-label="Vue d’administration">
      <button type="button" :aria-pressed="tab === 'clinics'" @click="show('clinics')">Cabinets</button>
      <button type="button" :aria-pressed="tab === 'calendar'" @click="openCalendar(calendarClinic)">Rendez-vous</button>
    </div>
    <template v-if="tab === 'clinics'">
      <ClinicForm
        v-if="editing !== null"
        :key="editing"
        :slug="editing"
        @saved="editing = null"
        @cancel="editing = null"
      />
      <ClinicStatsPanel v-else-if="inspected" :key="inspected.slug" :clinic="inspected" @back="inspected = null" />
      <ClinicLogsPanel v-else-if="journal" :key="journal.slug" :clinic="journal" @back="journal = null" />
      <PlatformJournalPanel v-else-if="platformJournal" @back="platformJournal = false" />
      <ClinicList
        v-else
        @journal="platformJournal = true"
        @create="editing = ''"
        @edit="slug => (editing = slug)"
        @calendar="openCalendar"
        @stats="clinic => (inspected = clinic)"
        @logs="clinic => (journal = clinic)"
      />
    </template>
    <CalendarList v-else :key="calendarClinic" :slug="calendarClinic" @clinics="show('clinics')" />
  </main>
</template>

<style scoped>
.tabs {
  display: inline-flex;
  margin-bottom: 24px;
  border: 1px solid #1e2a33;
  border-radius: 6px;
  overflow: hidden;
}
.tabs button {
  background: white;
  color: #1e2a33;
  padding: 9px 18px;
  font-size: 15px;
  font-weight: 500;
}
.tabs button + button {
  border-left: 1px solid #1e2a33;
}
.tabs button[aria-pressed='true'] {
  background: #1e2a33;
  color: white;
}
</style>
