<script setup lang="ts">
defineProps<{error: string; success: string}>();
const emit = defineEmits<{retry: []}>();
</script>

<template>
  <p v-if="error" role="alert" class="alert error">
    {{ error }} <button class="text-button" @click="emit('retry')">Réessayer</button>
  </p>
  <p v-if="success" role="status" class="alert success">{{ success }}</p>
</template>
