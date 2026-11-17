import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {createPinia, setActivePinia} from 'pinia';
import {api, setApiClinic} from '../../../src/api';
import {useClinic} from '../../../src/stores/clinic';
import {onClinicChange} from '../../../src/tenantCache';
import type {Clinic} from '../../../src/types';

vi.mock('../../../src/api', async importOriginal => ({
  ...(await importOriginal<typeof import('../../../src/api')>()),
  api: vi.fn(),
  setApiClinic: vi.fn(),
}));

const clinicA: Clinic = {slug: 'cabinet-a', name: 'Cabinet des Tilleuls', city: 'Paris', color: '#0873b9'};
const clinicB: Clinic = {slug: 'cabinet-b', name: 'Maison de santé du Canal', city: 'Lyon', color: '#0b7a5c'};
const unsubscribe: (() => void)[] = [];

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(onResolve => {
    resolve = onResolve;
  });
  return {promise, resolve};
}

function memberships(...clinics: Clinic[]) {
  vi.mocked(api).mockImplementation(async path => {
    if (path === '/me/clinics') return clinics as never;
    throw new Error(`Appel inattendu : ${path}`);
  });
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.mocked(api).mockReset();
  vi.mocked(setApiClinic).mockReset();
  window.history.replaceState(null, '', '/c/cabinet-a/');
});

afterEach(() => unsubscribe.splice(0).forEach(stop => stop()));

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

  it('bascule vers un cabinet à soi : adresse, en-tête et fiche du cabinet', async () => {
    memberships(clinicA, clinicB);
    const clinic = useClinic();
    await clinic.afterLogin();

    expect(await clinic.switchTo('cabinet-b')).toBe(true);

    expect(clinic.slug).toBe('cabinet-b');
    expect(clinic.current).toEqual(clinicB);
    expect(window.location.pathname).toBe('/c/cabinet-b/');
    expect(setApiClinic).toHaveBeenLastCalledWith('cabinet-b');
  });

  it('refuse un cabinet qui n’est pas à soi, inconnu ou mal formé', async () => {
    memberships(clinicA);
    const clinic = useClinic();
    await clinic.afterLogin();
    const cleared = vi.fn();
    unsubscribe.push(onClinicChange(cleared));

    for (const target of ['cabinet-b', 'cabinet-z', '../cabinet-b', 'Cabinet-B', '']) {
      expect(await clinic.switchTo(target)).toBe(false);
    }

    expect(clinic.slug).toBe('cabinet-a');
    expect(window.location.pathname).toBe('/c/cabinet-a/');
    expect(cleared).not.toHaveBeenCalled();
    expect(setApiClinic).toHaveBeenCalledTimes(1);
  });

  it('refuse toute bascule à un visiteur non connecté', async () => {
    const clinic = useClinic();
    expect(await clinic.switchTo('cabinet-b')).toBe(false);
    expect(clinic.slug).toBe('cabinet-a');
  });

  it('vide le cache avant de basculer', async () => {
    memberships(clinicA, clinicB);
    const clinic = useClinic();
    await clinic.afterLogin();
    const seen: string[] = [];
    unsubscribe.push(onClinicChange(() => seen.push(`${clinic.slug} ${window.location.pathname}`)));

    await clinic.switchTo('cabinet-b');

    expect(seen).toEqual(['cabinet-a /c/cabinet-a/']);
  });

  it('jette la fiche d’un cabinet arrivée après le changement de cabinet', async () => {
    const late = deferred<Clinic>();
    vi.mocked(api).mockImplementation(async path => {
      if (path === '/me/clinics') return [clinicA, clinicB] as never;
      if (path === '/clinic') return late.promise as never;
      throw new Error(`Appel inattendu : ${path}`);
    });
    const clinic = useClinic();
    const loading = clinic.loadCurrent();
    await clinic.afterLogin();
    await clinic.switchTo('cabinet-b');

    late.resolve(clinicA);
    await loading;

    expect(clinic.current).toEqual(clinicB);
  });
});

describe('choix du cabinet à la connexion', () => {
  it('garde le cabinet courant s’il fait partie des appartenances', async () => {
    window.history.replaceState(null, '', '/c/cabinet-b/');
    memberships(clinicA, clinicB);
    const clinic = useClinic();

    expect(await clinic.afterLogin()).toBe(false);

    expect(clinic.mine).toEqual([clinicA, clinicB]);
    expect(clinic.slug).toBe('cabinet-b');
  });

  it('bascule sur le premier cabinet de la personne sinon', async () => {
    window.history.replaceState(null, '', '/c/cabinet-b/');
    memberships(clinicA);
    const clinic = useClinic();

    expect(await clinic.afterLogin()).toBe(true);

    expect(clinic.slug).toBe('cabinet-a');
    expect(window.location.pathname).toBe('/c/cabinet-a/');
  });

  it('ne bascule nulle part sans appartenance', async () => {
    memberships();
    const clinic = useClinic();
    expect(await clinic.afterLogin()).toBe(false);
    expect(clinic.slug).toBe('cabinet-a');
  });
});
