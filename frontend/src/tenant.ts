export const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
export const SLUG_MAX = 40;
export const DEFAULT_CLINIC = 'cabinet-a';
export const BASE_DOMAIN = 'medislot.test';
export const DEFAULT_COLOR = '#0873b9';

const PATH = /^\/c\/([^/]+)/;
const COLOR = /^#[0-9a-f]{6}$/i;

export function validSlug(value: string | null | undefined): string | null {
  return value && value.length <= SLUG_MAX && SLUG.test(value) ? value : null;
}

export function clinicFromPath(path: string): string | null {
  return validSlug(PATH.exec(path)?.[1]);
}

export function clinicFromHost(host: string): string | null {
  const name = host.split(':')[0].toLowerCase();
  const suffix = '.' + BASE_DOMAIN;
  if (!name.endsWith(suffix)) return null;
  return validSlug(name.slice(0, -suffix.length));
}

export function clinicFromLocation(location: {pathname: string; host: string}): string {
  return clinicFromPath(location.pathname) ?? clinicFromHost(location.host) ?? DEFAULT_CLINIC;
}

export function clinicPath(slug: string): string {
  return `/c/${slug}/`;
}

export function safeColor(color: unknown): string {
  return typeof color === 'string' && COLOR.test(color) ? color.toLowerCase() : DEFAULT_COLOR;
}
