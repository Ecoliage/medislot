import {describe, expect, it} from 'vitest';
import {clinicsToCsv} from '../../src/clinicCsv';
import type {PlatformClinic} from '../../src/types';

const clinic: PlatformClinic = {
  slug: 'cabinet-a',
  name: 'Cabinet des Tilleuls',
  city: 'Paris',
  color: '#0873b9',
  practitioners: 4,
  appointments30d: 12,
  createdAt: '2026-03-01T10:00:00Z',
};

describe('export CSV des cabinets', () => {
  it('écrit un en-tête et une ligne par cabinet, séparés par des points-virgules', () => {
    const lines = clinicsToCsv([clinic]).split('\r\n');
    expect(lines[0]).toBe('﻿Cabinet;Identifiant;Ville;Couleur;Praticiens;RDV (30 j);Créé le');
    expect(lines[1]).toBe('Cabinet des Tilleuls;cabinet-a;Paris;#0873b9;4;12;01/03/2026');
  });

  it('met entre guillemets les valeurs avec ; ou " et neutralise les formules', () => {
    const line = clinicsToCsv([{...clinic, name: 'Le "Grand"; cabinet', city: '=HYPERLINK("x")'}]).split('\r\n')[1];
    expect(line).toBe('"Le ""Grand""; cabinet";cabinet-a;"\'=HYPERLINK(""x"")";#0873b9;4;12;01/03/2026');
  });
});
