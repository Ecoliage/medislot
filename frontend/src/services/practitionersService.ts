import {api} from '../api';
import {nearbyParams, type Place} from '../geo';
import type {Page, Professional, ProfessionalSummary, Slot} from '../types';

export interface PractitionerFilters {
  q: string;
  city: string;
  specialty: string;
  page: number;
  size: number;
  place: Place | null;
  radiusKm: number;
}

export function searchProfessionals(filters: PractitionerFilters): Promise<Page<Professional>> {
  const params = filters.place
    ? nearbyParams({
        ...filters.place,
        radiusKm: filters.radiusKm,
        specialty: filters.specialty,
        page: filters.page,
        size: filters.size,
      })
    : new URLSearchParams({
        q: filters.q,
        city: filters.city,
        specialty: filters.specialty,
        page: String(filters.page),
        size: String(filters.size),
      });
  return api<Page<Professional>>('/professionals?' + params);
}

export async function loadProfessionalSlots(professionals: Professional[]): Promise<Record<number, Slot[]>> {
  const entries = await Promise.all(
    professionals.map(async professional =>
      [professional.id, await api<Slot[]>(`/professionals/${professional.id}/slots`)] as const,
    ),
  );
  return Object.fromEntries(entries);
}

interface AdvancedSearchResult {
  id: number;
  nom: string;
  specialite: string;
  ville: string;
  adresse: string;
  tarif: number;
}

export async function searchAdvancedProfessionals(filters: {
  name: string;
  city: string;
  specialty: string;
}): Promise<ProfessionalSummary[]> {
  const params = new URLSearchParams({nom: filters.name, ville: filters.city, specialite: filters.specialty});
  const results = await api<AdvancedSearchResult[]>('/praticiens/recherche?' + params);
  return results.map(result => ({
    id: result.id,
    name: result.nom,
    specialty: result.specialite,
    city: result.ville,
    address: result.adresse,
    fee: result.tarif,
  }));
}
