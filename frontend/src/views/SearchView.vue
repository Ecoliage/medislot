<script setup lang="ts">
import ProfessionalCard from '../components/ProfessionalCard.vue';
import NearbyBar from '../components/NearbyBar.vue';
import NearbyMap from '../components/NearbyMap.vue';
import type {PractitionerSearch} from '../composables/usePractitionerSearch';
import {specialties, type Professional, type Slot} from '../types';

defineProps<{search: PractitionerSearch; moveId: number | null}>();
const emit = defineEmits<{
  choose: [professional: Professional, slot: Slot];
  profile: [professional: Professional];
  cancelMove: [];
}>();

function choose(professional: Professional, slot: Slot) {
  emit('choose', professional, slot);
}
</script>

<template>
  <section class="hero">
    <div class="hero-inner">
      <p class="eyebrow">VOTRE PROCHAIN RENDEZ-VOUS</p>
      <h1>Trouvez le bon praticien.<br />Prenez un moment pour vous.</h1>
      <p class="hero-copy">Un professionnel près de chez vous, au créneau qui vous convient.</p>
      <form class="searchbar" @submit.prevent="search.search(true)">
        <label
          ><span>Nom du praticien</span><input v-model="search.q" placeholder="Nom, praticien…" autocomplete="off" /></label
        ><label
          ><span>Spécialité</span
          ><select v-model="search.specialty">
            <option value="">Toutes les spécialités</option>
            <option v-for="(label, key) in specialties" :key="key" :value="key">{{ label }}</option>
          </select></label
        ><label
          ><span>Ville</span><input v-model="search.city" placeholder="Paris, Lyon…" autocomplete="address-level2" /></label
        ><button type="submit" :disabled="search.loading">
          {{ search.loading ? 'Recherche…' : 'Rechercher' }} <span aria-hidden="true">→</span>
        </button>
      </form>
      <NearbyBar @position="search.aroundPlace" />
      <div class="hero-bottom">
        <span>Des créneaux visibles en quelques clics</span
        ><span class="live" :class="{offline: !search.connected}"
          ><i></i>{{ search.connected ? 'Disponibilités en direct' : 'Reconnexion du direct…' }}</span
        >
      </div>
    </div>
  </section>
  <main id="main" class="main">
    <slot name="feedback" />
    <p v-if="search.error" role="alert" class="alert error">
      {{ search.error }} <button class="text-button" @click="search.search()">Réessayer</button>
    </p>
    <div class="section-title">
      <div>
        <p class="eyebrow dark">VOTRE RECHERCHE</p>
        <h2 v-if="search.place">
          {{ search.total }} praticien{{ search.total > 1 ? 's' : '' }} à moins de {{ search.radiusKm }} km de {{ search.place.label }}
        </h2>
        <h2 v-else>{{ search.total }} praticien{{ search.total > 1 ? 's' : '' }} à découvrir</h2>
      </div>
      <button v-if="search.place" class="text-button" @click="search.leaveNearby">Quitter la recherche autour de moi</button
      ><span v-else class="subtle">Rendez-vous au cabinet</span>
    </div>
    <div v-if="moveId" class="move-banner">
      Déplacement du rendez-vous n° {{ moveId }}
      <button class="text-button" @click="emit('cancelMove')">Abandonner le déplacement</button>
    </div>
    <div v-if="search.loading" class="loading" role="status">Nous recherchons vos prochains créneaux…</div>
    <div v-else-if="!search.doctors.length && search.place" class="empty">
      <h3>Aucun praticien à moins de {{ search.radiusKm }} km</h3>
      <p>Essayez une autre spécialité ou une autre adresse.</p>
      <button class="primary" @click="search.leaveNearby">Quitter la recherche autour de moi</button>
    </div>
    <div v-else-if="!search.doctors.length" class="empty">
      <h3>Aucun praticien ne correspond à cette recherche</h3>
      <p>Essayez une autre ville ou une spécialité différente.</p>
      <button class="primary" @click="search.clearFilters">Voir tous les praticiens</button>
    </div>
    <div v-else class="results">
      <div class="cards">
        <ProfessionalCard
          v-for="p in search.doctors"
          :key="p.id"
          :professional="p"
          :slots="search.slots[p.id] || []"
          @choose="choose"
          @profile="emit('profile', $event)"
        />
      </div>
      <aside class="help">
        <span class="help-icon" aria-hidden="true">+</span>
        <h2>Votre rendez-vous,<br />en toute simplicité.</h2>
        <ol>
          <li>Choisissez votre praticien.</li>
          <li>Sélectionnez un créneau.</li>
          <li>Retrouvez la confirmation dans votre espace.</li>
        </ol>
        <hr />
        <p>Un changement de programme ? Vous pouvez déplacer ou annuler votre rendez-vous depuis votre espace.</p>
        <p class="demo-note">Démonstration pédagogique.<br />Praticiens et données fictifs.</p>
      </aside>
    </div>
    <NearbyMap
      v-if="search.place && !search.loading && search.doctors.length"
      :center="search.place"
      :radius-km="search.radiusKm"
      :professionals="search.doctors"
    />
    <div v-if="search.total > search.pageSize" class="pagination">
      <button
        :disabled="search.page === 0"
        @click="
          search.page--;
          search.search();
        "
      >
        Précédent</button
      ><span>Page {{ search.page + 1 }}</span
      ><button
        :disabled="(search.page + 1) * search.pageSize >= search.total"
        @click="
          search.page++;
          search.search();
        "
      >
        Suivant
      </button>
    </div>
  </main>
