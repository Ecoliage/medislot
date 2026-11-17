import {onBeforeUnmount, onMounted, reactive} from 'vue';
import {apiUrl} from '../api';
import {errorMessage} from '../errors';
import type {Place} from '../geo';
import {loadProfessionalSlots, searchProfessionals} from '../services/practitionersService';
import type {Professional, Slot} from '../types';
import {useLive} from './useLive';

export function usePractitionerSearch() {
  const live = useLive();
  let generation = 0;
  let slotGeneration = 0;
  const state = reactive({
    q: '',
    city: '',
    specialty: '',
    page: 0,
    pageSize: 6,
    radiusKm: 5,
    total: 0,
    doctors: [] as Professional[],
    place: null as Place | null,
    slots: {} as Record<number, Slot[]>,
    loading: false,
    error: '',
    connected: live.connected,
  });

  async function loadSlots() {
    const currentSearch = generation;
    const currentSlots = ++slotGeneration;
    try {
      const slots = await loadProfessionalSlots(state.doctors);
      if (currentSearch === generation && currentSlots === slotGeneration) state.slots = slots;
    } catch (error) {
      if (currentSearch === generation && currentSlots === slotGeneration) throw error;
    }
  }

  async function search(reset = false) {
    if (reset) state.page = 0;
    const current = ++generation;
    state.loading = true;
    state.error = '';
    try {
      const result = await searchProfessionals({...state, size: state.pageSize});
      if (current !== generation) return;
      state.doctors = result.items;
      state.total = result.total;
      state.slots = {};
      await loadSlots();
    } catch (error) {
      if (current === generation) state.error = errorMessage(error);
    } finally {
      if (current === generation) state.loading = false;
    }
  }

  function aroundPlace(place: Place) {
    state.place = place;
    return search(true);
  }

  function leaveNearby() {
    state.place = null;
    return search(true);
  }

  function clearFilters() {
    state.q = '';
    state.city = '';
    state.specialty = '';
    state.place = null;
    return search(true);
  }

  function forProfessional(name: string) {
    state.q = name;
    state.city = '';
    state.specialty = '';
    state.place = null;
    return search(true);
  }

  function connectLive() {
    live.connect(apiUrl('/events'), () => loadSlots().catch(() => {}));
  }

  onMounted(connectLive);
  onBeforeUnmount(() => {
    generation++;
    slotGeneration++;
  });

  return Object.assign(state, {search, loadSlots, aroundPlace, leaveNearby, clearFilters, forProfessional});
}

export type PractitionerSearch = ReturnType<typeof usePractitionerSearch>;
