import { useEffect, useState } from 'react';
import type { Appointment } from '../../types';
import { describeTimeUntil, getUpcomingAppointments } from '../../utils/reminders';
import { to12h } from '../../utils/dates';

interface UpcomingAppointmentsAlertProps {
  appointments: Appointment[];
  onView: (id: string) => void;
  /** Window of the banner, in hours. */
  hours?: number;
  /** Browser notification lead time, in minutes. */
  notifyMinutesBefore?: number;
}

const NOTIFIED_KEY = 'ilussiones.notifiedAppointments';

function loadNotified(): Set<string> {
  try {
    return new Set(JSON.parse(sessionStorage.getItem(NOTIFIED_KEY) ?? '[]') as string[]);
  } catch {
    return new Set();
  }
}

const notificationsSupported = () => typeof window !== 'undefined' && 'Notification' in window;

export function UpcomingAppointmentsAlert({
  appointments,
  onView,
  hours = 24,
  notifyMinutesBefore = 30,
}: UpcomingAppointmentsAlertProps) {
  const [now, setNow] = useState(() => new Date());
  const [permission, setPermission] = useState<NotificationPermission | 'unsupported'>(() =>
    notificationsSupported() ? Notification.permission : 'unsupported',
  );

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  const upcoming = getUpcomingAppointments(appointments, now, hours);

  useEffect(() => {
    if (permission !== 'granted') return;
    const notified = loadNotified();
    let changed = false;
    for (const { appointment, minutesUntil, start } of upcoming) {
      if (minutesUntil > notifyMinutesBefore || notified.has(appointment.id)) continue;
      new Notification('Cita próxima - Ilussiones Boutique', {
        body: `${appointment.customer.name}: ${appointment.reason} a las ${start.toLocaleTimeString('es-SV', { hour: '2-digit', minute: '2-digit' })}`,
        tag: `appointment-${appointment.id}`,
      });
      notified.add(appointment.id);
      changed = true;
    }
    if (changed) sessionStorage.setItem(NOTIFIED_KEY, JSON.stringify([...notified]));
  }, [upcoming, permission, notifyMinutesBefore]);

  if (upcoming.length === 0) return null;

  const requestPermission = async () => {
    if (!notificationsSupported()) return;
    setPermission(await Notification.requestPermission());
  };

  return (
    <section
      aria-label="Recordatorios de citas"
      className="bg-amber-50 border-l-4 border-amber-400 p-4 rounded-r-xl shadow-xs w-full space-y-2"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-start">
          <span className="text-amber-500 text-lg sm:text-xl mr-3">⏰</span>
          <div>
            <h3 className="text-amber-800 font-bold text-xs sm:text-sm">
              {upcoming.length === 1 ? 'Tienes 1 cita pronto' : `Tienes ${upcoming.length} citas pronto`}
            </h3>
            <p className="text-amber-700 text-xs">Próximas {hours} horas</p>
          </div>
        </div>
        {permission === 'default' && (
          <button
            type="button"
            onClick={requestPermission}
            className="text-xs font-bold text-amber-800 hover:underline cursor-pointer whitespace-nowrap"
          >
            🔔 Activar avisos
          </button>
        )}
      </div>
      <ul className="divide-y divide-amber-100">
        {upcoming.slice(0, 5).map(({ appointment, minutesUntil }) => {
          const { time, period } = to12h(appointment.appointmentTime);
          return (
            <li key={appointment.id} className="py-1.5 flex items-center justify-between gap-2 text-xs">
              <span className="text-amber-900">
                <strong>{time} {period}</strong> · {appointment.customer.name} · {appointment.reason}
                <span className="ml-1 text-amber-600">({describeTimeUntil(minutesUntil)})</span>
              </span>
              <button
                type="button"
                onClick={() => onView(appointment.id)}
                className="text-amber-800 font-bold hover:underline cursor-pointer whitespace-nowrap"
              >
                Ver
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

export default UpcomingAppointmentsAlert;
