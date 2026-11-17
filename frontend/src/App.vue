<script setup lang="ts">
import {onMounted, ref} from 'vue';
import AppHeader from './components/layout/AppHeader.vue';
import AppFooter from './components/layout/AppFooter.vue';
import FeedbackMessages from './components/ui/FeedbackMessages.vue';
import NotificationsPanel from './components/notifications/NotificationsPanel.vue';
import AuthDialog from './components/auth/AuthDialog.vue';
import BookingDialog from './components/appointments/BookingDialog.vue';
import CancelAppointmentDialog from './components/appointments/CancelAppointmentDialog.vue';
import AppointmentHistoryDialog from './components/appointments/AppointmentHistoryDialog.vue';
import ProfessionalDialog from './components/practitioners/ProfessionalDialog.vue';
import SearchView from './views/SearchView.vue';
import AppointmentsView from './views/AppointmentsView.vue';
import AdvancedSearchView from './views/AdvancedSearchView.vue';
import PatientSpaceView from './views/PatientSpaceView.vue';
import AdminView from './views/AdminView.vue';
import {usePractitionerSearch} from './composables/usePractitionerSearch';
import {useAppointments} from './composables/useAppointments';
import {useNotifications} from './composables/useNotifications';
import {useSession} from './stores/session';
import {errorMessage} from './errors';
import type {AppView, Professional, Slot} from './types';

const session = useSession();
const view = ref<AppView>('search');
const ongletEspace = ref<string>();
const authOpen = ref(false);
const authError = ref('');
const profile = ref<Professional | null>(null);
const sessionError = ref('');
const search = usePractitionerSearch();
const booking = useAppointments({
  refreshSlots: search.loadSlots,
  refreshNotices: (): Promise<void> => notifications.refresh(),
  onConfirmed: () => { view.value = 'appointments'; },
  onMove: async appointment => {
    view.value = 'search';
    await search.forProfessional(appointment.professionalName);
  },
});
const notifications = useNotifications(booking.refresh);

function home() {
  view.value = 'search';
  booking.moveId = null;
}

function choose(professional: Professional, slot: Slot) {
  profile.value = null;
  booking.choose(professional, slot);
  if (!session.user) authOpen.value = true;
}

function closeAuth() {
  authOpen.value = false;
  authError.value = '';
  booking.closeDialogs();
}

function authenticated() {
  authOpen.value = false;
  authError.value = '';
  sessionError.value = '';
  booking.reload();
}

async function retryFeedback() {
  if (sessionError.value) {
    sessionError.value = '';
    try {
      await session.load();
    } catch (error) {
      sessionError.value = errorMessage(error);
    }
  }
  booking.error = '';
  if (view.value === 'appointments') await booking.reload();
  else await search.search();
}

function myAppointments() {
  if (!session.user) {
    authOpen.value = true;
    return;
  }
  view.value = 'appointments';
  booking.reload();
}

async function logout() {
  sessionError.value = '';
  try {
    await session.logout();
    booking.reset();
    profile.value = null;
    view.value = 'search';
  } catch (error) {
    sessionError.value = errorMessage(error);
  }
}

onMounted(async () => {
  const requested = new URLSearchParams(window.location.search).get('page');
  if (requested === 'compte' || requested === 'profil' || requested === 'messagerie') {
    ongletEspace.value = requested;
    view.value = 'espace';
  } else if (requested === 'admin') {
    view.value = 'admin';
  }
  await search.search();
  try {
    await session.load();
  } catch (error) {
    sessionError.value = errorMessage(error);
  }
  if (new URLSearchParams(window.location.search).get('error') === 'google') {
    authError.value = 'La connexion avec Google a échoué. Réessayez ou utilisez votre mot de passe.';
    authOpen.value = true;
    window.history.replaceState(null, '', window.location.pathname);
  }
});
</script>

<template>
  <a href="#main" class="skip">Aller au contenu</a>
  <AppHeader
    :view="view"
    :user="session.user"
    :notice-count="notifications.notices.length"
    :show-notices="notifications.show"
    @navigate="view = $event"
    @home="home"
    @appointments="myAppointments"
    @toggle-notices="notifications.show = !notifications.show"
    @login="authOpen = true"
    @logout="logout"
  />
  <SearchView
    v-if="view === 'search'"
    :search="search"
    :move-id="booking.moveId"
    @choose="choose"
    @profile="profile = $event"
    @cancel-move="booking.cancelMove"
  >
    <template #feedback>
      <NotificationsPanel v-if="notifications.show && session.user" :notices="notifications.notices" @close="notifications.show = false" />
      <FeedbackMessages :error="sessionError || booking.error" :success="booking.success" @retry="retryFeedback" />
    </template>
  </SearchView>
  <AppointmentsView
    v-else-if="view === 'appointments'"
    :appointments="booking.appointments"
    @move="booking.move"
    @cancel="booking.cancelId = $event; booking.error = ''"
    @history="booking.showHistory"
    @search="view = 'search'"
  >
    <template #feedback>
      <NotificationsPanel v-if="notifications.show && session.user" :notices="notifications.notices" @close="notifications.show = false" />
      <FeedbackMessages :error="sessionError || booking.error" :success="booking.success" @retry="retryFeedback" />
    </template>
  </AppointmentsView>
  <AdvancedSearchView v-else-if="view === 'recherche'" />
  <PatientSpaceView v-else-if="view === 'espace'" :onglet="ongletEspace" />
  <AdminView v-else-if="view === 'admin'" />
  <AppFooter />

  <AuthDialog v-if="authOpen" :initial-error="authError" @close="closeAuth" @authenticated="authenticated" />
  <BookingDialog
    v-else-if="booking.chosen"
    :choice="booking.chosen"
    :moving="booking.moveId !== null"
    :busy="booking.busy"
    :error="booking.error"
    @close="booking.closeDialogs"
    @confirm="booking.confirm"
  />
  <ProfessionalDialog v-else-if="profile" :professional="profile" @close="profile = null" />
  <AppointmentHistoryDialog v-else-if="booking.history" :history="booking.history" @close="booking.closeDialogs" />
  <CancelAppointmentDialog
    v-else-if="booking.cancelId !== null"
    :busy="booking.busy"
    :error="booking.error"
    @close="booking.closeDialogs"
    @confirm="booking.cancel"
  />
</template>

<style scoped>
.skip {
  position: fixed;
  top: -100px;
  left: 15px;
  background: white;
  padding: 15px;
  z-index: 100;
}
.skip:focus {
  top: 12px;
}
</style>
