import {beforeEach, describe, expect, it, vi} from 'vitest';
import {createApp, nextTick} from 'vue';
import ClinicStatsPanel from '../../../../src/components/admin/ClinicStatsPanel.vue';
import type {PlatformClinic} from '../../../../src/types';

const mocks = vi.hoisted(() => ({loadClinicStats: vi.fn()}));
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
  createApp(ClinicStatsPanel, {clinic, onBack}).mount(host);
  return {host, onBack};
}

describe('ClinicStatsPanel', () => {
  beforeEach(() => {
    mocks.loadClinicStats.mockReset();
    document.body.innerHTML = '';
  });

  it('affiche les compteurs du cabinet demandé', async () => {
    mocks.loadClinicStats.mockResolvedValue({patients: 31, practitioners: 7, slots: 420, appointments: 58});
    const {host} = mountPanel();
    await settle();
    expect(mocks.loadClinicStats).toHaveBeenCalledWith('cabinet-b');
    const values = [...host.querySelectorAll('.tile')].map(t => t.querySelector('dt')!.textContent + ' ' + t.querySelector('dd')!.textContent);
    expect(values).toEqual(['Rendez-vous 58', 'RDV sur 30 jours 12', 'Praticiens 7', 'Patients 31', 'Créneaux 420']);
  });

  it('affiche l’erreur quand le cabinet est inaccessible', async () => {
    mocks.loadClinicStats.mockRejectedValue(new Error('Cabinet introuvable.'));
    const {host} = mountPanel();
    await settle();
    expect(host.querySelector('[role="alert"]')!.textContent).toBe('Cabinet introuvable.');
    expect(host.querySelector('.tile')).toBeNull();
  });

  it('revient à la liste des cabinets', async () => {
    mocks.loadClinicStats.mockResolvedValue({patients: 0, practitioners: 0, slots: 0, appointments: 0});
    const {host, onBack} = mountPanel();
    await settle();
    host.querySelector<HTMLButtonElement>('button')!.click();
    expect(onBack).toHaveBeenCalledOnce();
  });
});
