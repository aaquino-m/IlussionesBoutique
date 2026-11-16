import { request } from './httpClient';
import type { CalendarProvider, CalendarProviderStatus } from '../types';

const BASE = '/api/calendar';

const path = (provider: CalendarProvider) => provider.toLowerCase();

export const calendarApi = {
  status: () => request<CalendarProviderStatus[]>(`${BASE}/status`),
  authorizeUrl: (provider: CalendarProvider) =>
    request<{ url: string }>(`${BASE}/${path(provider)}/authorize`),
  disconnect: (provider: CalendarProvider) =>
    request<void>(`${BASE}/${path(provider)}`, { method: 'DELETE' }),
  sync: () => request<{ synced: number }>(`${BASE}/sync`, { method: 'POST' }),
};
