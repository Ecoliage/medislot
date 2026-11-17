import {beforeEach, describe, expect, it, vi} from 'vitest';
import {createApp, nextTick} from 'vue';
import ClinicLogsPanel from '../../../../src/components/admin/ClinicLogsPanel.vue';
import type {PlatformClinic} from '../../../../src/types';

const mocks = vi.hoisted(() => ({loadClinicLogs: vi.fn()}));
vi.mock('../../../../src/services/adminService', () => mocks);

const clinic: PlatformClinic = {
  slug: 'cabinet-b', name: 'Cabinet B', city: 'Lyon', color: '#0b6bcb', practitioners: 7, appointments30d: 12, createdAt: '2026-01-01T00:00:00Z',
};

const settle = async () => {
  for (let i = 0; i < 4; i++) await Promise.resolve();
  await nextTick();
};

function mountPanel() {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const onBack = vi.fn();
  createApp(ClinicLogsPanel, {clinic, onBack}).mount(host);
  return {host, onBack};
}

describe('ClinicLogsPanel', () => {
  beforeEach(() => {
    mocks.loadClinicLogs.mockReset();
    document.body.innerHTML = '';
  });

  it('liste les événements du cabinet demandé', async () => {
    mocks.loadClinicLogs.mockResolvedValue([
      {id: 9, action: 'CANCELLED', label: 'Rendez-vous annulé', appointmentId: 4, practitioner: 'Dr Julie Rochas', createdAt: '2026-11-17T09:30:00Z'},
      {id: 8, action: 'CREATED', label: 'Réservation confirmée', appointmentId: 4, practitioner: 'Dr Julie Rochas', createdAt: '2026-11-17T09:00:00Z'},
    ]);
    const {host} = mountPanel();
    await settle();
    expect(mocks.loadClinicLogs).toHaveBeenCalledWith('cabinet-b');
    const rows = [...host.querySelectorAll('tbody tr')].map(row => [...row.querySelectorAll('td')].slice(1).map(cell => cell.textContent));
    expect(rows).toEqual([
      ['Rendez-vous annulé', 'n° 4', 'Dr Julie Rochas'],
      ['Réservation confirmée', 'n° 4', 'Dr Julie Rochas'],
    ]);
    expect(host.querySelector('tbody td')!.textContent).toContain('17/11/2026');
  });

  it('signale un journal vide', async () => {
    mocks.loadClinicLogs.mockResolvedValue([]);
    const {host} = mountPanel();
    await settle();
    expect(host.querySelector('.empty')!.textContent).toBe('Aucun événement pour ce cabinet.');
  });

  it('affiche l’erreur quand le cabinet est inaccessible', async () => {
    mocks.loadClinicLogs.mockRejectedValue(new Error('Cabinet introuvable.'));
    const {host} = mountPanel();
    await settle();
    expect(host.querySelector('[role="alert"]')!.textContent).toBe('Cabinet introuvable.');
    expect(host.querySelector('table')).toBeNull();
  });
});
