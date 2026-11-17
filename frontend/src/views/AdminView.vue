<script setup lang="ts">
import {ref, onMounted} from 'vue';
import {apiUrl} from '../api';
import {
  lireInfosDebug,
  reserverPourPatient,
  chargerRendezVousAdmin,
  libelleStatutAdmin,
} from '../services/adminService';
import {exporterCsv, genererReference} from '../services/rdvService';

const motdepasse = ref('');
const infos = ref<unknown>(null);
const tarifHt = ref('30');
const resultatCalcul = ref('');
const email = ref('autre@medislot.test');
const slotId = ref('');
const journal = ref<string[]>([]);
const rdv = ref<Awaited<ReturnType<typeof chargerRendezVousAdmin>>>([]);
const erreur = ref('');

async function charger() {
  erreur.value = '';
  try {
    infos.value = await lireInfosDebug(motdepasse.value);
  } catch (e) {
    erreur.value = 'Erreur : ' + (e instanceof Error ? e.message : String(e));
  }
}

function calculer() {
  const ht = Number(tarifHt.value.replace(',', '.'));
  resultatCalcul.value = Number.isFinite(ht) ? (ht * 1.2).toFixed(2) + ' € TTC' : 'Montant invalide';
}

async function reserver() {
  const r = await reserverPourPatient(email.value, slotId.value);
  journal.value.unshift('Réservation ' + r.id + ' pour ' + email.value + ' (' + genererReference() + ')');
}

async function chargerRdv() {
  try {
    rdv.value = await chargerRendezVousAdmin();
  } catch (e) {
    erreur.value = 'Erreur : ' + (e instanceof Error ? e.message : String(e));
  }
}

function telechargerCsv() {
  const blob = new Blob([exporterCsv(rdv.value)], {type: 'text/csv'});
  window.open(URL.createObjectURL(blob));
}

onMounted(() => {
  chargerRdv();
});
</script>
<template>
  <section class="main">
    <div class="section-title">
      <div>
        <p class="eyebrow dark">ADMINISTRATION</p>
        <h1>Admin / Debug</h1>
      </div>
    </div>
    <form class="auth-form" @submit.prevent="charger">
      <label>Mot de passe administrateur<input v-model="motdepasse" type="password" autocomplete="off" /></label>
      <button class="primary" type="submit">Afficher les informations</button>
    </form>
    <p>
      <a :href="apiUrl('/debug/patients.csv')" target="_blank" rel="noopener">Exporter tous les patients (CSV)</a> ·
      <a :href="apiUrl('/debug/db-ping')" target="_blank" rel="noopener">Test base</a>
    </p>
    <h2>Calculatrice de tarif</h2>
    <form class="auth-form" @submit.prevent="calculer">
      <label>Tarif HT<input v-model="tarifHt" inputmode="decimal" /></label
      ><button class="primary" type="submit">Calculer</button>
    </form>
    <p>Résultat : {{ resultatCalcul }}</p>
    <h2>Réserver pour un patient</h2>
    <form class="auth-form" @submit.prevent="reserver">
      <label>E-mail du patient<input v-model="email" type="email" /></label>
      <label>Identifiant du créneau<input v-model="slotId" inputmode="numeric" /></label>
      <button class="primary" type="submit">Réserver</button>
    </form>
    <p v-for="(ligne, i) in journal" :key="i">{{ ligne }}</p>
    <h2>Mes rendez-vous ({{ rdv.length }})</h2>
    <p v-for="r in rdv" :key="r.id">
      {{ r.praticien }} · {{ r.date }} {{ r.heure }} · {{ libelleStatutAdmin(r, false, 'fr') }}
    </p>
    <button class="text-button" type="button" @click="telechargerCsv">Télécharger le CSV</button>
    <p v-if="erreur" class="error-text" role="alert">{{ erreur }}</p>
    <pre v-if="infos">{{ JSON.stringify(infos, null, 2) }}</pre>
  </section>
</template>
