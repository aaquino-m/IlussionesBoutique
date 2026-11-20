import type { Appointment } from '../types';

export interface UpcomingAppointment {
  appointment: Appointment;
  start: Date;
  minutesUntil: number;
}

export function appointmentStart(appointment: Appointment): Date {
  return new Date(`${appointment.appointmentDate}T${appointment.appointmentTime.slice(0, 8)}`);
}

/** Scheduled appointments that start between `now` and `now + hours`, soonest first. */
export function getUpcomingAppointments(
  appointments: Appointment[],
  now: Date = new Date(),
  hours = 24,
): UpcomingAppointment[] {
  const limit = now.getTime() + hours * 3_600_000;
  return appointments
    .filter((appointment) => appointment.status === 'SCHEDULED')
    .map((appointment) => {
      const start = appointmentStart(appointment);
      return {
        appointment,
        start,
        minutesUntil: Math.round((start.getTime() - now.getTime()) / 60_000),
      };
    })
    .filter(({ start }) => start.getTime() >= now.getTime() && start.getTime() <= limit)
    .sort((a, b) => a.start.getTime() - b.start.getTime());
}

export function describeTimeUntil(minutes: number): string {
  if (minutes < 1) return 'ahora';
  if (minutes < 60) return `en ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `en ${hours} h` : `en ${hours} h ${rest} min`;
}
