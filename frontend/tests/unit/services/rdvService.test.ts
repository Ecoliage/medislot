import {describe, it, expect} from 'vitest';
import {memeCreneau} from '../../../src/services/rdvService';
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
