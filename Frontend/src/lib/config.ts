declare const __DEFAULT_API_BASE_URL__: string | undefined;

// Injected by vite.config.ts: production API on `main`, test API elsewhere.
const DEFAULT_API_BASE_URL =
  typeof __DEFAULT_API_BASE_URL__ === 'string'
    ? __DEFAULT_API_BASE_URL__
    : 'https://ilussionesboutique-test.up.railway.app';

const configuredBaseUrl = import.meta.env.VITE_API_URL?.trim();

export const API_BASE_URL = (
  configuredBaseUrl && configuredBaseUrl.length > 0
    ? configuredBaseUrl
    : DEFAULT_API_BASE_URL
).replace(/\/+$/, '');
