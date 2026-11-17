import {reactive, watch} from 'vue';
import {apiUrl} from '../api';
import {loadNotifications} from '../services/notificationsService';
import {useSession} from '../stores/session';
import type {Notice} from '../types';
import {useLive} from './useLive';

export function useNotifications(onChange: () => Promise<void>) {
  const session = useSession();
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

  watch(() => session.user, user => {
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
