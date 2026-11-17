import {api, CLINIC_HEADER} from '../api';
import {specialties, dateLabel, timeLabel} from '../types';
import type {
  Appointment,
  CalendarEntry,
  ClinicDetails,
  ClinicLog,
  ClinicPractitioner,
  ClinicStats,
  PlatformClinic,
  PlatformEvent,
} from '../types';

export async function chargerRendezVousAdmin(): Promise<any[]> {
  console.log('Chargement des rendez-vous', new Date());
  const liste: any = await api<Appointment[]>('/appointments');
  const resultat: any[] = [];
  for (let i = 0; i < liste.length; i++) {
    const a: any = liste[i];
    const ligne: any = {};
    ligne.id = a.id;
    ligne.praticien = a.professionalName;
    ligne.specialite = specialties[a.specialty] || a.specialty;
    ligne.ville = a.city;
    ligne.date = dateLabel(a.startsAt);
    ligne.heure = timeLabel(a.startsAt);
    ligne.statut = a.status;
    if (ligne.statut == 'CONFIRMED') {
      ligne.couleur = 'vert';
    } else {
      ligne.couleur = 'rouge';
    }
    resultat.push(ligne);
  }
  return resultat;
}

export function libelleStatutAdmin(rdv: any, court: any, langue: any): string {
  let resultat = '';
  if (rdv) {
    if (rdv.status == 'CONFIRMED') {
      if (court) {
        resultat = 'OK';
      } else {
        if (langue == 'en') {
          resultat = 'Confirmed';
        } else if (langue == 'fr') {
          resultat = 'Confirmé';
        } else {
          resultat = 'Confirmé';
        }
      }
    } else if (rdv.status == 'CANCELLED') {
      if (court) {
        resultat = 'X';
      } else {
        if (langue == 'en') {
          resultat = 'Cancelled';
        } else {
          resultat = 'Annulé';
        }
      }
    } else {
      for (let i = 0; i < 3; i++) {
        if (i == 2 && !court) {
          resultat = 'Inconnu';
        }
      }
    }
  }
  return resultat;
}

export async function lireInfosDebug(motdepasse: string): Promise<unknown> {
  return api('/debug/info?' + new URLSearchParams({motdepasse}));
}

export async function reserverPourPatient(
  email: string,
  slotId: string,
): Promise<{id: number; patient: string; statut: string}> {
  return api('/debug/reserver?' + new URLSearchParams({email, slotId}), {method: 'POST'});
}

export async function loadClinics(): Promise<PlatformClinic[]> {
  return (await api<PlatformClinic[]>('/platform/clinics')) ?? [];
}

export async function loadPlatformEvents(): Promise<PlatformEvent[]> {
  return (await api<PlatformEvent[]>('/platform/events')) ?? [];
}

export async function loadClinicStats(slug: string): Promise<ClinicStats> {
  return api<ClinicStats>('/clinic/stats', {headers: {[CLINIC_HEADER]: slug}});
}

export async function loadClinicLogs(slug: string): Promise<ClinicLog[]> {
  return (await api<ClinicLog[]>('/clinic/logs', {headers: {[CLINIC_HEADER]: slug}})) ?? [];
}

export interface ClinicChanges {
  name: string;
  city: string;
  address: string | null;
  color: string;
}

export interface NewClinic extends ClinicChanges {
  slug: string;
  adminEmail: string;
}

const clinicPathOf = (slug: string) => '/clinics/' + encodeURIComponent(slug);

export async function loadClinic(slug: string): Promise<ClinicDetails> {
  return api<ClinicDetails>(clinicPathOf(slug));
}

export async function createClinic(clinic: NewClinic): Promise<ClinicDetails> {
  return api<ClinicDetails>('/clinics', {method: 'POST', body: JSON.stringify(clinic)});
}

export async function updateClinic(slug: string, changes: ClinicChanges): Promise<ClinicDetails> {
  return api<ClinicDetails>(clinicPathOf(slug), {method: 'PUT', body: JSON.stringify(changes)});
}

export async function uploadClinicLogo(slug: string, file: File): Promise<ClinicDetails> {
  const body = new FormData();
  body.append('file', file);
  return api<ClinicDetails>(clinicPathOf(slug) + '/logo', {method: 'PUT', body});
}

export function clinicLogoUrl(slug: string): string {
  return '/api' + clinicPathOf(slug) + '/logo';
}

export async function loadClinicPractitioners(slug: string): Promise<ClinicPractitioner[]> {
  return (await api<ClinicPractitioner[]>(clinicPathOf(slug) + '/professionals')) ?? [];
}

/** {@code week}: any day of the week, as YYYY-MM-DD; the server reads Monday to Sunday. */
export async function loadPractitionerWeek(slug: string, practitionerId: number, week: string): Promise<CalendarEntry[]> {
  const path = `${clinicPathOf(slug)}/professionals/${practitionerId}/appointments?` + new URLSearchParams({week});
  return (await api<CalendarEntry[]>(path)) ?? [];
}
