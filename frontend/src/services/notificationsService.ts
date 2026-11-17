import {api} from '../api';
import type {Notice} from '../types';

export function loadNotifications(): Promise<Notice[]> {
  return api<Notice[]>('/notifications');
}
