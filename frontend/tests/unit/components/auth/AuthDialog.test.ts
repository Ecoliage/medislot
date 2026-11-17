import {afterEach, describe, expect, it, vi} from 'vitest';
import {createApp, h, type App} from 'vue';
import {createPinia} from 'pinia';
import AuthDialog from '../../../../src/components/auth/AuthDialog.vue';

const mounted: App[] = [];

function stubGoogleAvailability(enabled: boolean) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      new Response(JSON.stringify({enabled}), {status: 200, headers: {'Content-Type': 'application/json'}}),
    ),
  );
}

async function mountDialog(props: {initialError?: string} = {}) {
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp({render: () => h(AuthDialog, props)});
  app.use(createPinia());
  mounted.push(app);
  app.mount(host);
  await new Promise(resolve => setTimeout(resolve, 0));
  await new Promise(resolve => setTimeout(resolve, 0));
}

function googleButton() {
  return [...document.querySelectorAll<HTMLButtonElement>('button')].find(element =>
    element.textContent?.includes('Se connecter avec Google'),
  );
}

afterEach(() => {
  mounted.splice(0).forEach(app => app.unmount());
  document.body.replaceChildren();
  vi.unstubAllGlobals();
});

describe('AuthDialog', () => {
  it('propose la connexion Google quand le serveur l’annonce', async () => {
    stubGoogleAvailability(true);
    await mountDialog();
    expect(googleButton()).toBeDefined();
  });

  it('masque la connexion Google quand elle n’est pas configurée', async () => {
    stubGoogleAvailability(false);
    await mountDialog();
    expect(googleButton()).toBeUndefined();
  });

  it('affiche l’erreur transmise à l’ouverture', async () => {
    stubGoogleAvailability(false);
    await mountDialog({initialError: 'La connexion avec Google a échoué.'});
    expect(document.querySelector('[role="alert"]')?.textContent).toBe('La connexion avec Google a échoué.');
  });
});
