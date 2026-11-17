import {onScopeDispose, reactive, watch} from 'vue';
import {apiUrl} from '../api';
import {loadNotifications} from '../services/notificationsService';
import {useClinic} from '../stores/clinic';
import {useSession} from '../stores/session';
import type {Notice} from '../types';
import {onClinicChange} from '../tenantCache';
import {useLive} from './useLive';

export function useNotifications(onChange: () => Promise<void>) {
  const session = useSession();
  const clinic = useClinic();
  const live = useLive();
  let generation = 0;
  const state = reactive({notices: [] as Notice[], show: false});

  async function refresh() {
    if (!session.user) return;
    const current = ++generation;
    const notices = await loadNotifications();
    if (current === generation && session.user) state.notices = notices;
  }

  function clear() {
    generation++;
    state.notices = [];
  }

  onScopeDispose(onClinicChange(clear));

  watch([() => session.user, () => clinic.slug], ([user]) => {
    clear();
    live.close();
    if (!user) {
      state.show = false;
      return;
    }
    refresh().catch(() => {});
    live.connect(apiUrl('/notifications/events'), () => {
      refresh().catch(() => {});
      onChange().catch(() => {});
    }, 'notifications-changed');
  });

  return Object.assign(state, {refresh});
}
