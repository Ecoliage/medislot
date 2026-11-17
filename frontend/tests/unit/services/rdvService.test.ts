import {describe, it, expect} from 'vitest';
import {exporterCsv, ligneCsv, memeCreneau} from '../../../src/services/rdvService';
import type {Slot} from '../../../src/types';
const creneau = (id: number, startsAt: string): Slot => ({id, professionalId: 1, startsAt, endsAt: startsAt});
describe('memeCreneau', () => {
  it('reconnaît deux créneaux identiques', () => {
    expect(memeCreneau(creneau(1, '2026-11-02T09:00:00Z'), creneau(1, '2026-11-02T09:00:00Z'))).toBe(true);
  });
  it('distingue deux créneaux différents', () => {
    expect(memeCreneau(creneau(1, '2026-11-02T09:00:00Z'), creneau(2, '2026-11-03T09:00:00Z'))).toBe(false);
  });
});
describe('export CSV des rendez-vous', () => {
  it('laisse intactes les valeurs sans caractère spécial', () => {
    expect(ligneCsv(12, 'Dr Martin', 'cardiology', 'Lyon', '02/11/2026', '09:00', 'Confirmé', ';')).toBe(
      '12;Dr Martin;Cardiologie;Lyon;02/11/2026;09:00;Confirmé\n',
    );
  });
  it('met entre guillemets les valeurs avec le séparateur, " ou un saut de ligne', () => {
    expect(ligneCsv(12, 'Dr "Jo"; Martin', 'x', 'Lyon\nParis', '02/11/2026', '09:00', 'Confirmé', ';')).toBe(
      '12;"Dr ""Jo""; Martin";x;"Lyon\nParis";02/11/2026;09:00;Confirmé\n',
    );
  });
  it('tient compte du séparateur demandé', () => {
    expect(ligneCsv(12, 'Martin, Jo', 'x', 'Lyon;Paris', '02/11/2026', '09:00', 'Confirmé', ',')).toBe(
      '12,"Martin, Jo",x,Lyon;Paris,02/11/2026,09:00,Confirmé\n',
    );
  });
  it('neutralise les formules', () => {
    const csv = exporterCsv([
      {
        id: 3,
        praticien: '=HYPERLINK("http://x";"clic")',
        specialite: '+1',
        ville: '@SUM(A1)',
        date: '-2',
        heure: '09:00',
        status: 'CONFIRMED',
      },
    ]);
    expect(csv).toBe(
      'id;praticien;specialite;ville;date;heure;statut\n' +
        '3;"\'=HYPERLINK(""http://x"";""clic"")";\'+1;\'@SUM(A1);\'-2;09:00;Confirmé\n',
    );
  });
});
