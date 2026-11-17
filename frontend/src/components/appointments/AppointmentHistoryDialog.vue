<script setup lang="ts">
import BaseModal from '../ui/BaseModal.vue';
import {dateLabel, timeLabel} from '../../types';

defineProps<{history: {action: string; label: string; createdAt: string}[]}>();
const emit = defineEmits<{close: []}>();
</script>

<template>
  <BaseModal @close="emit('close')">
    <h2 id="modal-title">Historique du rendez-vous</h2>
    <ol class="timeline">
      <li v-for="(entry, index) in history" :key="index">
        <strong>{{ entry.label }}</strong>
        <p>{{ dateLabel(entry.createdAt) }} à {{ timeLabel(entry.createdAt) }}</p>
      </li>
    </ol>
  </BaseModal>
</template>

<style scoped>
.timeline {
  padding-left: 20px;
}
.timeline li {
  padding: 10px;
}
.timeline p {
  font-size: 13px;
  color: var(--muted);
}
</style>
