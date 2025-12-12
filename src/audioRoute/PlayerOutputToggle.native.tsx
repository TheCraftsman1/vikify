import React, { useMemo, useState } from 'react';

import {
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
  FlatList,
} from 'react-native';

import { useAudioRoute } from './useAudioRoute';
import type { AudioRouteDevice, SetRouteOptions } from './AudioRouteService';
import { formatOutputLabel } from './AudioRouteService';
import { playTestSound } from './TestSoundPlayer';

type Props = {
  /** Optional: wire your audio player volume control for smooth 150ms fade switching. */
  fadeOptions?: SetRouteOptions;
};

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
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>

      <Pressable
        accessibilityRole="button"
        style={[styles.button, !isSupported && styles.buttonDisabled]}
        onPress={() => setOpen(true)}
        disabled={!isSupported}
      >
        <Text style={styles.buttonText}>Change Output</Text>
      </Pressable>

      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <View style={styles.backdrop}>
          <View style={styles.modal}>
            <Text style={styles.modalTitle}>Audio Output</Text>

            <Text style={styles.currentLine}>Current: {current ? formatOutputLabel(current) : '—'}</Text>

            <FlatList
              data={sorted}
              keyExtractor={(d: AudioRouteDevice) => d.id}
              style={styles.list}
              renderItem={({ item }: { item: AudioRouteDevice }) => {
                const disabled = !item.isSelectable || isSwitching;
                return (
                  <Pressable
                    accessibilityRole="button"
                    onPress={() => onSelect(item)}
                    disabled={disabled}
                    style={[styles.routeRow, item.isActive && styles.routeRowActive, disabled && styles.routeRowDisabled]}
                  >
                    <Text style={styles.routeName}>{formatOutputLabel(item)}</Text>
                    {!item.isSelectable ? <Text style={styles.routeHint}>Not switchable</Text> : null}
                  </Pressable>
                );
              }}
            />

            <Pressable
              accessibilityRole="button"
              onPress={onTest}
              disabled={isTesting}
              style={[styles.button, styles.secondaryButton, isTesting && styles.buttonDisabled]}
            >
              <Text style={styles.buttonText}>{isTesting ? 'Playing…' : 'Play Test Sound'}</Text>
            </Pressable>

            {lastError ? <Text style={styles.errorText}>{lastError}</Text> : null}

            <Pressable accessibilityRole="button" onPress={() => setOpen(false)} style={styles.closeLink}>
              <Text style={styles.closeText}>Close</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 8 },
  label: { color: '#fff', opacity: 0.9 },
  button: {
    backgroundColor: '#1db954',
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: 10,
    alignItems: 'center',
  },
  secondaryButton: { backgroundColor: '#2a2a2a' },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#fff', fontWeight: '600' },

  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'center', padding: 16 },
  modal: { backgroundColor: '#121212', borderRadius: 14, padding: 16, gap: 10 },
  modalTitle: { color: '#fff', fontSize: 16, fontWeight: '700' },
  currentLine: { color: '#fff', opacity: 0.85 },

  list: { maxHeight: 240 },
  routeRow: { paddingVertical: 10, paddingHorizontal: 12, borderRadius: 10, backgroundColor: '#1b1b1b', marginBottom: 8 },
  routeRowActive: { borderWidth: 1, borderColor: '#1db954' },
  routeRowDisabled: { opacity: 0.6 },
  routeName: { color: '#fff', fontWeight: '600' },
  routeHint: { color: '#fff', opacity: 0.6, marginTop: 3, fontSize: 12 },

  errorText: { color: '#ff6b6b' },
  closeLink: { alignItems: 'center', paddingVertical: 6 },
  closeText: { color: '#fff', opacity: 0.85 },
});
