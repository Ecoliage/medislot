import type {PlatformClinic} from './types';

const HEADER = ['Cabinet', 'Identifiant', 'Ville', 'Couleur', 'Praticiens', 'RDV (30 j)', 'Créé le'];

function cell(value: string | number) {
  return String(value);
}

export function clinicsToCsv(clinics: PlatformClinic[]) {
  const rows = clinics.map(c => [
    c.name,
    c.slug,
    c.city,
    c.color,
    c.practitioners,
    c.appointments30d,
    new Date(c.createdAt).toLocaleDateString('fr-FR', {timeZone: 'Europe/Paris'}),
  ]);
  return '﻿' + [HEADER, ...rows].map(row => row.map(cell).join(';')).join('\r\n') + '\r\n';
}

export function downloadCsv(content: string, fileName: string) {
  const url = URL.createObjectURL(new Blob([content], {type: 'text/csv;charset=utf-8'}));
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}
