/*
  AudioRouteService.ts

  React Native + optional Web audio output route detection/switching.

  IMPORTANT:
  - On React Native, switching output routes and getting device names requires a small native module.
    This file expects (optionally) a native module named `AudioRouteNative` that:
      - getCurrentRoute(): Promise<AudioRouteDevice>
      - listAvailableRoutes(): Promise<AudioRouteDevice[]>
      - setRoute(routeId: string): Promise<void>
      - emits event `AudioRouteChanged` with payload AudioRouteDevice

  - On Web, output device switching is only supported in some browsers via HTMLMediaElement.setSinkId().
    Route type detection (BT vs wired) is heuristic based on device label.
*/

export type AudioRouteType = 'SPEAKER' | 'WIRED_HEADPHONES' | 'BLUETOOTH' | 'UNKNOWN';

export type AudioRouteDevice = {
  /** Stable identifier for selecting this route. */
  id: string;
  type: AudioRouteType;
  /** Human readable name; for Bluetooth should be the device name when available. */
  name: string;
  /** Whether route is currently active. */
  isActive: boolean;
  /** Whether route is currently available to select. */
  isAvailable: boolean;
  /** Whether the platform supports switching to this route programmatically. */
  isSelectable: boolean;
};

export type AudioFadeController = {
  /** Get current player volume (0..1). Optional. */
  getVolume?: () => number | Promise<number>;
  /** Set player volume (0..1). Optional. */
  setVolume?: (volume: number) => void | Promise<void>;
};

export type SetRouteOptions = {
  /** Fade out, switch, then fade in. Default: true */
  fade?: boolean;
  /** Fade duration in ms. Default: 150 */
  fadeDurationMs?: number;
  /** Optional integration with your audio player volume control. */
  fadeController?: AudioFadeController;
};

export type AudioRouteChangeEvent = {
  current: AudioRouteDevice;
  available: AudioRouteDevice[];
};

export type Unsubscribe = () => void;

type Listener = (evt: AudioRouteChangeEvent) => void;

export interface AudioRouteAdapter {
  /** True when adapter can do something meaningful on this platform. */
  isSupported(): boolean;

  getCurrentRoute(): Promise<AudioRouteDevice>;
  listAvailableRoutes(): Promise<AudioRouteDevice[]>;
  setRoute(routeId: string): Promise<void>;

  /** Subscribe to route change notifications. */
  subscribe(listener: (current: AudioRouteDevice) => void): Promise<{ remove: () => void }>;
}

function isBrowser(): boolean {
  return typeof window !== 'undefined' && typeof document !== 'undefined';
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function maybeGetVolume(controller?: AudioFadeController): Promise<number | null> {
  if (!controller?.getVolume) return null;
  try {
    const v = await controller.getVolume();
    if (typeof v !== 'number' || Number.isNaN(v)) return null;
    return Math.max(0, Math.min(1, v));
  } catch {
    return null;
  }
}

async function maybeSetVolume(controller: AudioFadeController | undefined, volume: number): Promise<void> {
  if (!controller?.setVolume) return;
  const v = Math.max(0, Math.min(1, volume));
  await controller.setVolume(v);
}

async function fadeVolume(
  controller: AudioFadeController | undefined,
  from: number,
  to: number,
  durationMs: number
): Promise<void> {
  if (!controller?.setVolume) {
    // No integration: we can only delay to avoid a harsh switch.
    await sleep(durationMs);
    return;
  }

  const steps = Math.max(3, Math.floor(durationMs / 30));
  const stepMs = Math.max(10, Math.floor(durationMs / steps));
  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    const v = from + (to - from) * t;
    await maybeSetVolume(controller, v);
    if (i < steps) await sleep(stepMs);
  }
}

export function formatOutputLabel(device: AudioRouteDevice): string {
  if (!device) return '🔊 Phone Speaker';

  if (device.type === 'SPEAKER') return '🔊 Phone Speaker';
  if (device.type === 'WIRED_HEADPHONES') return '🎧 Headphones';
  if (device.type === 'BLUETOOTH') {
    const name = (device.name || '').trim();
    return name ? `🎧 Bluetooth Device: ${name}` : '🎧 Bluetooth Device';
  }
  return device.name ? `🔊 ${device.name}` : '🔊 Output';
}

export function guessRouteTypeFromLabel(label: string): AudioRouteType {
  const s = (label || '').toLowerCase();
  if (!s) return 'UNKNOWN';
  if (s.includes('bluetooth') || s.includes('bt') || s.includes('airpods') || s.includes('buds')) return 'BLUETOOTH';
  if (s.includes('wired') || s.includes('headphone') || s.includes('headset') || s.includes('aux')) return 'WIRED_HEADPHONES';
  if (s.includes('speaker') || s.includes('phone')) return 'SPEAKER';
  return 'UNKNOWN';
}

