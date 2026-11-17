<script setup lang="ts">
import {ref} from 'vue';
import {GEO_MESSAGES, GeoError, geocode, getPosition, type Coordinates, type Place} from '../geo';

const props = withDefaults(
  defineProps<{
    locate?: () => Promise<Coordinates>;
    geocoder?: (address: string) => Promise<Place>;
  }>(),
  {
    locate: () => getPosition(),
    geocoder: (address: string) => geocode(address),
  },
);

const emit = defineEmits<{position: [place: Place]}>();

const address = ref('');
const waiting = ref(false);
const message = ref('');

async function run(find: () => Promise<Place>) {
  waiting.value = true;
  message.value = '';
  try {
    const place = await find();
    emit('position', place);
  } catch (e) {
    message.value = e instanceof GeoError ? e.message : GEO_MESSAGES.unavailable;
  }
  waiting.value = false;
}

function aroundMe() {
  return run(async () => {
    const pos = await props.locate();
    return {lat: pos.lat, lon: pos.lon, label: 'votre position'};
  });
}

function aroundAddress() {
  return run(() => props.geocoder(address.value));
}
</script>

<template>
  <div class="nearby-bar">
    <button type="button" class="nearby-locate" :disabled="waiting" @click="aroundMe">
      {{ waiting ? 'Localisation…' : 'Autour de moi (5 km)' }}
    </button>
    <form class="nearby-address" @submit.prevent="aroundAddress">
      <label>
        <span>ou une adresse, un code postal</span>
        <input v-model="address" placeholder="12 rue de la République, 69002…" autocomplete="off" :disabled="waiting" />
      </label>
      <button type="submit" :disabled="waiting">Chercher</button>
    </form>
    <p v-if="message" role="alert" class="nearby-error">{{ message }}</p>
  </div>
</template>

<style scoped>
.nearby-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 14px 22px;
  margin-top: 18px;
}
.nearby-locate {
  background: white;
  color: #07598f;
  font-weight: 700;
  border-radius: 8px;
  padding: 13px 20px;
}
.nearby-locate:hover {
  background: #e5f2fa;
}
.nearby-address {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}
.nearby-address label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #e4f3fc;
}
.nearby-address input {
  min-width: 270px;
  padding: 11px 12px;
  border: 0;
  border-radius: 8px;
  color: var(--navy);
}
.nearby-address button {
  background: #07598f;
  color: white;
  font-weight: 700;
  border-radius: 8px;
  padding: 12px 18px;
}
.nearby-address button:hover {
  background: #03466f;
}
.nearby-error {
  flex-basis: 100%;
  margin: 0;
  padding: 10px 14px;
  border-radius: 8px;
  background: #fff1ec;
  color: #922c13;
  font-size: 13px;
}
@media (max-width: 650px) {
  .nearby-address {
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }
  .nearby-address input {
    min-width: 0;
    width: 100%;
  }
  .nearby-locate {
    width: 100%;
  }
}
</style>
