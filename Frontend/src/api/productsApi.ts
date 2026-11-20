import { request } from './httpClient';
import type { Product, ProductInput } from '../types';

const BASE = '/api/product';

export const productsApi = {
  getAll: () => request<Product[]>(BASE),
  create: (data: ProductInput) =>
    request<Product>(BASE, { method: 'POST', body: JSON.stringify(data) }),
  update: (id: string, data: Partial<ProductInput>) =>
    request<Product>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  adjustStock: (id: string, delta: number) =>
    request<Product>(`${BASE}/${id}/stock`, { method: 'PATCH', body: JSON.stringify({ delta }) }),
  remove: (id: string) => request<void>(`${BASE}/${id}`, { method: 'DELETE' }),
};
