import {beforeEach, describe, expect, it, vi} from 'vitest';
import {createPinia, setActivePinia} from 'pinia';
import {api, setApiClinic} from '../../../src/api';
import {useClinic} from '../../../src/stores/clinic';
import type {Clinic} from '../../../src/types';

vi.mock('../../../src/api', async importOriginal => ({
  ...(await importOriginal<typeof import('../../../src/api')>()),
  api: vi.fn(),
  setApiClinic: vi.fn(),
}));

const clinicA: Clinic = {slug: 'cabinet-a', name: 'Cabinet des Tilleuls', city: 'Paris', color: '#0873b9'};

beforeEach(() => {
  setActivePinia(createPinia());
  vi.mocked(api).mockReset();
  vi.mocked(setApiClinic).mockReset();
  window.history.replaceState(null, '', '/c/cabinet-a/');
});

describe('cabinet courant', () => {
  it('vient de l’adresse au chargement et part dans l’en-tête', () => {
    window.history.replaceState(null, '', '/c/cabinet-b/');
    const clinic = useClinic();
    expect(clinic.slug).toBe('cabinet-b');
    expect(setApiClinic).toHaveBeenCalledWith('cabinet-b');
  });

  it('ramène une adresse sans cabinet sur le cabinet par défaut en gardant la requête', () => {
    window.history.replaceState(null, '', '/?page=admin');
    const clinic = useClinic();
    clinic.init();
    expect(clinic.slug).toBe('cabinet-a');
    expect(window.location.pathname + window.location.search).toBe('/c/cabinet-a/?page=admin');
  });

  it('charge la fiche du cabinet courant', async () => {
    vi.mocked(api).mockImplementation(async path => {
      if (path === '/clinic') return clinicA as never;
      throw new Error(`Appel inattendu : ${path}`);
    });
    const clinic = useClinic();
    await clinic.loadCurrent();
    expect(clinic.current).toEqual(clinicA);
  });
});
