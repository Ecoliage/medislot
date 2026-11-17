import {reactive} from 'vue';
import {ApiError} from '../api';
import {errorMessage} from '../errors';
import {cancelAppointment, loadAppointmentHistory, loadAppointments, saveAppointment} from '../services/appointmentsService';
import {useSession} from '../stores/session';
import type {Appointment, AppointmentHistoryEntry, BookingChoice, Professional, Slot} from '../types';

interface AppointmentActions {
  refreshSlots: () => Promise<void>;
  refreshNotices: () => Promise<void>;
  onConfirmed: () => void;
  onMove: (appointment: Appointment) => Promise<void>;
}

export function useAppointments(actions: AppointmentActions) {
  const session = useSession();
  let generation = 0;
  const state = reactive({
    appointments: [] as Appointment[],
    chosen: null as BookingChoice | null,
    moveId: null as number | null,
    history: null as AppointmentHistoryEntry[] | null,
    cancelId: null as number | null,
    busy: false,
    error: '',
    success: '',
  });

  async function refresh() {
    if (!session.user) return;
    const current = ++generation;
    const appointments = await loadAppointments();
    if (current === generation && session.user) state.appointments = appointments;
  }

  async function reload() {
    state.error = '';
    try {
      await refresh();
    } catch (error) {
      state.error = errorMessage(error);
    }
  }

  function choose(professional: Professional, slot: Slot) {
    state.error = '';
    state.success = '';
    state.chosen = {professional, slot};
  }

  async function refreshAfterChange() {
    await Promise.all([refresh(), actions.refreshNotices(), actions.refreshSlots()]);
  }

  async function confirm() {
    if (!state.chosen || state.busy) return;
    state.busy = true;
    state.error = '';
    try {
      await saveAppointment(state.chosen.slot.id, state.moveId);
      state.success = state.moveId === null ? 'Votre rendez-vous est confirmé.' : 'Votre rendez-vous a été déplacé.';
      state.chosen = null;
      state.moveId = null;
      await refreshAfterChange();
      actions.onConfirmed();
    } catch (error) {
      state.error = errorMessage(error);
      if (error instanceof ApiError && error.status === 409) {
        state.chosen = null;
        await actions.refreshSlots().catch(() => {});
      }
    } finally {
      state.busy = false;
    }
  }

  async function cancel() {
    if (state.cancelId === null || state.busy) return;
    state.busy = true;
    state.error = '';
    try {
      await cancelAppointment(state.cancelId);
      state.cancelId = null;
      state.success = 'Votre rendez-vous a été annulé.';
      await refreshAfterChange();
    } catch (error) {
      state.error = errorMessage(error);
    } finally {
      state.busy = false;
    }
  }

  async function move(appointment: Appointment) {
    state.moveId = appointment.id;
    await actions.onMove(appointment);
    state.success = 'Choisissez un nouveau créneau. Votre réservation actuelle reste conservée jusqu’à confirmation.';
  }

  function cancelMove() {
    state.moveId = null;
    state.success = '';
  }

  async function showHistory(id: number) {
    try {
      state.history = await loadAppointmentHistory(id);
    } catch (error) {
      state.error = errorMessage(error);
    }
  }

  function closeDialogs() {
    if (state.busy) return;
    state.chosen = null;
    state.history = null;
    state.cancelId = null;
  }

  function reset() {
    generation++;
    state.appointments = [];
    state.chosen = null;
    state.history = null;
    state.cancelId = null;
    state.moveId = null;
    state.error = '';
    state.success = '';
  }


  return Object.assign(state, {refresh, reload, choose, confirm, cancel, move, cancelMove, showHistory, closeDialogs, reset});
}
