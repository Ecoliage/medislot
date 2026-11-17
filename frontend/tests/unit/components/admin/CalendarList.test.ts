import {beforeEach, describe, expect, it, vi} from 'vitest';
import {createApp, nextTick} from 'vue';
import CalendarList from '../../../../src/components/admin/CalendarList.vue';

const mocks = vi.hoisted(() => ({
  loadClinics: vi.fn(),
  loadClinicPractitioners: vi.fn(),
  loadPractitionerWeek: vi.fn(),
}));
vi.mock('../../../../src/services/adminService', () => mocks);

const settle = async () => {
  for (let i = 0; i < 10; i++) await Promise.resolve();
  await nextTick();
};

beforeEach(() => {
  vi.useFakeTimers({toFake: ['Date']});
  vi.setSystemTime(new Date('2026-11-17T10:00:00+01:00'));
  Object.values(mocks).forEach(m => m.mockReset());
  mocks.loadClinics.mockResolvedValue([
    {slug: 'cabinet-a', name: 'Cabinet des Tilleuls', city: 'Paris'},
    {slug: 'brotteaux', name: 'Cabinet des Brotteaux', city: 'Lyon'},
  ]);
  mocks.loadClinicPractitioners.mockResolvedValue([{id: 7, name: 'Dr Amel Benali', specialty: 'general'}]);
  mocks.loadPractitionerWeek.mockResolvedValue([
    {id: 1, startsAt: '2026-11-16T08:00:00Z', patient: 'P-7F3A', status: 'CONFIRMED'},
    {id: 2, startsAt: '2026-11-17T09:00:00Z', patient: 'P-9B04', status: 'CANCELLED'},
  ]);
});

async function mount(slug?: string) {
  const host = document.createElement('div');
  const onClinics = vi.fn();
  createApp(CalendarList, {slug, onClinics}).mount(host);
  await settle();
  await settle();
  return {host, onClinics};
}

describe('rendez-vous d’un praticien', () => {
  it('ouvre le cabinet demandé, son premier praticien et la semaine en cours', async () => {
    const {host} = await mount('brotteaux');
    expect(mocks.loadClinicPractitioners).toHaveBeenCalledWith('brotteaux');
    expect(mocks.loadPractitionerWeek).toHaveBeenCalledWith('brotteaux', 7, '2026-11-16');
    expect(host.querySelector('.breadcrumb')!.textContent).toMatch(/Cabinets\s*›\s*Cabinet des Brotteaux\s*›\s*Dr Amel Benali/);
    expect(host.querySelector('h2')!.textContent).toBe('Rendez-vous · Dr Amel Benali');
    const rows = [...host.querySelectorAll('tbody tr')].map(r => [...r.querySelectorAll('td')].map(td => td.textContent!.trim()));
    expect(rows).toEqual([
      ['lun. 16/11', '09:00', 'P-7F3A', 'Confirmé'],
      ['mar. 17/11', '10:00', 'P-9B04', 'Annulé'],
    ]);
  });

  it('filtre par statut et revient aux cabinets par le fil d’Ariane', async () => {
    const {host, onClinics} = await mount();
    const status = host.querySelector<HTMLSelectElement>('select[aria-label="Statut"]')!;
    status.value = 'CANCELLED';
    status.dispatchEvent(new Event('change'));
    await nextTick();
    expect(host.querySelectorAll('tbody tr')).toHaveLength(1);
    host.querySelector<HTMLButtonElement>('.crumb')!.click();
    expect(onClinics).toHaveBeenCalled();
  });

  it('recharge quand on change de semaine', async () => {
    const {host} = await mount();
    const week = host.querySelector<HTMLSelectElement>('select[aria-label="Semaine"]')!;
    expect(week.selectedOptions[0].textContent).toBe('Semaine du 16/11');
    week.value = '2026-11-23';
    week.dispatchEvent(new Event('change'));
    await settle();
    expect(mocks.loadPractitionerWeek).toHaveBeenLastCalledWith('cabinet-a', 7, '2026-11-23');
  });
});
