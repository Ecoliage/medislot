import {describe, it, expect, vi, beforeEach} from 'vitest';
import {api, ApiError} from '../../src/api';
describe('Front/back : statut et corps', () => {
  beforeEach(() => vi.restoreAllMocks());
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
  it.skip('propage une panne réseau', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Network failure')));
    await expect(api('/professionals')).rejects.toThrow('Network failure');
  });
});
