import { API_BASE_URL } from '../lib/config';
import { authHeaders, getAuthToken, saveSession } from '../lib/authSession';
import type { AuthUser } from '../types';

export class ApiError extends Error {
  status: number;
  details?: unknown;

  constructor(status: number, message: string, details?: unknown) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

export interface SessionResult {
  token: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

const AUTH_PREFIX = '/api/auth/';

// Solo las rutas de /api/auth envían la cookie HttpOnly de sesión.
const send = (path: string, options: RequestInit) =>
  fetch(`${API_BASE_URL}${path}`, {
    ...options,
    credentials: path.startsWith(AUTH_PREFIX) ? 'include' : options.credentials,
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(),
      ...options.headers,
    },
  });

async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const details = await response.json().catch(() => undefined);
    throw new ApiError(response.status, `Error ${response.status}`, details);
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

/** Canjea la cookie de sesión por un token de acceso nuevo. */
export async function fetchSession(signal?: AbortSignal): Promise<SessionResult> {
  const result = await parse<SessionResult>(await send(`${AUTH_PREFIX}session`, { method: 'POST', signal }));
  saveSession(result.token, result.user);
  return result;
}

let refreshing: Promise<boolean> | null = null;

// Varias peticiones que caducan a la vez comparten una sola renovación.
const refreshOnce = () => {
  refreshing ??= fetchSession()
    .then(() => true)
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
};

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const hadToken = getAuthToken() !== null;
  let response = await send(path, options);

  // El token de acceso dura poco; si caduca, la cookie lo renueva sin volver a pedir login.
  if (response.status === 401 && hadToken && !path.startsWith(AUTH_PREFIX) && (await refreshOnce())) {
    response = await send(path, options);
  }
  return parse<T>(response);
}
