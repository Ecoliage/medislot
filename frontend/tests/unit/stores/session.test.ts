import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {createPinia, setActivePinia} from 'pinia';
import {useSession} from '../../../src/stores/session';

const identity = {id: 1, name: 'Camille', email: 'camille@medislot.test', role: 'PATIENT'};

function reply(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {status});
}

describe('session', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('mémorise l’identité chargée puis l’oublie à la déconnexion', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url === '/api/me') return reply(200, identity);
        if (url === '/api/csrf') return reply(200, {token: 't', headerName: 'X-CSRF-TOKEN'});
        return reply(204);
      }),
    );
    const session = useSession();
    await session.load();
    expect(localStorage.getItem('medislot_user')).toBe(JSON.stringify(identity));
    await session.logout();
    expect(session.user).toBeNull();
    expect(localStorage.getItem('medislot_user')).toBeNull();
  });

  it('oublie l’identité quand la session n’est plus valide', async () => {
    localStorage.setItem('medislot_user', JSON.stringify(identity));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply(401, {title: 'Non authentifié'})));
    const session = useSession();
    await session.load();
    expect(session.user).toBeNull();
    expect(localStorage.getItem('medislot_user')).toBeNull();
  });
});
