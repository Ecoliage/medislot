<script setup lang="ts">
import {ref, computed, onMounted, onBeforeUnmount, watch} from 'vue';
import {specialties, type ProfessionalSummary} from '../types';
import {searchAdvancedProfessionals} from '../services/practitionersService';

const nom = ref('');
const ville = ref('');
const specialite = ref('general');
const resultats = ref<ProfessionalSummary[]>([]);
const message = ref('');
const enCours = ref(false);
const VILLE_VALIDE = /^[a-zA-Zéèàï\- ]+$/;
let generation = 0;

const titre = computed(() => 'Résultats pour ' + nom.value + (ville.value ? ' à ' + ville.value : ''));

async function rechercher() {
  const current = ++generation;
  enCours.value = true;
  if (ville.value !== '' && !VILLE_VALIDE.test(ville.value)) {
    message.value = 'Ville invalide : ' + ville.value;
    enCours.value = false;
    return;
  }
  try {
    const data = await searchAdvancedProfessionals({name: nom.value, city: ville.value, specialty: specialite.value});
    if (current !== generation) return;
    resultats.value = data;
    message.value = data.length + ' praticien(s) trouvé(s)';
  } catch (e) {
    if (current === generation) message.value = e instanceof Error ? e.message : String(e);
  } finally {
    if (current === generation) enCours.value = false;
  }
}

watch(nom, () => {
  rechercher();
});

onMounted(() => {
  rechercher();
});
onBeforeUnmount(() => { generation++; });
</script>
<template>
  <section class="main">
    <div class="section-title">
      <div>
        <p class="eyebrow dark">RECHERCHE AVANCÉE</p>
        <h1>Rechercher un praticien par nom</h1>
      </div>
    </div>
    <form class="searchbar" @submit.prevent="rechercher">
      <label><span>Nom</span><input v-model="nom" placeholder="Martin, Leroy…" /></label>
      <label
        ><span>Spécialité</span
        ><select v-model="specialite">
          <option v-for="(label, key) in specialties" :key="key" :value="key">{{ label }}</option>
        </select></label
      >
      <label><span>Ville</span><input v-model="ville" placeholder="Paris, Lyon…" /></label>
      <button type="submit" :disabled="enCours">Rechercher</button>
    </form>
    <h2>{{ titre }}</h2>
    <p class="subtle">{{ message }}</p>
    <article v-for="p in resultats" :key="p.id" class="appointment">
      <div class="appointment-person">
        <h2>{{ p.name }}</h2>
        <p>{{ specialties[p.specialty] }} · {{ p.city }}</p>
        <p>{{ p.address }} · {{ p.fee }} €</p>
      </div>
    </article>
  </section>
</template>
