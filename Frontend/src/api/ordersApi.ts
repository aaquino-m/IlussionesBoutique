import { request } from './httpClient';
import type { Order, OrderStatus } from '../types';

const BASE = '/api/order';

export interface NewOrderData {
  orderId: string;
  customerId: string;
  eventType: string;
  deliveryDate: string;
  productIds: string[];
}

export const ordersApi = {
  getAll: () => request<Order[]>(BASE),
  getByCustomer: (customerId: string) => request<Order[]>(`${BASE}/customer/${customerId}`),
  create: (data: NewOrderData) =>
    request<Order>(BASE, {
      method: 'POST',
      body: JSON.stringify({
        orderId: data.orderId,
        eventType: data.eventType,
        // Midday avoids the stored instant shifting to the previous day in UTC-6.
        deliveryDate: `${data.deliveryDate}T12:00:00`,
        customer: { id: data.customerId },
        products: data.productIds.map((productId) => ({ productId })),
      }),
    }),
  update: (id: string, data: { eventType: string; deliveryDate: string; status?: OrderStatus }) =>
    request<Order>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  remove: (id: string) => request<void>(`${BASE}/${id}`, { method: 'DELETE' }),
};