import {beforeEach, describe, expect, it, vi} from 'vitest';
import {createApp, nextTick} from 'vue';
import ClinicForm from '../../../../src/components/admin/ClinicForm.vue';
import type {ClinicDetails} from '../../../../src/types';

const mocks = vi.hoisted(() => ({
  createClinic: vi.fn(),
  updateClinic: vi.fn(),
  loadClinic: vi.fn(),
  uploadClinicLogo: vi.fn(),
}));
vi.mock('../../../../src/services/adminService', () => ({
  ...mocks,
  clinicLogoUrl: (slug: string) => `/api/clinics/${slug}/logo`,
}));

const details: ClinicDetails = {
  slug: 'part-dieu', name: 'Cabinet de la Part-Dieu', city: 'Lyon', address: '12 rue Servient', color: '#0b6bcb', hasLogo: false,
};

const settle = async () => {
  for (let i = 0; i < 4; i++) await Promise.resolve();
  await nextTick();
};

function mountForm(slug?: string) {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const onSaved = vi.fn();
  const onCancel = vi.fn();
  createApp(ClinicForm, {slug, onSaved, onCancel}).mount(host);
  const field = (placeholder: string) => host.querySelector<HTMLInputElement>(`input[placeholder="${placeholder}"]`)!;
  const type = (input: HTMLInputElement, value: string) => {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const submit = () => host.querySelector('form')!.dispatchEvent(new Event('submit'));
  return {host, onSaved, onCancel, field, type, submit};
}

function chooseLogo(host: HTMLElement, file: File) {
  const input = host.querySelector<HTMLInputElement>('input[type="file"]')!;
  Object.defineProperty(input, 'files', {value: [file], configurable: true});
  input.dispatchEvent(new Event('change'));
}

beforeEach(() => {
  vi.resetAllMocks();
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});

describe('ClinicForm', () => {
  it('crée le cabinet, envoie le logo puis signale la réussite', async () => {
    mocks.createClinic.mockResolvedValue(details);
    mocks.uploadClinicLogo.mockResolvedValue({...details, hasLogo: true});
    const {host, field, type, submit, onSaved} = mountForm();

    type(field('Cabinet de la Part-Dieu'), 'Cabinet de la Part-Dieu');
    await nextTick();
    expect(field('part-dieu').value).toBe('cabinet-de-la-part-dieu');
    type(field('part-dieu'), 'part-dieu');
    type(field('Lyon'), 'Lyon');
    type(field('12 rue Servient'), ' 12 rue Servient ');
    type(field('admin@part-dieu.test'), 'autre@medislot.test');
    type(host.querySelector<HTMLInputElement>('.hex')!, '#0B6BCB');
    const logo = new File(['<svg/>'], 'logo.svg', {type: 'image/svg+xml'});
    chooseLogo(host, logo);
    submit();
    await settle();

    expect(mocks.createClinic).toHaveBeenCalledWith({
      name: 'Cabinet de la Part-Dieu', slug: 'part-dieu', city: 'Lyon', address: '12 rue Servient',
      color: '#0b6bcb', adminEmail: 'autre@medislot.test',
    });
    expect(mocks.uploadClinicLogo).toHaveBeenCalledWith('part-dieu', logo);
    expect(onSaved).toHaveBeenCalledWith('part-dieu');
  });

  it('refuse un logo trop lourd ou d’un autre type sans rien envoyer', async () => {
    const {host} = mountForm();
    chooseLogo(host, new File(['x'], 'photo.jpg', {type: 'image/jpeg'}));
    await nextTick();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('Le logo doit être un PNG ou un SVG.');
    chooseLogo(host, new File([new Uint8Array(200 * 1024 + 1)], 'gros.png', {type: 'image/png'}));
    await nextTick();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('Le logo dépasse 200 Ko.');
    expect(host.querySelector('.file-name')?.textContent).toBe('Choisir un fichier…');
  });

  it('en modification : pré-remplit, fige l’identifiant, masque l’e-mail et enregistre', async () => {
    mocks.loadClinic.mockResolvedValue({...details, hasLogo: true});
    mocks.updateClinic.mockResolvedValue(details);
    const {host, field, type, submit, onSaved} = mountForm('part-dieu');
    await settle();

    expect(field('Cabinet de la Part-Dieu').value).toBe('Cabinet de la Part-Dieu');
    expect(field('part-dieu').disabled).toBe(true);
    expect(field('admin@part-dieu.test')).toBeNull();
    expect(host.querySelector<HTMLImageElement>('.logo-preview')?.getAttribute('src')).toBe('/api/clinics/part-dieu/logo');
    type(field('Lyon'), 'Villeurbanne');
    submit();
    await settle();

    expect(mocks.updateClinic).toHaveBeenCalledWith('part-dieu', {
      name: 'Cabinet de la Part-Dieu', city: 'Villeurbanne', address: '12 rue Servient', color: '#0b6bcb',
    });
    expect(mocks.uploadClinicLogo).not.toHaveBeenCalled();
    expect(onSaved).toHaveBeenCalledWith('part-dieu');
  });

  it('garde le formulaire ouvert si le logo échoue après la création', async () => {
    mocks.createClinic.mockResolvedValue(details);
    mocks.uploadClinicLogo.mockRejectedValue(new Error('Le logo dépasse 200 Ko.'));
    const {host, field, type, submit, onSaved} = mountForm();
    type(field('Cabinet de la Part-Dieu'), 'Cabinet de la Part-Dieu');
    type(field('Lyon'), 'Lyon');
    type(field('admin@part-dieu.test'), 'autre@medislot.test');
    chooseLogo(host, new File(['<svg/>'], 'logo.svg', {type: 'image/svg+xml'}));
    submit();
    await settle();

    expect(onSaved).not.toHaveBeenCalled();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe(
      'Le cabinet est créé, mais le logo n’a pas été enregistré : Le logo dépasse 200 Ko.',
    );
    expect(host.querySelector('h2')?.textContent).toBe('Modifier le cabinet');
  });
});
