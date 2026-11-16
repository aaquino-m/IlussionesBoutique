import { useCallback, useEffect, useState } from 'react';
import { calendarApi } from '../../api/calendarApi';
import type { CalendarProvider, CalendarProviderStatus } from '../../types';

const LABELS: Record<CalendarProvider, { name: string; icon: string }> = {
  GOOGLE: { name: 'Google Calendar', icon: '📆' },
  MICROSOFT: { name: 'Microsoft Outlook', icon: '🗓️' },
};

function readOAuthResult(): string {
  const params = new URLSearchParams(window.location.search);
  const result = params.get('calendar');
  if (!result) return '';
  const provider = (params.get('provider') ?? '').toUpperCase() as CalendarProvider;
  const name = LABELS[provider]?.name ?? 'el calendario';
  window.history.replaceState(null, '', window.location.pathname);
  return result === 'connected' ? `Conectado con ${name}.` : `No se pudo conectar con ${name}.`;
}

export function CalendarSettings() {
  const [providers, setProviders] = useState<CalendarProviderStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState(readOAuthResult);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setProviders(await calendarApi.status());
      setError('');
    } catch {
      setError('No se pudo cargar el estado de los calendarios.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    calendarApi
      .status()
      .then((result) => {
        if (active) setProviders(result);
      })
      .catch(() => {
        if (active) setError('No se pudo cargar el estado de los calendarios.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const connect = async (provider: CalendarProvider) => {
    setBusy(true);
    try {
      const { url } = await calendarApi.authorizeUrl(provider);
      window.location.assign(url);
    } catch {
      setError('No se pudo iniciar la conexión.');
      setBusy(false);
    }
  };

  const disconnect = async (provider: CalendarProvider) => {
    setBusy(true);
    try {
      await calendarApi.disconnect(provider);
      setMessage(`${LABELS[provider].name} desconectado.`);
      await load();
    } catch {
      setError('No se pudo desconectar.');
    } finally {
      setBusy(false);
    }
  };

  const sync = async () => {
    setBusy(true);
    try {
      const { synced } = await calendarApi.sync();
      setMessage(`${synced} cita(s) sincronizadas.`);
    } catch {
      setError('No se pudieron sincronizar las citas.');
    } finally {
      setBusy(false);
    }
  };

  const anyConnected = providers.some((p) => p.connected);

  return (
    <section className="w-full space-y-3">
      <div>
        <h2 className="text-sm sm:text-base font-bold text-gray-800">🔗 Calendarios</h2>
        <p className="text-xs text-gray-500">
          Las citas nuevas, editadas o canceladas se reflejan automáticamente en los calendarios conectados.
        </p>
      </div>

      {message && <p className="bg-green-50 border border-green-200 text-green-700 text-xs font-semibold p-2.5 rounded-xl">{message}</p>}
      {error && <p role="alert" className="bg-red-50 border border-red-200 text-red-600 text-xs font-semibold p-2.5 rounded-xl">{error}</p>}

      {loading ? (
        <p className="text-xs text-gray-400">Cargando...</p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {providers.map((status) => (
            <div key={status.provider} className="bg-white border border-gray-100 rounded-2xl p-4 shadow-xs space-y-2">
              <div className="flex items-center gap-2">
                <span className="text-2xl">{LABELS[status.provider].icon}</span>
                <div>
                  <p className="font-bold text-sm text-gray-800">{LABELS[status.provider].name}</p>
                  <p className="text-xs text-gray-500">
                    {!status.configured
                      ? 'No configurado en el servidor'
                      : status.connected
                        ? `Conectado${status.accountEmail ? ` como ${status.accountEmail}` : ''}`
                        : 'Sin conectar'}
                  </p>
                </div>
              </div>
              {status.configured && (
                status.connected ? (
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => disconnect(status.provider)}
                    className="w-full py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-xl text-xs font-bold cursor-pointer disabled:opacity-50"
                  >
                    Desconectar
                  </button>
                ) : (
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => connect(status.provider)}
                    className="w-full py-2 bg-pink-600 hover:bg-pink-700 text-white rounded-xl text-xs font-bold cursor-pointer disabled:opacity-50"
                  >
                    Conectar
                  </button>
                )
              )}
            </div>
          ))}
        </div>
      )}

      {anyConnected && (
        <button
          type="button"
          disabled={busy}
          onClick={sync}
          className="py-2 px-4 bg-pink-50 hover:bg-pink-100 text-pink-700 rounded-xl text-xs font-bold cursor-pointer disabled:opacity-50"
        >
          🔄 Sincronizar citas próximas
        </button>
      )}
    </section>
  );
}

export default CalendarSettings;
