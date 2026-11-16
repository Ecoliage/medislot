import {describe, expect, it, vi} from 'vitest';
import {createApp, nextTick} from 'vue';
import NearbyBar from './components/NearbyBar.vue';
import {GEO_MESSAGES, GeoError, type Coordinates, type Place} from './geo';

type Locate = () => Promise<Coordinates>;
type Geocoder = (address: string) => Promise<Place>;

function mountBar(locate: Locate, geocoder: Geocoder = vi.fn()) {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const onPosition = vi.fn();
  createApp(NearbyBar, {locate, geocoder, onPosition}).mount(host);
  const locateButton = () => host.querySelector<HTMLButtonElement>('.nearby-locate')!;
  return {host, onPosition, locateButton};
}

const settle = async () => {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
};

describe('NearbyBar', () => {
  it('ne demande rien au montage', () => {
    const locate = vi.fn();
    const geocoder = vi.fn();
    mountBar(locate, geocoder);
    expect(locate).not.toHaveBeenCalled();
    expect(geocoder).not.toHaveBeenCalled();
  });

  it('émet la position au clic', async () => {
    const locate = vi.fn().mockResolvedValue({lat: 45.764, lon: 4.836});
    const {locateButton, onPosition} = mountBar(locate);
    locateButton().click();
    await nextTick();
    expect(locateButton().disabled).toBe(true);
    expect(locateButton().textContent).toContain('Localisation');
    await settle();
    expect(locate).toHaveBeenCalledTimes(1);
    expect(onPosition).toHaveBeenCalledWith({lat: 45.764, lon: 4.836, label: 'votre position'});
    expect(locateButton().disabled).toBe(false);
  });

  it('affiche le message de refus', async () => {
    const locate = vi.fn().mockRejectedValue(new GeoError('denied'));
    const {host, locateButton, onPosition} = mountBar(locate);
    locateButton().click();
    await settle();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe(GEO_MESSAGES.denied);
    expect(onPosition).not.toHaveBeenCalled();
  });

  it('géocode l’adresse saisie', async () => {
    const geocoder = vi.fn().mockResolvedValue({lat: 45.75, lon: 4.826, label: 'Lyon 2e Arrondissement'});
    const {host, onPosition} = mountBar(vi.fn(), geocoder);
    const input = host.querySelector<HTMLInputElement>('input')!;
    input.value = '69002';
    input.dispatchEvent(new Event('input'));
    host.querySelector('form')!.dispatchEvent(new Event('submit', {cancelable: true}));
    await settle();
    expect(geocoder).toHaveBeenCalledWith('69002');
    expect(onPosition).toHaveBeenCalledWith({lat: 45.75, lon: 4.826, label: 'Lyon 2e Arrondissement'});
  });
});
