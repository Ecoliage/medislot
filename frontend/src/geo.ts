export type GeoReason =
  | 'insecure'
  | 'unsupported'
  | 'denied'
  | 'unavailable'
  | 'timeout'
  | 'not-found'
  | 'rate-limited'
  | 'network';

const TYPE_ADDRESS = 'saisissez une adresse ou un code postal';

export const GEO_MESSAGES: Record<GeoReason, string> = {
  insecure:
    'La localisation exige une connexion sécurisée (HTTPS). Saisissez une adresse ou un code postal à la place.',
  unsupported: 'Votre navigateur ne propose pas la géolocalisation. Saisissez une adresse ou un code postal.',
  denied: `Localisation refusée. Autorisez-la dans votre navigateur ou ${TYPE_ADDRESS}.`,
  unavailable: `Votre position est indisponible pour le moment. Réessayez ou ${TYPE_ADDRESS}.`,
  timeout: `La localisation a pris trop de temps. Réessayez ou ${TYPE_ADDRESS}.`,
  'not-found': 'Adresse introuvable. Précisez-la (numéro, rue, ville) ou saisissez un code postal.',
  'rate-limited': 'Le service d’adresses est saturé. Patientez quelques secondes puis réessayez.',
  network: 'Connexion impossible. Vérifiez le réseau puis réessayez.',
};

export class GeoError extends Error {
  reason: GeoReason;
  constructor(reason: GeoReason) {
    super(GEO_MESSAGES[reason]);
    this.reason = reason;
  }
}

export interface Coordinates {
  lat: number;
  lon: number;
}
export interface Place extends Coordinates {
  label: string;
}

export interface GeoEnvironment {
  isSecureContext: boolean;
  geolocation?: Pick<Geolocation, 'getCurrentPosition'> | null;
}

export interface NearbyOptions extends Coordinates {
  radiusKm?: number;
  specialty?: string;
  page?: number;
  size?: number;
}

export const POSITION_OPTIONS: PositionOptions = {enableHighAccuracy: false, timeout: 10000, maximumAge: 0};

// 3 décimales (~100 m) : assez précis pour trier par distance, sans envoyer la position exacte de l’utilisateur
export function roundCoord(value: number): number {
  return Math.round(value * 1000) / 1000;
}

export function getPosition(env?: GeoEnvironment): Promise<Coordinates> {
  const e = env ?? {isSecureContext: window.isSecureContext, geolocation: navigator.geolocation};
  if (!e.isSecureContext) return Promise.reject(new GeoError('insecure'));
  const geoloc = e.geolocation;
  if (!geoloc) return Promise.reject(new GeoError('unsupported'));
  return new Promise((resolve, reject) => {
    geoloc.getCurrentPosition(
      pos => resolve({lat: roundCoord(pos.coords.latitude), lon: roundCoord(pos.coords.longitude)}),
      err => {
        if (err.code === 1) reject(new GeoError('denied'));
        else if (err.code === 3) reject(new GeoError('timeout'));
        else reject(new GeoError('unavailable'));
      },
      POSITION_OPTIONS,
    );
  });
}

export function nearbyParams(o: NearbyOptions): URLSearchParams {
  const params = new URLSearchParams();
  params.set('lat', String(roundCoord(o.lat)));
  params.set('lon', String(roundCoord(o.lon)));
  params.set('radiusKm', String(o.radiusKm ?? 5));
  if (o.specialty) params.set('specialty', o.specialty);
  params.set('page', String(o.page ?? 0));
  params.set('size', String(o.size ?? 6));
  return params;
}

export function formatDistance(km: number): string {
  if (km < 1) return Math.round(km * 1000) + ' m';
  return km.toLocaleString('fr-FR', {maximumFractionDigits: 1}) + ' km';
}

const GEOCODE_URL = 'https://data.geopf.fr/geocodage/search';
const MIN_SCORE = 0.4;

interface Feature {
  geometry: {coordinates: [number, number]};
  properties: {score: number; label: string};
}

const cache = new Map<string, Promise<Place>>();

export function clearGeocodeCache(): void {
  cache.clear();
}

async function fetchPlace(query: string, fetcher: typeof fetch): Promise<Place> {
  let res: Response;
  try {
    res = await fetcher(GEOCODE_URL + '?' + new URLSearchParams({q: query, limit: '1'}), {
      credentials: 'omit',
      headers: {Accept: 'application/json'},
    });
  } catch {
    throw new GeoError('network');
  }
  if (res.status === 429) throw new GeoError('rate-limited');
  if (!res.ok) throw new GeoError('network');
  const data = (await res.json()) as {features?: Feature[]};
  const best = data.features?.[0];
  if (!best || best.properties.score < MIN_SCORE) throw new GeoError('not-found');
  // GeoJSON donne [longitude, latitude], l’inverse de l’usage habituel
  const [lon, lat] = best.geometry.coordinates;
  return {lat: roundCoord(lat), lon: roundCoord(lon), label: best.properties.label};
}

export function geocode(address: string, fetcher: typeof fetch = (...args) => fetch(...args)): Promise<Place> {
  const key = address.trim().toLowerCase().replace(/\s+/g, ' ');
  if (key.length < 3) return Promise.reject(new GeoError('not-found'));
  let pending = cache.get(key);
  if (!pending) {
    pending = fetchPlace(key, fetcher);
    cache.set(key, pending);
    // une promesse rejetée ne doit pas rester en cache, sinon on ne pourrait plus réessayer
    pending.catch(() => cache.delete(key));
  }
  return pending;
}
