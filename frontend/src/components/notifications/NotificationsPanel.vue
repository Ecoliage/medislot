<script setup lang="ts">
import {dateLabel, type Notice} from '../../types';

defineProps<{notices: Notice[]}>();
const emit = defineEmits<{close: []}>();
</script>

<template>
  <div class="notices">
    <div class="section-title">
      <h2>Vos notifications</h2>
      <button class="text-button" @click="emit('close')">Fermer</button>
    </div>
    <p v-if="!notices.length">Aucune notification pour le moment.</p>
    <p v-for="n in notices" :key="n.id">
      {{ n.message }} <small>{{ dateLabel(n.createdAt) }}</small>
    </p>
  </div>
</template>

<style scoped>
.notices {
  background: white;
  padding: 25px;
  border: 1px solid var(--line);
  border-radius: 12px;
  margin-bottom: 25px;
}
.notices small {
  color: var(--muted);
}
</style>
