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
