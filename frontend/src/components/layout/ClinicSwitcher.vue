<script setup lang="ts">
import {computed} from 'vue';
import {useClinic} from '../../stores/clinic';
import {safeColor} from '../../tenant';

const clinic = useClinic();
const name = computed(() => clinic.current?.name ?? '');
const initials = computed(() =>
  name.value
    .split(/\s+/)
    .filter(word => /^\p{Lu}/u.test(word))
    .slice(0, 2)
    .map(word => word[0])
    .join(''),
);
const color = computed(() => safeColor(clinic.current?.color));

async function change(event: Event) {
  const select = event.target as HTMLSelectElement;
  if (!(await clinic.switchTo(select.value))) select.value = clinic.slug;
}
</script>

<template>
  <div v-if="clinic.current" class="clinic">
    <span class="badge" :style="{background: color}" aria-hidden="true">{{ initials }}</span>
    <select v-if="clinic.mine.length > 1" aria-label="Cabinet" :value="clinic.slug" @change="change">
      <option v-for="option in clinic.mine" :key="option.slug" :value="option.slug">{{ option.name }}</option>
    </select>
    <span v-else class="name">{{ name }}</span>
  </div>
</template>

<style scoped>
.clinic {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.badge {
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: white;
  font-size: 12px;
  font-weight: 700;
  display: grid;
  place-items: center;
}
.name,
select {
  color: #26465c;
  font-size: 14px;
  font-weight: 600;
}
select {
  border: 1px solid #bfd5e4;
  border-radius: 8px;
  padding: 6px 8px;
  background: white;
  max-width: 220px;
}
.name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
