import {defineStore} from 'pinia';
import {ref} from 'vue';
import {api, refreshCsrf, ApiError} from '../api';
import type {Identity} from '../types';
import {useClinic} from './clinic';
export const useSession = defineStore('session', () => {
  const user = ref<Identity | null>(null);
  const clinic = useClinic();
  async function load() {
    try {
      const identity = await api<Identity>('/me');
      await clinic.afterLogin();
      user.value = identity;
      localStorage.setItem('medislot_user', JSON.stringify(user.value));
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        user.value = null;
        clinic.forgetMemberships();
        localStorage.removeItem('medislot_user');
      } else throw e;
    }
  }
  async function login(email: string, password: string) {
    await refreshCsrf();
    await api('/login', {method: 'POST', body: new URLSearchParams({username: email, password})});
    await refreshCsrf();
    await load();
  }
  async function logout() {
    await api('/logout', {method: 'POST'});
    user.value = null;
    clinic.forgetMemberships();
    localStorage.removeItem('medislot_user');
    await refreshCsrf();
  }
  return {user, load, login, logout};
});
