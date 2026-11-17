<script setup lang="ts">
import {ref} from 'vue';
import ClinicForm from '../components/admin/ClinicForm.vue';
import ClinicList from '../components/admin/ClinicList.vue';

/** null: the list; '' : creating a clinic; a slug: editing that clinic. */
const editing = ref<string | null>(null);
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
    <ClinicList v-else @create="editing = ''" @edit="slug => (editing = slug)" />
  </main>
</template>
