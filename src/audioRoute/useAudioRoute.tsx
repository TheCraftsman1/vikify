import React, { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { AudioRouteDevice, AudioRouteService, SetRouteOptions } from './AudioRouteService';
import { audioRouteService as defaultService, formatOutputLabel } from './AudioRouteService';

export type AudioRouteState = {
  isSupported: boolean;
  current: AudioRouteDevice | null;
  available: AudioRouteDevice[];
  label: string;
  isLoading: boolean;
  lastError: string | null;

  refresh: () => Promise<void>;
  setRoute: (routeId: string, options?: SetRouteOptions) => Promise<void>;
};

const AudioRouteContext = createContext<AudioRouteState | null>(null);

export function AudioRouteProvider({
  children,
  service,
}: {
  children: React.ReactNode;
  service?: AudioRouteService;
}) {
  const svc = service ?? defaultService;

  const [isSupported, setIsSupported] = useState(false);
  const [current, setCurrent] = useState<AudioRouteDevice | null>(null);
  const [available, setAvailable] = useState<AudioRouteDevice[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [lastError, setLastError] = useState<string | null>(null);

  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const refresh = async () => {
    setIsLoading(true);
    setLastError(null);
    try {
      const supported = await svc.isSupported();
      const snap = await svc.getSnapshot();
      if (!mounted.current) return;
      setIsSupported(supported);
      setCurrent(snap.current);
      setAvailable(snap.available);
    } catch (e: any) {
      if (!mounted.current) return;
      setLastError(e?.message ?? String(e));
    } finally {
      if (mounted.current) setIsLoading(false);
    }
  };

  const setRoute = async (routeId: string, options: SetRouteOptions = {}) => {
    setLastError(null);
    try {
      await svc.setRoute(routeId, options);
    } catch (e: any) {
      setLastError(e?.message ?? String(e));
      throw e;
    }
  };

  useEffect(() => {
    const unsub = svc.subscribe(({ current: c, available: a }) => {
      setCurrent(c);
      setAvailable(a);
    });

    void refresh();
    return () => {
      unsub();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [svc]);

  const label = useMemo(() => {
    return current ? formatOutputLabel(current) : '🔊 Phone Speaker';
  }, [current]);

  const value = useMemo<AudioRouteState>(
    () => ({
      isSupported,
      current,
      available,
      label,
      isLoading,
      lastError,
      refresh,
      setRoute,
    }),
    [isSupported, current, available, label, isLoading, lastError]
  );

  return <AudioRouteContext.Provider value={value}>{children}</AudioRouteContext.Provider>;
}

export function useAudioRoute(): AudioRouteState {
  const ctx = useContext(AudioRouteContext);
  if (!ctx) {
    throw new Error('useAudioRoute must be used within <AudioRouteProvider>.');
  }
  return ctx;
}
