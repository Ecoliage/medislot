import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {createApp, nextTick, type App as VueApp} from 'vue';
import {createPinia} from 'pinia';
import App from '../../src/App.vue';
import {api, ApiError} from '../../src/api';
import type {Appointment, Professional, Slot} from '../../src/types';

vi.mock('../../src/api', async importOriginal => ({
  ...await importOriginal<typeof import('../../src/api')>(),
  api: vi.fn(),
  refreshCsrf: vi.fn(),
}));
vi.mock('../../src/composables/useLive', async () => {
  const {ref} = await import('vue');
  return {useLive: () => ({connected: ref(true), connect: vi.fn(), close: vi.fn()})};
});
vi.mock('../../src/components/NearbyMap.vue', () => ({default: {render: () => null}}));

const professional: Professional = {
  id: 1, name: 'Dr Martin', specialty: 'general', city: 'Lyon',
  address: '1 rue du Cabinet', bio: 'Médecin généraliste', fee: 30,
};
const slots: Slot[] = [
  {id: 11, professionalId: 1, startsAt: '2026-12-01T08:00:00Z', endsAt: '2026-12-01T08:30:00Z'},
  {id: 12, professionalId: 1, startsAt: '2026-12-01T09:00:00Z', endsAt: '2026-12-01T09:30:00Z'},
];
const clinic = {slug: 'cabinet-a', name: 'Cabinet des Tilleuls', city: 'Paris', color: '#0873b9'};
const mounted: VueApp[] = [];
let signedIn = false;
let appointments: Appointment[] = [];

function appointment(slot: Slot = slots[0]): Appointment {
  return {
    id: 41, slotId: slot.id, professionalId: 1, professionalName: professional.name,
    specialty: professional.specialty, address: professional.address, city: professional.city,
    startsAt: slot.startsAt, status: 'CONFIRMED',
  };
}

function mount(component: Parameters<typeof createApp>[0]) {
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp(component).use(createPinia());
  mounted.push(app);
  app.mount(host);
  return host;
}

function button(name: string, root: ParentNode = document) {
  const found = [...root.querySelectorAll<HTMLButtonElement>('button')]
    .find(element => element.textContent?.trim() === name || element.getAttribute('aria-label') === name);
  if (!found) throw new Error(`Bouton absent : ${name}`);
  return found;
}

function dialog() {
  const found = document.querySelector<HTMLElement>('[role="dialog"]');
  if (!found) throw new Error('Dialogue absent');
  return found;
}

