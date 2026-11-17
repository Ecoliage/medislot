<script setup lang="ts">
import type {AppView, Identity} from '../../types';
import ClinicSwitcher from './ClinicSwitcher.vue';

defineProps<{view: AppView; user: Identity | null; noticeCount: number; showNotices: boolean}>();
const emit = defineEmits<{
  navigate: [view: AppView];
  home: [];
  appointments: [];
  toggleNotices: [];
  login: [];
  logout: [];
}>();
</script>

<template>
  <header class="header">
    <button class="brand" @click="emit('home')">MediSlot<span class="brand-dot">.</span></button>
    <nav aria-label="Navigation principale">
      <button :class="{active: view === 'search'}" @click="emit('navigate', 'search')">Trouver un praticien</button
      ><button :class="{active: view === 'appointments'}" @click="emit('appointments')">Mes rendez-vous</button
      ><button :class="{active: view === 'recherche'}" @click="emit('navigate', 'recherche')">Recherche avancée</button
      ><button :class="{active: view === 'espace'}" @click="emit('navigate', 'espace')">Espace patient</button
      ><button :class="{active: view === 'admin'}" @click="emit('navigate', 'admin')">Admin</button
      ><button v-if="user" @click="emit('toggleNotices')" :aria-expanded="showNotices">
        Notifications <span class="count">{{ noticeCount }}</span></button
      ><button v-if="!user" class="login" @click="emit('login')">Se connecter</button
      ><button v-else class="login" @click="emit('logout')">{{ user.name.split(' ')[0] }} · Déconnexion</button>
    </nav>
  </header>
  <div class="clinic-bar">
    <ClinicSwitcher />
  </div>
</template>

<style scoped>
.header {
  height: 90px;
  padding: 0 max(32px, calc((100vw - 1280px) / 2));
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: white;
  gap: 28px;
}
.clinic-bar {
  padding: 8px max(32px, calc((100vw - 1280px) / 2));
  background: white;
  border-top: 1px solid var(--line);
}
.brand-dot {
  color: #34b8b1;
}
.header nav {
  display: flex;
  gap: 24px;
  align-items: center;
}
.header nav button {
  background: none;
  color: #26465c;
  font-weight: 600;
  padding: 12px 0;
  white-space: nowrap;
}
.header nav button.active {
  color: var(--blue);
}
.header nav .login {
  padding: 12px 20px;
  border: 1px solid #bfd5e4;
  border-radius: 8px;
  color: var(--blue);
}
.count {
  font-size: 12px;
  background: #eaf4fa;
  border-radius: 20px;
  padding: 2px 6px;
}
@media (max-width: 1350px) {
  .header {
    padding: 0 32px;
  }
}
@media (max-width: 1050px) {
  .header nav {
    gap: 15px;
  }
  .header nav button {
    font-size: 12px;
  }
}
@media (max-width: 650px) {
  .clinic-bar {
    padding: 8px 18px;
  }
  .header {
    height: auto;
    min-height: 80px;
    padding: 18px;
    flex-wrap: wrap;
    gap: 12px;
  }
  .header nav {
    gap: 15px;
    flex-wrap: wrap;
  }
  .header nav .login {
    padding: 6px 10px;
  }
}
</style>
