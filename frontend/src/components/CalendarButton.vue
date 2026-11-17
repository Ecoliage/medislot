<script setup lang="ts">
import {apiUrl} from '../api';

const props = defineProps<{id: number}>();

async function downloadIcs() {
  const ics = await fetch(apiUrl(`/calendar/${props.id}`), {
    credentials: 'same-origin',
    headers: {Accept: 'text/calendar'},
  });
  if (!ics.ok) throw new Error(String(ics.status));

  const url = URL.createObjectURL(await ics.blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = `rendez-vous-${props.id}.ics`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
</script>
<template>
  <div class="convocation">
    <button type="button" class="text-button" @click="downloadIcs">
      Ajouter au calendrier
    </button>
  </div>
</template>
