import {api} from '../api';

export interface Registration {
  name: string;
  email: string;
  password: string;
}

export function registerAccount(account: Registration) {
  return api('/register', {method: 'POST', body: JSON.stringify(account)});
}
