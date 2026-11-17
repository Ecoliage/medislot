import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {createApp, nextTick, type App} from 'vue';
import {createPinia, setActivePinia, type Pinia} from 'pinia';
import {useAppointments} from '../../../src/composables/useAppointments';
import {useNotifications} from '../../../src/composables/useNotifications';
import {useClinic} from '../../../src/stores/clinic';
import {useSession} from '../../../src/stores/session';
import type {Appointment, Clinic, Notice} from '../../../src/types';

const mocks = vi.hoisted(() => ({
  loadAppointments: vi.fn(),
  loadNotifications: vi.fn(),
  connect: vi.fn(),
  close: vi.fn(),
}));

vi.mock('../../../src/services/appointmentsService', () => ({
  loadAppointments: mocks.loadAppointments,
  saveAppointment: vi.fn(),
  cancelAppointment: vi.fn(),
  loadAppointmentHistory: vi.fn(),
}));
vi.mock('../../../src/services/notificationsService', () => ({loadNotifications: mocks.loadNotifications}));
vi.mock('../../../src/composables/useLive', async () => {
  const {ref} = await import('vue');
  return {useLive: () => ({connected: ref(true), connect: mocks.connect, close: mocks.close})};
});

const clinicA: Clinic = {slug: 'cabinet-a', name: 'Cabinet des Tilleuls', city: 'Paris', color: '#0873b9'};
const clinicB: Clinic = {slug: 'cabinet-b', name: 'Maison de santé du Canal', city: 'Lyon', color: '#0b7a5c'};
const appointment = (id: number, professionalName: string): Appointment => ({
  id, slotId: id, professionalId: 1, professionalName, specialty: 'general',
  address: '1 rue du Cabinet', city: 'Paris', startsAt: '2026-12-01T08:00:00Z', status: 'CONFIRMED',
});
const notice = (id: number, message: string): Notice => ({id, message, createdAt: '2026-11-17T08:00:00Z'});

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(onResolve => {
    resolve = onResolve;
  });
  return {promise, resolve};
}

const apps: App[] = [];
let pinia: Pinia;

function mount() {
  let booking!: ReturnType<typeof useAppointments>;
  let notifications!: ReturnType<typeof useNotifications>;
  const app = createApp({
    setup() {
      booking = useAppointments({
        refreshSlots: async () => {},
        refreshNotices: async () => {},
        onConfirmed: () => {},
        onMove: async () => {},
      });
      notifications = useNotifications(async () => {});
      return () => null;
    },
  });
  app.use(pinia);
  apps.push(app);
  app.mount(document.createElement('div'));
  return {booking, notifications};
}

beforeEach(() => {
  vi.resetAllMocks();
  window.history.replaceState(null, '', '/c/cabinet-a/');
  pinia = createPinia();
  setActivePinia(pinia);
  useClinic().mine = [clinicA, clinicB];
  useSession().user = {id: 1, name: 'Camille Dupont', email: 'patient@medislot.test', role: 'PATIENT'};
  mocks.loadAppointments.mockResolvedValue([]);
  mocks.loadNotifications.mockResolvedValue([]);
});

afterEach(() => apps.splice(0).forEach(app => app.unmount()));

describe('changement de cabinet et cache', () => {
  it('vide les rendez-vous et les notifications dès la bascule, puis recharge ceux du nouveau cabinet', async () => {
    mocks.loadAppointments.mockResolvedValueOnce([appointment(1, 'Dr Sarah Martin')]);
    mocks.loadNotifications.mockResolvedValueOnce([notice(1, 'Rendez-vous confirmé dans A')]);
    const {booking, notifications} = mount();
    await booking.refresh();
    await notifications.refresh();
    expect(booking.appointments).toHaveLength(1);
    expect(notifications.notices).toHaveLength(1);
    mocks.loadAppointments.mockResolvedValue([appointment(9, 'Dr Amel Benali')]);
    mocks.loadNotifications.mockResolvedValue([]);

    const switching = useClinic().switchTo('cabinet-b');

    expect(booking.appointments).toEqual([]);
    expect(notifications.notices).toEqual([]);
    expect(await switching).toBe(true);
    await vi.waitFor(() => expect(booking.appointments.map(value => value.professionalName)).toEqual(['Dr Amel Benali']));
    expect(mocks.connect).toHaveBeenLastCalledWith(
      '/api/notifications/events?clinic=cabinet-b', expect.any(Function), 'notifications-changed',
    );
  });

  it('jette une réponse arrivée après le changement de cabinet', async () => {
    const slowAppointments = deferred<Appointment[]>();
    const slowNotices = deferred<Notice[]>();
    mocks.loadAppointments.mockReturnValueOnce(slowAppointments.promise);
    mocks.loadNotifications.mockReturnValueOnce(slowNotices.promise);
    const {booking, notifications} = mount();
    const pendingAppointments = booking.refresh();
    const pendingNotices = notifications.refresh();

    await useClinic().switchTo('cabinet-b');
    await nextTick();
    slowAppointments.resolve([appointment(1, 'Dr Sarah Martin')]);
    slowNotices.resolve([notice(1, 'Rendez-vous confirmé dans A')]);
    await Promise.all([pendingAppointments, pendingNotices]);

    expect(booking.appointments).toEqual([]);
    expect(notifications.notices).toEqual([]);
  });

  it('ne garde aucun abonnement au cache après le démontage', async () => {
    const {booking} = mount();
    apps.splice(0).forEach(app => app.unmount());
    booking.appointments = [appointment(1, 'Dr Sarah Martin')];

    await useClinic().switchTo('cabinet-b');

    expect(booking.appointments).toHaveLength(1);
  });
});