function stableRouteId(type: AudioRouteType, name?: string): string {
  // Keep route IDs stable across refreshes.
  const n = (name || '').trim();
  return n ? `${type}:${n}` : type;
}

class WebAudioRouteAdapter implements AudioRouteAdapter {
  isSupported(): boolean {
    return isBrowser() && typeof navigator !== 'undefined' && !!(navigator as any).mediaDevices;
  }

  async getCurrentRoute(): Promise<AudioRouteDevice> {
    // Web cannot reliably tell the *current* sink without tracking setSinkId.
    // We return a safe default.
    return {
      id: 'SPEAKER',
      type: 'SPEAKER',
      name: 'Phone Speaker',
      isActive: true,
      isAvailable: true,
      isSelectable: false,
    };
  }

  async listAvailableRoutes(): Promise<AudioRouteDevice[]> {
    // Web route enumeration is limited; enumerateDevices returns outputs but labels may be blank
    // until the user grants media permissions.
    const mediaDevices = (navigator as any).mediaDevices as MediaDevices | undefined;
    if (!mediaDevices?.enumerateDevices) {
      return [
        {
          id: 'SPEAKER',
          type: 'SPEAKER',
          name: 'Phone Speaker',
          isActive: true,
          isAvailable: true,
          isSelectable: false,
        },
      ];
    }

    const devices = await mediaDevices.enumerateDevices();
    const outputs = devices.filter((d) => d.kind === 'audiooutput');

    if (outputs.length === 0) {
      return [
        {
          id: 'SPEAKER',
          type: 'SPEAKER',
          name: 'Phone Speaker',
          isActive: true,
          isAvailable: true,
          isSelectable: false,
        },
      ];
    }

    return outputs.map((d, idx) => {
      const name = d.label || `Audio Output ${idx + 1}`;
      const type = guessRouteTypeFromLabel(name);
      return {
        id: d.deviceId || stableRouteId(type, name),
        type,
        name,
        isActive: false,
        isAvailable: true,
        // Switching requires attaching sinkId to a specific media element.
        isSelectable: typeof (HTMLMediaElement.prototype as any).setSinkId === 'function',
      } satisfies AudioRouteDevice;
    });
  }

  async setRoute(_routeId: string): Promise<void> {
    // Web switching requires the caller to set sinkId on the actual HTMLMediaElement.
    // We intentionally no-op.
    throw new Error('Web output switching must be done on the media element via setSinkId().');
  }

  async subscribe(listener: (current: AudioRouteDevice) => void): Promise<{ remove: () => void }> {
    const mediaDevices = (navigator as any).mediaDevices as MediaDevices | undefined;
    if (!mediaDevices?.addEventListener) {
      return { remove: () => {} };
    }

    const onChange = async () => {
      // Best effort: re-enumerate and emit a generic “changed” signal.
      const devices = await this.listAvailableRoutes();
      // Keep current route as speaker; callers can interpret available changes.
      listener({
        id: 'SPEAKER',
        type: 'SPEAKER',
        name: 'Phone Speaker',
        isActive: true,
        isAvailable: true,
        isSelectable: false,
      });
      void devices;
    };

    mediaDevices.addEventListener('devicechange', onChange);
    return {
      remove: () => mediaDevices.removeEventListener('devicechange', onChange),
    };
  }
}

class ReactNativeAudioRouteAdapter implements AudioRouteAdapter {
  private rn: any;
  private emitter: any;
  private native: any;

  constructor(rn: any) {
    this.rn = rn;
    this.native = rn?.NativeModules?.AudioRouteNative;
    this.emitter = this.native ? new rn.NativeEventEmitter(this.native) : null;
  }

  isSupported(): boolean {
    return !!this.native;
  }

  async getCurrentRoute(): Promise<AudioRouteDevice> {
    if (!this.native?.getCurrentRoute) {
      return {
        id: 'UNKNOWN',
        type: 'UNKNOWN',
        name: 'Output',
        isActive: true,
        isAvailable: true,
        isSelectable: false,
      };
    }
    return this.native.getCurrentRoute();
  }

  async listAvailableRoutes(): Promise<AudioRouteDevice[]> {
    if (!this.native?.listAvailableRoutes) {
      // Fallback: at least show speaker route.
      return [
        {
          id: 'SPEAKER',
          type: 'SPEAKER',
          name: 'Phone Speaker',
          isActive: true,
          isAvailable: true,
          isSelectable: false,
        },
      ];
    }
    return this.native.listAvailableRoutes();
  }

  async setRoute(routeId: string): Promise<void> {
    if (!this.native?.setRoute) {
      throw new Error('Audio route switching is not available (missing native module).');
    }
    await this.native.setRoute(routeId);
  }