async function login() {
  const form = dialog().querySelector('form')!;
  for (const [type, value] of [['email', 'patient@medislot.test'], ['password', 'Mot-de-passe-demo!']]) {
    const input = form.querySelector<HTMLInputElement>(`input[type="${type}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input', {bubbles: true}));
  }
  form.dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}));
  await vi.waitFor(() => expect(button('Camille · Déconnexion')).toBeTruthy());
}

beforeEach(() => {
  signedIn = false;
  appointments = [];
  window.history.replaceState(null, '', '/');
  vi.mocked(api).mockReset();
  vi.mocked(api).mockImplementation(async (path, init) => {
    let result: unknown;
    if (path.startsWith('/professionals?')) {
      result = {items: [professional], page: 0, size: 6, total: 1};
    } else if (path === '/professionals/1/slots') {
      result = slots;
    } else if (path === '/login') {
      signedIn = true;
    } else if (path === '/me') {
      if (!signedIn) throw new ApiError('Non connecté', 401);
      result = {id: 2, name: 'Camille Patient', email: 'patient@medislot.test', role: 'PATIENT'};
    } else if (path === '/clinic') {
      result = clinic;
    } else if (path === '/notifications') {
      result = [];
    } else if (path === '/appointments/41/cancel') {
      appointments = appointments.map(value => ({...value, status: 'CANCELLED'}));
    } else if (path === '/appointments' && !init?.method) {
      result = appointments;
    } else if (path === '/appointments' || path === '/appointments/41') {
      const body = JSON.parse(String(init?.body)) as {slotId: number};
      appointments = [appointment(slots.find(slot => slot.id === body.slotId)!)];
      result = appointments[0];
    } else {
      throw new Error(`Appel inattendu : ${path}`);
    }
    return result as never;
  });
});

afterEach(() => {
  mounted.splice(0).forEach(app => app.unmount());
  document.body.replaceChildren();
  localStorage.clear();
});

describe('parcours de rendez-vous après extraction des composants', () => {
  it('conserve le créneau pendant la connexion puis permet de réserver et annuler', async () => {
    mount(App);
    await vi.waitFor(() => expect(document.querySelector('.slot')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('.slot')!.click();
    await nextTick();
    expect(dialog().textContent).toContain('Heureux de vous retrouver');
    await login();
    expect(dialog().textContent).toContain('Confirmer votre rendez-vous');
    expect(dialog().textContent).toContain(professional.name);
    button('Confirmer le rendez-vous', dialog()).click();
    await vi.waitFor(() => expect(document.querySelector('.appointment .status')?.textContent).toBe('Confirmé'));
    expect(api).toHaveBeenCalledWith('/appointments', {
      method: 'POST', body: JSON.stringify({slotId: 11}),
    });
    expect(document.body.textContent).toContain('Votre rendez-vous est confirmé.');
    button('Annuler').click();
    await nextTick();
    button('Confirmer l’annulation', dialog()).click();
    await vi.waitFor(() => expect(document.querySelector('.appointment .status')?.textContent).toBe('Annulé'));
    expect(api).toHaveBeenCalledWith('/appointments/41/cancel', {method: 'POST'});
    expect(document.body.textContent).toContain('Votre rendez-vous a été annulé.');
  });

  it('déplace le rendez-vous existant vers le nouveau créneau', async () => {
    signedIn = true;
    appointments = [appointment()];
    mount(App);
    await vi.waitFor(() => expect(button('Camille · Déconnexion')).toBeTruthy());
    button('Mes rendez-vous').click();
    await vi.waitFor(() => expect(button('Déplacer')).toBeTruthy());
    button('Déplacer').click();
    await vi.waitFor(() => expect(document.querySelectorAll('.slot')).toHaveLength(2));
    document.querySelectorAll<HTMLButtonElement>('.slot')[1].click();
    await nextTick();
    button('Confirmer le déplacement', dialog()).click();
    await vi.waitFor(() => expect(document.querySelector('.appointment-date')?.textContent).toContain('10:00'));
    expect(api).toHaveBeenCalledWith('/appointments/41', {
      method: 'PATCH', body: JSON.stringify({slotId: 12}),
    });
    expect(document.body.textContent).toContain('Votre rendez-vous a été déplacé.');
  });

  it('abandonne la sélection à la fermeture du dialogue de connexion', async () => {
    mount(App);
    await vi.waitFor(() => expect(document.querySelector('.slot')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('.slot')!.click();
    await nextTick();
    button('Fermer', dialog()).click();
    await nextTick();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
    button('Se connecter').click();
    await nextTick();
    await login();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(api).not.toHaveBeenCalledWith('/appointments', expect.objectContaining({method: 'POST'}));
  });

  it('permet de réessayer la recherche après un conflit de réservation', async () => {
    signedIn = true;
    const implementation = vi.mocked(api).getMockImplementation()!;
    vi.mocked(api).mockImplementation(async (path, init) => {
      if (path === '/appointments' && init?.method === 'POST') throw new ApiError('Créneau déjà réservé', 409);
      return implementation(path, init);
    });
    mount(App);
    await vi.waitFor(() => expect(button('Camille · Déconnexion')).toBeTruthy());
    document.querySelector<HTMLButtonElement>('.slot')!.click();
    await nextTick();
    button('Confirmer le rendez-vous', dialog()).click();
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull());
    expect(document.querySelector('[role="alert"]')?.textContent).toContain('Créneau déjà réservé');
    button('Réessayer').click();
    await vi.waitFor(() => expect(document.querySelector('[role="alert"]')).toBeNull());
    expect(document.querySelector('.slot')).not.toBeNull();
    expect(api).toHaveBeenCalledWith('/professionals?q=&city=&specialty=&page=0&size=6');
  });
});
