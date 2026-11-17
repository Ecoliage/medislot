<script setup lang="ts">
import BaseModal from '../ui/BaseModal.vue';
import {specialties, type Professional} from '../../types';

defineProps<{professional: Professional}>();
const emit = defineEmits<{close: []}>();
</script>

<template>
  <BaseModal @close="emit('close')">
    <p class="eyebrow dark">VOTRE PRATICIEN</p>
    <h2 id="modal-title">{{ professional.name }}</h2>
    <p class="specialty">{{ specialties[professional.specialty] }}</p>
    <p>{{ professional.bio }}</p>
    <h3>Le cabinet</h3>
    <p>{{ professional.address }}, {{ professional.city }}</p>
    <h3>Consultation</h3>
    <p>{{ professional.fee }} € · 30 minutes · Au cabinet</p>
    <button class="primary wide" @click="emit('close')">Voir les disponibilités</button>
  </BaseModal>
</template>
