<script setup lang="ts">
import {ref} from 'vue';
import ClinicForm from '../components/admin/ClinicForm.vue';
import ClinicList from '../components/admin/ClinicList.vue';
import ClinicLogsPanel from '../components/admin/ClinicLogsPanel.vue';
import ClinicStatsPanel from '../components/admin/ClinicStatsPanel.vue';
import type {PlatformClinic} from '../types';

/** null: the list; '' : creating a clinic; a slug: editing that clinic. */
const editing = ref<string | null>(null);
const inspected = ref<PlatformClinic | null>(null);
const journal = ref<PlatformClinic | null>(null);
</script>

<template>
  <main id="main" class="main">
    <div class="section-title">
      <div>
        <p class="eyebrow dark">ADMINISTRATION</p>
        <h1>Cabinets</h1>
      </div>
    </div>
    <ClinicForm
      v-if="editing !== null"
      :key="editing"
      :slug="editing"
      @saved="editing = null"
      @cancel="editing = null"
    />
    <ClinicStatsPanel v-else-if="inspected" :key="inspected.slug" :clinic="inspected" @back="inspected = null" />
    <ClinicLogsPanel v-else-if="journal" :key="journal.slug" :clinic="journal" @back="journal = null" />
    <ClinicList
      v-else
      @create="editing = ''"
      @edit="slug => (editing = slug)"
      @stats="clinic => (inspected = clinic)"
      @logs="clinic => (journal = clinic)"
    />
  </main>
</template>
