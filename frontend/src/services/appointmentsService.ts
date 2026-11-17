import {api} from '../api';
import type {Appointment, AppointmentHistoryEntry} from '../types';

export function loadAppointments(): Promise<Appointment[]> {
  return api<Appointment[]>('/appointments');
}

export function saveAppointment(slotId: number, appointmentId: number | null): Promise<unknown> {
  return api(appointmentId === null ? '/appointments' : `/appointments/${appointmentId}`, {
    method: appointmentId === null ? 'POST' : 'PATCH',
    body: JSON.stringify({slotId}),
  });
}

export function cancelAppointment(id: number): Promise<unknown> {
  return api(`/appointments/${id}/cancel`, {method: 'POST'});
}

export function loadAppointmentHistory(id: number): Promise<AppointmentHistoryEntry[]> {
  return api<AppointmentHistoryEntry[]>(`/appointments/${id}/history`);
}
