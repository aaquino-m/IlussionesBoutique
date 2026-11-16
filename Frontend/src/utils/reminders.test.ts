import { describe, expect, it } from 'vitest';
import { describeTimeUntil, getUpcomingAppointments } from './reminders';
import type { Appointment } from '../types';

const customer = { id: 'c1', name: 'Ana' } as Appointment['customer'];

function appointment(id: string, date: string, time: string, status: Appointment['status'] = 'SCHEDULED'): Appointment {
  return { id, customer, reason: 'Prueba', appointmentDate: date, appointmentTime: time, status };
}

describe('getUpcomingAppointments', () => {
  const now = new Date('2026-11-16T10:00:00');

  it('keeps scheduled appointments inside the window, soonest first', () => {
    const result = getUpcomingAppointments(
      [
        appointment('later', '2026-11-17', '09:00:00'),
        appointment('soon', '2026-11-16', '10:30:00'),
        appointment('past', '2026-11-16', '09:00:00'),
        appointment('far', '2026-11-18', '10:00:00'),
        appointment('cancelled', '2026-11-16', '11:00:00', 'CANCELLED'),
      ],
      now,
      24,
    );
    expect(result.map((item) => item.appointment.id)).toEqual(['soon', 'later']);
    expect(result[0].minutesUntil).toBe(30);
  });
});

describe('describeTimeUntil', () => {
  it('formats minutes and hours', () => {
    expect(describeTimeUntil(0)).toBe('ahora');
    expect(describeTimeUntil(45)).toBe('en 45 min');
    expect(describeTimeUntil(120)).toBe('en 2 h');
    expect(describeTimeUntil(135)).toBe('en 2 h 15 min');
  });
});
