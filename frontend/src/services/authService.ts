import {api} from '../api';

export interface Registration {
  name: string;
  email: string;
  password: string;
}

export function registerAccount(account: Registration) {
  return api('/register', {method: 'POST', body: JSON.stringify(account)});
}

export function googleLoginEnabled() {
  return api<{enabled: boolean}>('/login/google').then(response => response.enabled);
}

export function nextPath(next: string | null, origin: string): string {
  try {
    const url = new URL(next || '/', origin);
    return url.origin === origin ? url.pathname + url.search + url.hash : '/';
  } catch {
    return '/';
  }
}

export function signInWithGoogle() {
  window.location.assign('/oauth2/authorization/google');
}
