import {defineStore} from 'pinia';
import {ref} from 'vue';
import {api, setApiClinic} from '../api';
import {clinicFromLocation, clinicFromPath, clinicPath} from '../tenant';
import type {Clinic} from '../types';

export const useClinic = defineStore('clinic', () => {
  const slug = ref(clinicFromLocation(window.location));
  const current = ref<Clinic | null>(null);
  setApiClinic(slug.value);

  function init() {
    if (clinicFromPath(window.location.pathname) !== slug.value) {
      window.history.replaceState(null, '', clinicPath(slug.value) + window.location.search);
    }
  }

  async function loadCurrent() {
    current.value = await api<Clinic>('/clinic');
  }

  return {slug, current, init, loadCurrent};
});
