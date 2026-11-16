<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref, watch} from 'vue';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import {formatDistance, type Coordinates} from '../geo';
import type {Professional} from '../types';

const props = defineProps<{
  center: Coordinates;
  radiusKm: number;
  professionals: Professional[];
}>();

const container = ref<HTMLElement | null>(null);
let map: L.Map | null = null;
let layer: L.LayerGroup | null = null;

function draw() {
  if (!map || !layer) return;
  layer.clearLayers();
  const center = L.latLng(props.center.lat, props.center.lon);
  const radius = props.radiusKm * 1000;
  map.fitBounds(center.toBounds(radius * 2));
  L.circle(center, {radius, color: '#0873b9', weight: 2, fillOpacity: 0.06}).addTo(layer);
  L.circleMarker(center, {radius: 6, color: '#12334b', fillColor: '#12334b', fillOpacity: 1}).addTo(layer);
  for (const p of props.professionals) {
    if (p.latitude == null || p.longitude == null) continue;
    const popup = document.createElement('div');
    const name = document.createElement('strong');
    name.textContent = p.name;
    popup.append(name);
    if (p.distanceKm != null) popup.append(document.createElement('br'), formatDistance(p.distanceKm));
    L.circleMarker([p.latitude, p.longitude], {radius: 8, color: '#07598f', fillColor: '#34b8b1', fillOpacity: 0.9})
      .bindPopup(popup)
      .addTo(layer);
  }
}

onMounted(() => {
  map = L.map(container.value!);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 19,
  }).addTo(map);
  layer = L.layerGroup().addTo(map);
  draw();
});

watch(() => [props.center, props.radiusKm, props.professionals], draw);

onBeforeUnmount(() => {
  map?.remove();
  map = null;
  layer = null;
});
</script>

<template>
  <div
    ref="container"
    class="nearby-map"
    role="region"
    aria-label="Carte des praticiens autour de votre position"
  ></div>
</template>
