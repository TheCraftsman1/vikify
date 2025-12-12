import React, { useMemo, useState } from 'react';

import { useAudioRoute } from './useAudioRoute';
import type { AudioRouteDevice, SetRouteOptions } from './AudioRouteService';
import { formatOutputLabel } from './AudioRouteService';
import { playTestSound } from './TestSoundPlayer';

type Props = {
  /** Optional: wire your audio player volume control for smooth 150ms fade switching. */
  fadeOptions?: SetRouteOptions;
};

// Web implementation for this Vite project.
// React Native implementation lives in PlayerOutputToggle.native.tsx.
export function PlayerOutputToggle({ fadeOptions }: Props) {
  const { label, available, current, setRoute, lastError, isSupported } = useAudioRoute();
  const [open, setOpen] = useState(false);
  const [isSwitching, setIsSwitching] = useState(false);
  const [isTesting, setIsTesting] = useState(false);

  const sorted = useMemo(() => {
    const list = [...available];
    list.sort((a, b) => {
      if (a.isActive && !b.isActive) return -1;
      if (!a.isActive && b.isActive) return 1;
      if (a.type !== b.type) return a.type.localeCompare(b.type);
      return a.name.localeCompare(b.name);
    });
    return list;
  }, [available]);

  const onSelect = async (device: AudioRouteDevice) => {
    if (!device.isSelectable) return;
    setIsSwitching(true);
    try {
      await setRoute(device.id, {
        fade: true,
        fadeDurationMs: 150,
        ...(fadeOptions ?? {}),
      });
      setOpen(false);
    } finally {
      setIsSwitching(false);
    }
  };

  const onTest = async () => {
    setIsTesting(true);
    try {
      await playTestSound({ durationMs: 700, frequencyHz: 440, volume: 0.35 });
    } finally {
      setIsTesting(false);
    }
  };

  return (
    <div style={{ display: 'grid', gap: 'var(--space-2)' }}>
      <div style={{ color: 'var(--text-primary)', opacity: 0.9 }}>{label}</div>

      <button
        type="button"
        onClick={() => setOpen(true)}
        disabled={!isSupported}
        style={{
          background: 'var(--accent-primary)',
          color: 'var(--text-primary)',
          padding: '10px 12px',
          borderRadius: 'var(--border-radius-lg)',
          opacity: isSupported ? 1 : 0.6,
        }}
      >
        Change Output
      </button>

      {open ? (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.6)',
            display: 'grid',
            placeItems: 'center',
            padding: 'var(--space-4)',
            zIndex: 9999,
          }}
          onClick={() => setOpen(false)}
        >
          <div
            style={{
              width: 'min(520px, 100%)',
              background: 'var(--bg-secondary)',
              borderRadius: 'var(--border-radius-xl)',
              padding: 'var(--space-4)',
              boxShadow: 'var(--shadow-lg)',
              display: 'grid',
              gap: 'var(--space-3)',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ fontWeight: 700, fontSize: 'var(--font-size-md)' }}>Audio Output</div>
            <div style={{ color: 'var(--text-secondary)' }}>
              Current: {current ? formatOutputLabel(current) : '—'}
            </div>

            <div style={{ display: 'grid', gap: 'var(--space-2)', maxHeight: 260, overflow: 'auto' }}>
              {sorted.map((d) => {
                const disabled = !d.isSelectable || isSwitching;
                return (
                  <button
                    key={d.id}
                    type="button"
                    disabled={disabled}
                    onClick={() => onSelect(d)}
                    style={{
                      textAlign: 'left',
                      padding: '10px 12px',
                      borderRadius: 'var(--border-radius-lg)',
                      background: 'var(--bg-tertiary)',
                      border: d.isActive ? `1px solid var(--accent-primary)` : '1px solid transparent',
                      opacity: disabled ? 0.6 : 1,
                    }}
                  >
                    <div style={{ fontWeight: 600 }}>{formatOutputLabel(d)}</div>
                    {!d.isSelectable ? (
                      <div style={{ color: 'var(--text-muted)', marginTop: 4, fontSize: 'var(--font-size-sm)' }}>
                        Not switchable on this platform
                      </div>
                    ) : null}
                  </button>
                );
              })}
            </div>

            <button
              type="button"
              onClick={onTest}
              disabled={isTesting}
              style={{
                background: 'var(--bg-highlight)',
                color: 'var(--text-primary)',
                padding: '10px 12px',
                borderRadius: 'var(--border-radius-lg)',
                opacity: isTesting ? 0.6 : 1,
              }}
            >
              {isTesting ? 'Playing…' : 'Play Test Sound'}
            </button>

            {lastError ? <div style={{ color: 'var(--color-error)' }}>{lastError}</div> : null}

            <button
              type="button"
              onClick={() => setOpen(false)}
              style={{
                background: 'transparent',
                color: 'var(--text-secondary)',
                padding: '6px 0',
              }}
            >
              Close
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
