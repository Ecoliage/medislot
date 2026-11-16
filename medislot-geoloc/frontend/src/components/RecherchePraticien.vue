<script setup lang="ts">
import {ref, computed, onMounted, watch} from 'vue';
import {specialties} from '../types';
import {api} from '../api';

interface Resultat {
  id: number;
  nom: string;
  specialite: string;
  ville: string;
  adresse: string;
  tarif: number;
}

const nom = ref('');
const ville = ref('');
const specialite = ref('general');
const resultats = ref<Resultat[]>([]);
const message = ref('');
const enCours = ref(false);
const VILLE_VALIDE = /^[a-zA-Zéèàï\- ]+$/;

const titre = computed(() => 'Résultats pour ' + nom.value + (ville.value ? ' à ' + ville.value : ''));

async function rechercher() {
  enCours.value = true;
  if (ville.value !== '' && !VILLE_VALIDE.test(ville.value)) {
    message.value = 'Ville invalide : ' + ville.value;
    enCours.value = false;
    return;
  }
  try {
    const params = new URLSearchParams({nom: nom.value, ville: ville.value, specialite: specialite.value});
    const data = await api<Resultat[]>('/praticiens/recherche?' + params);
    resultats.value = data;
    message.value = data.length + ' praticien(s) trouvé(s)';
  } catch (e) {
    message.value = e instanceof Error ? e.message : String(e);
  }
  enCours.value = false;
}

watch(nom, () => {
  rechercher();
});

onMounted(() => {
  rechercher();
});
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
        <h2>{{ p.nom }}</h2>
        <p>{{ specialties[p.specialite] }} · {{ p.ville }}</p>
        <p>{{ p.adresse }} · {{ p.tarif }} €</p>
      </div>
    </article>
  </section>
</template>
