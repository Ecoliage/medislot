import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {createApp, type App} from 'vue';
import {createPinia} from 'pinia';
import {usePractitionerSearch, type PractitionerSearch} from '../../../src/composables/usePractitionerSearch';
import {useClinic} from '../../../src/stores/clinic';
import type {Page, Professional, Slot} from '../../../src/types';

const mocks = vi.hoisted(() => ({
  searchProfessionals: vi.fn(),
  loadProfessionalSlots: vi.fn(),
  connect: vi.fn(),
}));

vi.mock('../../../src/services/practitionersService', () => ({
  searchProfessionals: mocks.searchProfessionals,
  loadProfessionalSlots: mocks.loadProfessionalSlots,
}));
vi.mock('../../../src/composables/useLive', async () => {
  const {ref} = await import('vue');
  return {useLive: () => ({connected: ref(false), connect: mocks.connect})};
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((onResolve, onReject) => {
    resolve = onResolve;
    reject = onReject;
  });
  return {promise, resolve, reject};
}

const professional = (id: number): Professional => ({
  id,
  name: `Dr ${id}`,
  specialty: 'general',
  city: 'Lyon',
  address: '1 rue de la République',
  bio: '',
  fee: 30,
});
const page = (...professionals: Professional[]): Page<Professional> => ({
  items: professionals,
  total: professionals.length,
  page: 0,
  size: 6,
});
const slot = (id: number, professionalId: number): Slot => ({
  id,
  professionalId,
  startsAt: '2026-11-18T09:00:00Z',
  endsAt: '2026-11-18T09:30:00Z',
});
const settle = async () => {
  await Promise.resolve();
  await Promise.resolve();
};

const apps: App[] = [];
function mountSearch(): PractitionerSearch {
  let search!: PractitionerSearch;
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp({
    setup() {
      useClinic(); // as App.vue does first: sets the clinic sent with every call
      search = usePractitionerSearch();
      return () => null;
    },
  });
  app.use(createPinia());
  apps.push(app);
  app.mount(host);
  return search;
}

beforeEach(() => {
  vi.resetAllMocks();
  mocks.searchProfessionals.mockResolvedValue(page());
  mocks.loadProfessionalSlots.mockResolvedValue({});
});
afterEach(() => {
  apps.splice(0).forEach(app => app.unmount());
  document.body.replaceChildren();
});

describe('usePractitionerSearch', () => {
  it('reste en chargement jusqu’aux disponibilités et affiche les filtres demandés', async () => {
    const doctor = professional(1);
    const availability = deferred<Record<number, Slot[]>>();
    mocks.searchProfessionals.mockResolvedValue(page(doctor));
    mocks.loadProfessionalSlots.mockReturnValue(availability.promise);
    const search = mountSearch();
    search.q = 'Martin';
    search.city = 'Lyon';
    search.specialty = 'cardiology';
    search.page = 2;

    const pending = search.search();
    await settle();
    expect(search.loading).toBe(true);
    expect(mocks.searchProfessionals).toHaveBeenCalledWith(expect.objectContaining({
      q: 'Martin', city: 'Lyon', specialty: 'cardiology', page: 2, size: 6, place: null,
    }));
    availability.resolve({1: [slot(10, 1)]});
    await pending;

    expect(search.loading).toBe(false);
    expect(search.doctors).toEqual([doctor]);
    expect(search.slots).toEqual({1: [slot(10, 1)]});
    expect(search.total).toBe(1);
  });

  it('recommence à la première page autour d’un lieu puis retrouve la recherche classique', async () => {
    const search = mountSearch();
    const place = {lat: 45.764, lon: 4.836, label: 'Lyon'};
    search.page = 3;
    search.specialty = 'cardiology';

    await search.aroundPlace(place);
    expect(mocks.searchProfessionals).toHaveBeenLastCalledWith(expect.objectContaining({
      place, page: 0, specialty: 'cardiology', radiusKm: 5,
    }));
    search.page = 2;
    await search.leaveNearby();
    expect(search.place).toBeNull();
    expect(mocks.searchProfessionals).toHaveBeenLastCalledWith(expect.objectContaining({
      place: null, page: 0, specialty: 'cardiology',
    }));
  });

  it('rafraîchit les disponibilités du résultat courant lors d’un événement en direct', async () => {
    const doctor = professional(1);
    mocks.searchProfessionals.mockResolvedValue(page(doctor));
    mocks.loadProfessionalSlots.mockResolvedValueOnce({1: [slot(10, 1)]});
    const search = mountSearch();
    await search.search();
    mocks.loadProfessionalSlots.mockResolvedValue({1: [slot(11, 1)]});

    const onChange = mocks.connect.mock.calls[0][1] as () => Promise<void>;
    await onChange();

    expect(mocks.connect.mock.calls[0][0]).toBe('/api/events?clinic=cabinet-a');
    expect(search.slots).toEqual({1: [slot(11, 1)]});
    expect(search.doctors).toEqual([doctor]);
    expect(mocks.searchProfessionals).toHaveBeenCalledTimes(1);
  });

  it('conserve le résultat récent quand une ancienne recherche répond en retard', async () => {
    const old = deferred<Page<Professional>>();
    mocks.searchProfessionals.mockReturnValueOnce(old.promise).mockResolvedValueOnce(page(professional(2)));
    const search = mountSearch();
    const oldRequest = search.search();
    search.q = 'nouveau';
    await search.search();

    old.resolve(page(professional(1)));
    await oldRequest;

    expect(search.doctors).toEqual([professional(2)]);
    expect(mocks.loadProfessionalSlots).toHaveBeenCalledTimes(1);
    expect(search.loading).toBe(false);
  });

  it('ignore une erreur obsolète sans arrêter le chargement de la nouvelle recherche', async () => {
    const old = deferred<Page<Professional>>();
    const recent = deferred<Page<Professional>>();
    mocks.searchProfessionals.mockReturnValueOnce(old.promise).mockReturnValueOnce(recent.promise);
    const search = mountSearch();
    const oldRequest = search.search();
    const recentRequest = search.search();

    old.reject(new Error('ancienne panne'));
    await oldRequest;
    expect(search.error).toBe('');
    expect(search.loading).toBe(true);
    recent.resolve(page(professional(2)));
    await recentRequest;

    expect(search.error).toBe('');
    expect(search.doctors).toEqual([professional(2)]);
    expect(search.loading).toBe(false);
  });

  it.each(['success', 'failure'] as const)('ignore les créneaux obsolètes (%s) après une nouvelle recherche', async outcome => {
    const oldSlots = deferred<Record<number, Slot[]>>();
    mocks.searchProfessionals.mockResolvedValueOnce(page(professional(1))).mockResolvedValueOnce(page(professional(2)));
    mocks.loadProfessionalSlots.mockReturnValueOnce(oldSlots.promise).mockResolvedValueOnce({2: [slot(20, 2)]});
    const search = mountSearch();
    const oldRequest = search.search();
    await settle();
    await search.search();

    if (outcome === 'success') oldSlots.resolve({1: [slot(10, 1)]});
    else oldSlots.reject(new Error('anciens créneaux indisponibles'));
    await oldRequest;

    expect(search.doctors).toEqual([professional(2)]);
    expect(search.slots).toEqual({2: [slot(20, 2)]});
    expect(search.error).toBe('');
    expect(search.loading).toBe(false);
  });
});
