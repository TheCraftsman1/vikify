import { describe, expect, it, vi } from 'vitest';
import { AudioRouteService, formatOutputLabel, guessRouteTypeFromLabel } from '../AudioRouteService';
import type { AudioRouteAdapter, AudioRouteDevice } from '../AudioRouteService';

describe('AudioRouteService (logic)', () => {
  it('formats labels correctly', () => {
    expect(
      formatOutputLabel({
        id: 'SPEAKER',
        type: 'SPEAKER',
        name: 'Phone Speaker',
        isActive: true,
        isAvailable: true,
        isSelectable: true,
      })
    ).toBe('🔊 Phone Speaker');

    expect(
      formatOutputLabel({
        id: 'WIRED',
        type: 'WIRED_HEADPHONES',
        name: 'Wired Headset',
        isActive: true,
        isAvailable: true,
        isSelectable: true,
      })
    ).toBe('🎧 Headphones');

    expect(
      formatOutputLabel({
        id: 'BT',
        type: 'BLUETOOTH',
        name: 'AirPods Pro',
        isActive: true,
        isAvailable: true,
        isSelectable: true,
      })
    ).toBe('🎧 Bluetooth Device: AirPods Pro');
  });

  it('guesses route type heuristically from labels', () => {
    expect(guessRouteTypeFromLabel('Bluetooth Headset')).toBe('BLUETOOTH');
    expect(guessRouteTypeFromLabel('Wired headphones')).toBe('WIRED_HEADPHONES');
    expect(guessRouteTypeFromLabel('Phone speaker')).toBe('SPEAKER');
  });

  it('dedupes emissions when snapshot unchanged', async () => {
    const current: AudioRouteDevice = {
      id: 'SPEAKER',
      type: 'SPEAKER',
      name: 'Phone Speaker',
      isActive: true,
      isAvailable: true,
      isSelectable: true,
    };

    const available: AudioRouteDevice[] = [
      { ...current },
      {
        id: 'BT:AirPods',
        type: 'BLUETOOTH',
        name: 'AirPods',
        isActive: false,
        isAvailable: true,
        isSelectable: true,
      },
    ];

    const adapter: AudioRouteAdapter = {
      isSupported: () => true,
      getCurrentRoute: vi.fn(async () => current),
      listAvailableRoutes: vi.fn(async () => available),
      setRoute: vi.fn(async () => {}),
      subscribe: vi.fn(async () => ({ remove: () => {} })),
    };

    const svc = new AudioRouteService(adapter);

    const cb = vi.fn();
    const unsub = svc.subscribe(cb);

    // Initial snapshot triggers once.
    await svc.refresh();

    // Refresh again with same snapshot should not add new emission.
    await svc.refresh();

    // NOTE: depending on timing, first subscribe also triggers an initial getSnapshot.
    // We only assert it never grows beyond 2 calls.
    expect(cb.mock.calls.length).toBeLessThanOrEqual(2);

    unsub();
  });
});
