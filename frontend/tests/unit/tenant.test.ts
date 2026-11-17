import {describe, expect, it} from 'vitest';
import {
  DEFAULT_CLINIC,
  DEFAULT_COLOR,
  clinicFromHost,
  clinicFromLocation,
  clinicFromPath,
  clinicPath,
  safeColor,
  validSlug,
} from '../../src/tenant';

describe('cabinet courant : lecture de l’adresse', () => {
  it('lit le cabinet dans le chemin /c/<cabinet>/', () => {
    expect(clinicFromPath('/c/cabinet-a/')).toBe('cabinet-a');
    expect(clinicFromPath('/c/cabinet-b')).toBe('cabinet-b');
    expect(clinicFromPath('/c/cabinet-b/rendez-vous')).toBe('cabinet-b');
  });

  it('refuse un chemin sans cabinet ou avec un cabinet mal formé', () => {
    expect(clinicFromPath('/')).toBeNull();
    expect(clinicFromPath('/cabinet-a/')).toBeNull();
    expect(clinicFromPath('/x/c/cabinet-a/')).toBeNull();
    expect(clinicFromPath('/c/Cabinet-A/')).toBeNull();
    expect(clinicFromPath('/c/cabinet_a/')).toBeNull();
    expect(clinicFromPath('/c/%2e%2e/')).toBeNull();
    expect(clinicFromPath('/c/' + 'a'.repeat(41) + '/')).toBeNull();
  });

  it('lit le cabinet dans le sous-domaine de medislot.test', () => {
    expect(clinicFromHost('cabinet-a.medislot.test')).toBe('cabinet-a');
    expect(clinicFromHost('cabinet-b.medislot.test:8088')).toBe('cabinet-b');
    expect(clinicFromHost('CABINET-A.MEDISLOT.TEST')).toBe('cabinet-a');
  });

  it('teste la fin du nom d’hôte, pas sa présence quelque part', () => {
    expect(clinicFromHost('cabinet-a.medislot.test.pirate.example')).toBeNull();
    expect(clinicFromHost('cabinet-a.medislot.test.pirate.example:8088')).toBeNull();
    expect(clinicFromHost('pirate.example')).toBeNull();
    expect(clinicFromHost('medislot.test')).toBeNull();
    expect(clinicFromHost('cabinet-amedislot.test')).toBeNull();
    expect(clinicFromHost('a.b.medislot.test')).toBeNull();
    expect(clinicFromHost('localhost:8088')).toBeNull();
  });

  it('préfère le chemin, puis l’hôte, puis le cabinet par défaut', () => {
    expect(clinicFromLocation({pathname: '/c/cabinet-b/', host: 'cabinet-a.medislot.test'})).toBe('cabinet-b');
    expect(clinicFromLocation({pathname: '/', host: 'cabinet-b.medislot.test'})).toBe('cabinet-b');
    expect(clinicFromLocation({pathname: '/', host: 'localhost:8088'})).toBe(DEFAULT_CLINIC);
  });

  it('construit le chemin d’un cabinet', () => {
    expect(clinicPath('cabinet-b')).toBe('/c/cabinet-b/');
  });

  it('valide un slug', () => {
    expect(validSlug('cabinet-a')).toBe('cabinet-a');
    expect(validSlug('')).toBeNull();
    expect(validSlug(null)).toBeNull();
    expect(validSlug('-a')).toBeNull();
    expect(validSlug('a--b')).toBeNull();
  });
});

describe('couleur du cabinet', () => {
  it('garde une couleur hexadécimale à six chiffres, en minuscules', () => {
    expect(safeColor('#0b7a5c')).toBe('#0b7a5c');
    expect(safeColor('#0B7A5C')).toBe('#0b7a5c');
  });

  it('remplace toute autre valeur par la couleur par défaut', () => {
    for (const value of ['red', '#fff', '#0b7a5c;background:url(//pirate.example)', 'url(x)', '', null, undefined, 12]) {
      expect(safeColor(value)).toBe(DEFAULT_COLOR);
    }
  });
});
