import {describe, it, expect, vi} from 'vitest';
import {createApp, nextTick} from 'vue';
import ConvocationButton from './components/ConvocationButton.vue';
import {
  ConvocationError,
  FALLBACK,
  MESSAGES,
  OFFLINE,
  REVOKE_DELAY,
  convocationUrl,
  fetchConvocation,
  filenameFrom,
  openConvocation,
  saveConvocation,
  type Browser,
  type Tab,
} from './convocation';

const pdfResponse = () =>
  new Response('%PDF-1.7', {
    status: 200,
    headers: {'Content-Type': 'application/pdf', 'Content-Disposition': 'inline; filename="convocation-12.pdf"'},
  });
const problemResponse = (status: number) =>
  new Response(JSON.stringify({title: 'erreur'}), {status, headers: {'Content-Type': 'application/problem+json'}});
const fetcherOf = (response: Response) => vi.fn().mockResolvedValue(response) as unknown as typeof fetch;

function fakeBrowser() {
  const tab = {show: vi.fn(), close: vi.fn()} satisfies Tab;
  const browser = {
    openTab: vi.fn(() => tab),
    createUrl: vi.fn(() => 'blob:convocation'),
    revoke: vi.fn(),
    save: vi.fn(),
    later: vi.fn(),
  } satisfies Browser;
  return {tab, browser};
}

describe('URL et nom de fichier', () => {
  it('construit l’URL d’affichage et de téléchargement', () => {
    expect(convocationUrl(12)).toBe('/api/appointments/12/convocation.pdf');
    expect(convocationUrl(12, true)).toBe('/api/appointments/12/convocation.pdf?download=true');
  });
  it('lit le nom de fichier avec ou sans guillemets', () => {
    expect(filenameFrom('inline; filename="convocation-12.pdf"', 12)).toBe('convocation-12.pdf');
    expect(filenameFrom('attachment; filename=convocation-12.pdf', 12)).toBe('convocation-12.pdf');
  });
  it('ne garde que le nom de base, avec un nom par défaut sans en-tête', () => {
    expect(filenameFrom('attachment; filename="../../etc/passwd"', 12)).toBe('passwd');
    expect(filenameFrom(null, 12)).toBe('convocation-12.pdf');
  });
});

describe('fetchConvocation', () => {
  it('retourne le blob et le nom du fichier', async () => {
    const fetcher = fetcherOf(pdfResponse());
    const file = await fetchConvocation(12, false, fetcher);
    expect(file.filename).toBe('convocation-12.pdf');
    expect(file.blob.size).toBeGreaterThan(0);
    expect(fetcher).toHaveBeenCalledWith('/api/appointments/12/convocation.pdf', {
      credentials: 'same-origin',
      headers: {Accept: 'application/pdf'},
    });
  });
  it('traduit les erreurs HTTP, avec un message par défaut', async () => {
    await expect(fetchConvocation(12, false, fetcherOf(problemResponse(404)))).rejects.toMatchObject({
      message: MESSAGES[404],
      status: 404,
    });
    await expect(fetchConvocation(12, false, fetcherOf(problemResponse(409)))).rejects.toMatchObject({
      message: MESSAGES[409],
      status: 409,
    });
    await expect(fetchConvocation(12, false, fetcherOf(problemResponse(503)))).rejects.toMatchObject({
      message: FALLBACK,
      status: 503,
    });
  });
  it('signale une panne réseau', async () => {
    const fetcher = vi.fn().mockRejectedValue(new TypeError('Failed to fetch')) as unknown as typeof fetch;
    await expect(fetchConvocation(12, false, fetcher)).rejects.toMatchObject({message: OFFLINE});
  });
  it('refuse une réponse qui n’est pas un PDF', async () => {
    const html = new Response('<html></html>', {status: 200, headers: {'Content-Type': 'text/html'}});
    await expect(fetchConvocation(12, false, fetcherOf(html))).rejects.toMatchObject({message: FALLBACK});
  });
});

describe('openConvocation et saveConvocation', () => {
  it('ouvre l’onglet avant la réponse', async () => {
    const {tab, browser} = fakeBrowser();
    let resolveFetch: (r: Response) => void = () => {};
    const fetcher = vi.fn(
      () =>
        new Promise<Response>(resolve => {
          resolveFetch = resolve;
        }),
    ) as unknown as typeof fetch;
    const pending = openConvocation(12, browser, fetcher);
    expect(browser.openTab).toHaveBeenCalledTimes(1);
    expect(tab.show).not.toHaveBeenCalled();
    resolveFetch(pdfResponse());
    await pending;
    expect(tab.show).toHaveBeenCalledWith('blob:convocation');
  });
  it('ferme l’onglet et relaie l’erreur', async () => {
    const {tab, browser} = fakeBrowser();
    await expect(openConvocation(12, browser, fetcherOf(problemResponse(404)))).rejects.toMatchObject({
      message: MESSAGES[404],
    });
    expect(tab.close).toHaveBeenCalledTimes(1);
    expect(tab.show).not.toHaveBeenCalled();
  });
  it('libère l’URL après le délai, et enregistre', async () => {
    const {browser} = fakeBrowser();
    await openConvocation(12, browser, fetcherOf(pdfResponse()));
    const [release, delay] = browser.later.mock.calls[0] as unknown as [() => void, number];
    expect(delay).toBe(REVOKE_DELAY);
    release();
    expect(browser.revoke).toHaveBeenCalledWith('blob:convocation');

    const saving = fakeBrowser();
    const fetcher = fetcherOf(pdfResponse());
    await saveConvocation(12, saving.browser, fetcher);
    expect(saving.browser.save).toHaveBeenCalledWith('blob:convocation', 'convocation-12.pdf');
    expect(fetcher).toHaveBeenCalledWith('/api/appointments/12/convocation.pdf?download=true', expect.anything());
    expect(saving.tab.show).not.toHaveBeenCalled();
  });
});

describe('ConvocationButton', () => {
  function mountButton(open: (id: number) => Promise<void>, save: (id: number) => Promise<void> = async () => {}) {
    const host = document.createElement('div');
    document.body.appendChild(host);
    createApp(ConvocationButton, {id: 12, open, save}).mount(host);
    const buttons = () => Array.from(host.querySelectorAll('button'));
    return {host, buttons};
  }
  const flush = async () => {
    await Promise.resolve();
    await nextTick();
  };

  it('désactive les boutons pendant l’attente', async () => {
    const {buttons} = mountButton(() => new Promise<void>(() => {}));
    buttons()[0].click();
    await nextTick();
    expect(buttons().every(b => b.disabled)).toBe(true);
    expect(buttons()[0].textContent).toContain('Préparation');
  });
  it('affiche l’erreur', async () => {
    const {host, buttons} = mountButton(() => Promise.reject(new ConvocationError(MESSAGES[409], 409)));
    buttons()[0].click();
    await flush();
    await flush();
    expect(host.querySelector('[role=alert]')?.textContent).toContain(MESSAGES[409]);
    expect(buttons().every(b => !b.disabled)).toBe(true);
  });
  it('efface l’erreur après un succès', async () => {
    let attempt = 0;
    const open = () => (attempt++ === 0 ? Promise.reject(new ConvocationError(MESSAGES[500], 500)) : Promise.resolve());
    const {host, buttons} = mountButton(open);
    buttons()[0].click();
    await flush();
    await flush();
    expect(host.querySelector('[role=alert]')).not.toBeNull();
    buttons()[0].click();
    await flush();
    await flush();
    expect(host.querySelector('[role=alert]')).toBeNull();
    expect(buttons().every(b => !b.disabled)).toBe(true);
  });
});
