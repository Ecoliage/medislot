import {defineStore} from 'pinia';
import {ref} from 'vue';
import {api, setApiClinic} from '../api';
import {clinicFromLocation, clinicFromPath, clinicPath, validSlug} from '../tenant';
import {clearTenantCaches} from '../tenantCache';
import type {Clinic} from '../types';

export const useClinic = defineStore('clinic', () => {
  const slug = ref(clinicFromLocation(window.location));
  const current = ref<Clinic | null>(null);
  const mine = ref<Clinic[]>([]);
  setApiClinic(slug.value);

  function init() {
    if (clinicFromPath(window.location.pathname) !== slug.value) {
      window.history.replaceState(null, '', clinicPath(slug.value) + window.location.search);
    }
  }

  async function loadCurrent() {
    const requested = slug.value;
    const clinic = await api<Clinic>('/clinic');
    if (requested === slug.value) current.value = clinic;
  }

  async function switchTo(target: string): Promise<boolean> {
    const next = validSlug(target);
    if (!next || next === slug.value) return false;
    const known = mine.value.find(clinic => clinic.slug === next);
    if (!known) return false;
    clearTenantCaches();
    slug.value = next;
    current.value = known;
    setApiClinic(next);
    window.history.pushState(null, '', clinicPath(next));
    return true;
  }

  async function afterLogin(): Promise<boolean> {
    mine.value = (await api<Clinic[]>('/me/clinics')) ?? [];
    if (mine.value.length === 0 || mine.value.some(clinic => clinic.slug === slug.value)) return false;
    return switchTo(mine.value[0].slug);
  }

  function forgetMemberships() {
    mine.value = [];
  }

  return {slug, current, mine, init, loadCurrent, switchTo, afterLogin, forgetMemberships};
});