</template>

<style scoped>
.hero {
  background: #0873b9;
  color: white;
}
.hero-inner {
  max-width: 1280px;
  margin: auto;
  padding: 48px 0 26px;
}
.hero h1 {
  font-size: 45px;
  max-width: 840px;
  margin-bottom: 15px;
  color: white;
}
.hero-copy {
  color: #e3f2fd;
  font-size: 17px;
  margin-bottom: 30px;
}
.hero-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  margin-top: 21px;
  color: #e4f3fc;
}
.live {
  display: flex;
  align-items: center;
  gap: 8px;
}
.live i {
  width: 7px;
  height: 7px;
  background: #94eadb;
  border-radius: 50%;
}
.live.offline i {
  background: #f2c184;
}
.results {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 264px;
  gap: 28px;
  align-items: start;
}
.cards {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.help {
  background: #e7f3f7;
  border-radius: 13px;
  padding: 26px;
}
.help-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  color: #15769a;
  border: 1px solid #b0d8e5;
  border-radius: 10px;
  font-size: 26px;
  margin-bottom: 21px;
}
.help h2 {
  font-size: 20px;
  line-height: 1.45;
}
.help ol {
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.7;
}
.help li {
  margin-bottom: 12px;
}
.help p {
  font-size: 13px;
  color: #4c6979;
}
.help hr {
  border: 0;
  border-top: 1px solid #c7e0e9;
  margin: 23px 0;
}
.demo-note {
  margin-top: 24px;
  font-size: 11px !important;
}
.move-banner {
  padding: 15px;
  background: #eaf4fc;
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
}
@media (min-width: 1500px) {
  .hero-inner {
    padding-top: 58px;
    padding-bottom: 29px;
  }
  .hero h1 {
    font-size: 49px;
  }
  .hero-copy {
    margin-bottom: 34px;
  }
}
@media (max-width: 1350px) {
  .hero-inner {
    margin-left: 32px;
    margin-right: 32px;
  }
  .results {
    grid-template-columns: minmax(0, 1fr) 225px;
  }
  .help {
    padding: 22px;
  }
}
@media (max-width: 1050px) {
  .help {
    display: none;
  }
  .results {
    display: block;
  }
  .hero h1 {
    font-size: 38px;
  }
}
@media (max-width: 650px) {
  .hero-inner {
    margin: 0 18px;
  }
  .hero-inner {
    padding: 32px 0 23px;
  }
  .hero h1 {
    font-size: 32px;
    letter-spacing: -1px;
  }
  .hero-copy {
    font-size: 14px;
  }
  .hero-bottom {
    gap: 15px;
    flex-direction: column;
    align-items: flex-start;
  }
  .move-banner {
    flex-direction: column;
  }
  .hero .eyebrow {
    font-size: 10px;
  }
}
</style>
