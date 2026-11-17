import {describe, expect, it} from 'vitest';
import {nextPath} from '../../../src/services/authService';

const origin = 'http://localhost:8088';

describe('nextPath', () => {
  it('garde un chemin du même site avec sa requête et son ancre', () => {
    expect(nextPath('/?page=profil', origin)).toBe('/?page=profil');
    expect(nextPath('/espace?a=1#b', origin)).toBe('/espace?a=1#b');
  });

  it('revient à l’accueil sans paramètre', () => {
    expect(nextPath(null, origin)).toBe('/');
    expect(nextPath('', origin)).toBe('/');
  });

  it('refuse les adresses qui sortent du site', () => {
    expect(nextPath('//evil.example/x', origin)).toBe('/');
    expect(nextPath('/\\evil.example/x', origin)).toBe('/');
    expect(nextPath('/\t/evil.example', origin)).toBe('/');
    expect(nextPath('https://evil.example/x', origin)).toBe('/');
    expect(nextPath('javascript:alert(1)', origin)).toBe('/');
  });
});