  async subscribe(listener: (current: AudioRouteDevice) => void): Promise<{ remove: () => void }> {
    if (!this.emitter?.addListener) {
      return { remove: () => {} };
    }

    const sub = this.emitter.addListener('AudioRouteChanged', (payload: AudioRouteDevice) => {
      listener(payload);
    });

    return {
      remove: () => sub.remove?.(),
    };
  }
}

async function createDefaultAdapter(): Promise<AudioRouteAdapter> {
  // Prefer RN adapter when available.
  try {
    // IMPORTANT: don't use `import('react-native')` directly.
    // Vite/Vitest will try to resolve it during bundling even if the code path never runs.
    // This Function-based importer stays runtime-only.
    const importer = new Function('m', 'return import(m)') as (m: string) => Promise<any>;
    const rn = await importer('react-native');
    const adapter = new ReactNativeAudioRouteAdapter(rn);
    if (adapter.isSupported()) return adapter;
  } catch {
    // Not React Native.
  }

  const web = new WebAudioRouteAdapter();
  return web;
}

export class AudioRouteService {
  private adapterPromise: Promise<AudioRouteAdapter>;
  private listeners = new Set<Listener>();
  private subscription: { remove: () => void } | null = null;

  private lastCurrent: AudioRouteDevice | null = null;
  private lastAvailableKey: string | null = null;

  constructor(adapter?: AudioRouteAdapter) {
    this.adapterPromise = adapter ? Promise.resolve(adapter) : createDefaultAdapter();
  }

  async isSupported(): Promise<boolean> {
    const adapter = await this.adapterPromise;
    return adapter.isSupported();
  }

  async getSnapshot(): Promise<AudioRouteChangeEvent> {
    const adapter = await this.adapterPromise;
    const [current, available] = await Promise.all([adapter.getCurrentRoute(), adapter.listAvailableRoutes()]);
    return { current, available };
  }

  async listAvailableRoutes(): Promise<AudioRouteDevice[]> {
    const adapter = await this.adapterPromise;
    return adapter.listAvailableRoutes();
  }

  async getCurrentRoute(): Promise<AudioRouteDevice> {
    const adapter = await this.adapterPromise;
    return adapter.getCurrentRoute();
  }

  async setRoute(routeId: string, options: SetRouteOptions = {}): Promise<void> {
    const adapter = await this.adapterPromise;
    const fade = options.fade !== false;
    const fadeDurationMs = options.fadeDurationMs ?? 150;

    const initialVolume = await maybeGetVolume(options.fadeController);
    const startVolume = initialVolume ?? 1;

    if (fade) {
      await fadeVolume(options.fadeController, startVolume, 0, fadeDurationMs);
    }

    await adapter.setRoute(routeId);

    if (fade) {
      await fadeVolume(options.fadeController, 0, startVolume, fadeDurationMs);
    }

    // Refresh snapshot so UI updates immediately.
    await this.refresh();
  }

  async refresh(): Promise<void> {
    const evt = await this.getSnapshot();
    this.emitIfChanged(evt);
  }

  subscribe(listener: Listener): Unsubscribe {
    this.listeners.add(listener);

    // Start underlying subscription lazily.
    void this.ensureNativeSubscription();

    // Emit initial snapshot.
    void this.getSnapshot().then((evt) => this.emitIfChanged(evt));

    return () => {
      this.listeners.delete(listener);
      if (this.listeners.size === 0) {
        this.subscription?.remove();
        this.subscription = null;
      }
    };
  }

  private async ensureNativeSubscription(): Promise<void> {
    if (this.subscription) return;
    const adapter = await this.adapterPromise;
    this.subscription = await adapter.subscribe(async (current) => {
      const available = await adapter.listAvailableRoutes();
      this.emitIfChanged({ current, available });
    });
  }

  private emitIfChanged(evt: AudioRouteChangeEvent): void {
    const availableKey = (evt.available || [])
      .map((d) => `${d.id}:${d.isAvailable ? 1 : 0}:${d.isSelectable ? 1 : 0}`)
      .sort()
      .join('|');

    const currentKey = evt.current ? `${evt.current.id}:${evt.current.type}:${evt.current.name}:${evt.current.isActive}` : 'null';

    if (this.lastCurrent && currentKey === `${this.lastCurrent.id}:${this.lastCurrent.type}:${this.lastCurrent.name}:${this.lastCurrent.isActive}` && this.lastAvailableKey === availableKey) {
      return;
    }

    this.lastCurrent = evt.current;
    this.lastAvailableKey = availableKey;

    for (const l of this.listeners) {
      l(evt);
    }
  }
}

/** Default singleton for app usage. */
export const audioRouteService = new AudioRouteService();
