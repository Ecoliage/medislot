import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
  GEO_MESSAGES,
  GeoError,
  POSITION_OPTIONS,
  clearGeocodeCache,
  formatDistance,
  geocode,
  getPosition,
  nearbyParams,
  type GeoEnvironment,
} from './geo';

type OnSuccess = (position: GeolocationPosition) => void;
type OnError = (error: GeolocationPositionError) => void;

const position = (latitude: number, longitude: number) => ({coords: {latitude, longitude}}) as GeolocationPosition;

function fakeEnv(
  behaviour: (onSuccess: OnSuccess, onError: OnError) => void,
): GeoEnvironment & {getCurrentPosition: ReturnType<typeof vi.fn>} {
  const getCurrentPosition = vi.fn(behaviour);
  return {isSecureContext: true, geolocation: {getCurrentPosition}, getCurrentPosition};
}

const failWith = (code: number) => fakeEnv((_, onError) => onError({code} as GeolocationPositionError));

const reasonOf = async (promise: Promise<unknown>) => {
  try {
    await promise;
  } catch (e) {
    return e instanceof GeoError ? e.reason : 'other';
  }
  return 'no error';
};

describe('getPosition', () => {
  it('renvoie la position arrondie', async () => {
    const env = fakeEnv(onSuccess => onSuccess(position(45.7640123, 4.8356789)));
    await expect(getPosition(env)).resolves.toEqual({lat: 45.764, lon: 4.836});
    expect(env.getCurrentPosition).toHaveBeenCalledWith(expect.any(Function), expect.any(Function), POSITION_OPTIONS);
    expect(POSITION_OPTIONS).toEqual({enableHighAccuracy: false, timeout: 10000, maximumAge: 0});
  });

  it('refuse sans HTTPS ou sans géolocalisation', async () => {
    const env = {...fakeEnv(() => {}), isSecureContext: false};
    expect(await reasonOf(getPosition(env))).toBe('insecure');
    expect(env.getCurrentPosition).not.toHaveBeenCalled();
    expect(await reasonOf(getPosition({isSecureContext: true, geolocation: undefined}))).toBe('unsupported');
  });

  it('traduit les erreurs du navigateur', async () => {
    const error = await getPosition(failWith(1)).catch(e => e);
    expect(error).toBeInstanceOf(GeoError);
    expect(error.reason).toBe('denied');
    expect(error.message).toBe(GEO_MESSAGES.denied);
    expect(await reasonOf(getPosition(failWith(2)))).toBe('unavailable');
    expect(await reasonOf(getPosition(failWith(3)))).toBe('timeout');
  });
});

describe('nearbyParams', () => {
  it('arrondit la position et remplit les valeurs par défaut', () => {
    const params = nearbyParams({lat: 45.76404321, lon: 4.83569999, specialty: 'cardiology', page: 2});
    expect(Object.fromEntries(params)).toEqual({
      lat: '45.764',
      lon: '4.836',
      radiusKm: '5',
      specialty: 'cardiology',
      page: '2',
      size: '6',
    });
  });

  it('omet la spécialité vide', () => {
    expect(nearbyParams({lat: 45.7, lon: 4.8}).has('specialty')).toBe(false);
    expect(nearbyParams({lat: 45.7, lon: 4.8, specialty: ''}).has('specialty')).toBe(false);
  });
});

describe('formatDistance', () => {
  it('affiche les mètres sous 1 km, sinon les kilomètres', () => {
    expect(formatDistance(0.3)).toBe('300 m');
    expect(formatDistance(0.05)).toBe('50 m');
    expect(formatDistance(1.2)).toBe('1,2 km');
    expect(formatDistance(12)).toBe('12 km');
    expect(formatDistance(4.96)).toBe('5 km');
  });
});

describe('geocode', () => {
  beforeEach(() => clearGeocodeCache());

  const lyon2 = () =>
    new Response(
      JSON.stringify({
        type: 'FeatureCollection',
        features: [
          {
            type: 'Feature',
            geometry: {type: 'Point', coordinates: [4.826148, 45.750351]},
            properties: {label: 'Lyon 2e Arrondissement', score: 0.86, postcode: '69002'},
          },
        ],
        query: '69002',
      }),
      {status: 200},
    );

  it('lit les coordonnées GeoJSON [lon, lat]', async () => {
    const fetcher = vi.fn().mockResolvedValue(lyon2());
    const place = await geocode('69002', fetcher);
    expect(place).toEqual({lat: 45.75, lon: 4.826, label: 'Lyon 2e Arrondissement'});
    const [url, init] = fetcher.mock.calls[0];
    expect(url).toBe('https://data.geopf.fr/geocodage/search?q=69002&limit=1');
    expect(init.credentials).toBe('omit');
  });

  it('met en cache les saisies équivalentes', async () => {
    const fetcher = vi.fn().mockImplementation(async () => lyon2());
    const results = await Promise.all([geocode('  Place   Bellecour ', fetcher), geocode('place bellecour', fetcher)]);
    await geocode('PLACE BELLECOUR', fetcher);
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(results[0]).toBe(results[1]);
  });

  it('gère les erreurs sans garder l’échec en cache', async () => {
    const empty = () => new Response(JSON.stringify({features: []}), {status: 200});
    expect(await reasonOf(geocode('zzzzzz', vi.fn().mockResolvedValue(empty())))).toBe('not-found');
    const weak = new Response(
      JSON.stringify({features: [{geometry: {coordinates: [1, 2]}, properties: {label: 'x', score: 0.2}}]}),
    );
    expect(await reasonOf(geocode('qsdfgh', vi.fn().mockResolvedValue(weak)))).toBe('not-found');
    expect(await reasonOf(geocode('ab', vi.fn()))).toBe('not-found');
    const saturated = vi.fn().mockResolvedValue(new Response('', {status: 429}));
    expect(await reasonOf(geocode('rue de la paix', saturated))).toBe('rate-limited');
    const recovered = vi.fn().mockResolvedValue(lyon2());
    await expect(geocode('rue de la paix', recovered)).resolves.toMatchObject({label: 'Lyon 2e Arrondissement'});
    expect(await reasonOf(geocode('quai Saint-Antoine', vi.fn().mockRejectedValue(new TypeError('offline'))))).toBe(
      'network',
    );
  });
});
