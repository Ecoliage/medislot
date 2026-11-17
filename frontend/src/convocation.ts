import {apiUrl} from './api';

export const MESSAGES: Record<number, string> = {
  401: 'Connectez-vous pour obtenir votre convocation.',
  404: 'Ce rendez-vous est introuvable.',
  409: 'Ce rendez-vous est annulé : il n’a plus de convocation.',
  500: 'La convocation n’a pas pu être préparée. Réessayez dans un instant.',
};
export const FALLBACK = 'La convocation n’est pas disponible pour le moment.';
export const OFFLINE = 'Connexion impossible. Vérifiez le réseau puis réessayez.';
export const REVOKE_DELAY = 60_000;

export class ConvocationError extends Error {
  constructor(
    message: string,
    public status?: number,
  ) {
    super(message);
  }
}

export function convocationUrl(id: number, download = false): string {
  return apiUrl(`/appointments/${id}/convocation.pdf${download ? '?download=true' : ''}`);
}

export function filenameFrom(header: string | null, id: number): string {
  const found = header?.match(/filename="?([^";]+)"?/)?.[1];
  // on ne garde que le nom de base pour éviter un chemin du type ../../x
  const baseName = found?.split(/[\\/]/).pop()?.trim();
  return baseName || 'convocation-' + id + '.pdf';
}

export interface ConvocationFile {
  blob: Blob;
  filename: string;
}

async function requestConvocation(id: number, download: boolean, fetcher: typeof fetch): Promise<Response> {
  try {
    return await fetcher(convocationUrl(id, download), {
      credentials: 'same-origin',
      headers: {Accept: 'application/pdf'},
    });
  } catch (e) {
    // fetch lève une TypeError quand le réseau est coupé
    if (e instanceof TypeError) throw new ConvocationError(OFFLINE);
    throw e;
  }
}

export async function fetchConvocation(
  id: number,
  download = false,
  fetcher: typeof fetch = fetch,
): Promise<ConvocationFile> {
  const r = await requestConvocation(id, download, fetcher);
  if (!r.ok) throw new ConvocationError(MESSAGES[r.status] ?? FALLBACK, r.status);
  if (!(r.headers.get('content-type') ?? '').startsWith('application/pdf'))
    throw new ConvocationError(FALLBACK, r.status);
  return {blob: await r.blob(), filename: filenameFrom(r.headers.get('content-disposition'), id)};
}

export interface Tab {
  show(url: string): void;
  close(): void;
}
export interface Browser {
  openTab(): Tab;
  createUrl(blob: Blob): string;
  revoke(url: string): void;
  save(url: string, filename: string): void;
  later(f: () => void, ms: number): void;
}

export const realBrowser: Browser = {
  openTab() {
    const w = window.open('', '_blank');
    return {
      show: (url: string) => {
        if (w) w.location.href = url;
      },
      close: () => w?.close(),
    };
  },
  createUrl: (blob: Blob) => URL.createObjectURL(blob),
  revoke: (url: string) => URL.revokeObjectURL(url),
  save(url: string, filename: string) {
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  },
  later: (f: () => void, ms: number) => {
    setTimeout(f, ms);
  },
};

export async function openConvocation(
  id: number,
  browser: Browser = realBrowser,
  fetcher: typeof fetch = fetch,
): Promise<void> {
  // l’onglet doit être ouvert tout de suite (dans le clic) : après le await, le navigateur bloquerait la popup
  const tab = browser.openTab();
  try {
    const file = await fetchConvocation(id, false, fetcher);
    const url = browser.createUrl(file.blob);
    tab.show(url);
    // on laisse le temps à l’onglet de charger le PDF avant de libérer l’URL
    browser.later(() => browser.revoke(url), REVOKE_DELAY);
  } catch (e) {
    tab.close();
    throw e;
  }
}

export async function saveConvocation(
  id: number,
  browser: Browser = realBrowser,
  fetcher: typeof fetch = fetch,
): Promise<void> {
  const file = await fetchConvocation(id, true, fetcher);
  const url = browser.createUrl(file.blob);
  browser.save(url, file.filename);
  browser.later(() => browser.revoke(url), REVOKE_DELAY);
}
