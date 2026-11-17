import {describe, it, expect, vi, beforeEach} from 'vitest';
import {api, apiUrl, ApiError, setApiClinic} from '../../src/api';
describe('Front/back : statut et corps', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    setApiClinic(null);
  });
  it('expose le message métier pour un conflit HTTP', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify({detail: 'Créneau déjà réservé'}), {status: 409})),
    );
    await expect(api('/appointments')).rejects.toEqual(new ApiError('Créneau déjà réservé', 409));
  });
  it('remplace le message d’un trop grand nombre de tentatives', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html></html>', {status: 429})));
    await expect(api('/compte/oubli')).rejects.toEqual(
      new ApiError('Trop de tentatives, réessayez dans quelques minutes.', 429),
    );
  });
  it('conserve un résultat vide comme succès', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify({items: [], total: 0}), {status: 200})),
    );
    await expect(api('/professionals')).resolves.toEqual({items: [], total: 0});
  });
  it('envoie le cabinet courant dans l’en-tête X-Clinic de chaque appel', async () => {
    const fetcher = vi.fn().mockImplementation(async () => new Response('[]', {status: 200}));
    vi.stubGlobal('fetch', fetcher);
    await api('/professionals');
    expect(new Headers(fetcher.mock.calls[0][1].headers).has('X-Clinic')).toBe(false);
    setApiClinic('cabinet-b');
    await api('/professionals');
    await api('/appointments/4/cancel', {method: 'POST', headers: {'X-CSRF-TOKEN': 't'}});
    expect(new Headers(fetcher.mock.calls[1][1].headers).get('X-Clinic')).toBe('cabinet-b');
    expect(new Headers(fetcher.mock.calls.at(-1)![1].headers).get('X-Clinic')).toBe('cabinet-b');
  });
  it('ajoute le cabinet aux adresses ouvertes sans en-tête', () => {
    expect(apiUrl('/events')).toBe('/api/events');
    setApiClinic('cabinet-b');
    expect(apiUrl('/events')).toBe('/api/events?clinic=cabinet-b');
    expect(apiUrl('/appointments/3/convocation.pdf?download=true')).toBe(
      '/api/appointments/3/convocation.pdf?download=true&clinic=cabinet-b',
    );
  });
  it.skip('propage une panne réseau', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Network failure')));
    await expect(api('/professionals')).rejects.toThrow('Network failure');
  });
});
