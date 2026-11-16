import { request } from './httpClient';
import type { AuthUser } from '../types';

export interface LoginResult {
  token: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

/**
 * El backend es la única autoridad de autenticación. Aquí solo se envían las
 * credenciales y se devuelve el token junto con los datos públicos del usuario.
 */
export const authApi = {
  login: (email: string, password: string) =>
    request<LoginResult>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
  forgotPassword: (email: string) =>
    request<{ status: string }>('/api/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email }),
    }),
  resetPassword: (token: string, newPassword: string) =>
    request<void>('/api/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ token, newPassword }),
    }),
};
