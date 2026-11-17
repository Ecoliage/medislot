import {beforeEach, describe, expect, it, vi} from 'vitest';
import {createApp, nextTick} from 'vue';
import PlatformJournalPanel from '../../../../src/components/admin/PlatformJournalPanel.vue';

const mocks = vi.hoisted(() => ({loadPlatformEvents: vi.fn()}));
vi.mock('../../../../src/services/adminService', () => mocks);

const settle = async () => {
  for (let i = 0; i < 4; i++) await Promise.resolve();
  await nextTick();
};

function mountPanel() {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const onBack = vi.fn();
  createApp(PlatformJournalPanel, {onBack}).mount(host);
  return {host, onBack};
}

describe('PlatformJournalPanel', () => {
  beforeEach(() => {
    mocks.loadPlatformEvents.mockReset();
    document.body.innerHTML = '';
  });

  it('montre qui a fait quoi sur quel cabinet', async () => {
    mocks.loadPlatformEvents.mockResolvedValue([
      {id: 2, action: 'CLINIC_UPDATED', label: 'Cabinet modifié', actorEmail: 'second@medislot.test', clinicSlug: 'part-dieu', clinicName: 'Cabinet de la Part-Dieu', createdAt: '2026-11-17T10:00:00Z'},
      {id: 1, action: 'CLINIC_CREATED', label: 'Cabinet créé', actorEmail: 'admin@medislot.test', clinicSlug: 'part-dieu', clinicName: 'Cabinet de la Part-Dieu', createdAt: '2026-11-17T09:00:00Z'},
    ]);
    const {host} = mountPanel();
    await settle();
    const rows = [...host.querySelectorAll('tbody tr')].map(row => {
      const cells = row.querySelectorAll('td');
      return [cells[1].textContent, cells[2].querySelector('.slug')!.textContent, cells[3].textContent];
    });
    expect(rows).toEqual([
      ['Cabinet modifié', 'part-dieu', 'second@medislot.test'],
      ['Cabinet créé', 'part-dieu', 'admin@medislot.test'],
    ]);
  });

  it('signale un journal vide', async () => {
    mocks.loadPlatformEvents.mockResolvedValue([]);
    const {host} = mountPanel();
    await settle();
    expect(host.querySelector('.empty')!.textContent).toBe('Aucune action enregistrée.');
  });

  it('revient à la liste des cabinets', async () => {
    mocks.loadPlatformEvents.mockResolvedValue([]);
    const {host, onBack} = mountPanel();
    await settle();
    host.querySelector<HTMLButtonElement>('button')!.click();
    expect(onBack).toHaveBeenCalledOnce();
  });
});
