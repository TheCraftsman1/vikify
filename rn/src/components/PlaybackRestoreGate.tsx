import React, { useEffect, useState } from 'react';
import { View, Text } from 'react-native';

// Minimal UI indicator: shown only if restore takes >300ms.
export const PlaybackRestoreGate = ({ isRestoring, children }: { isRestoring: boolean; children: React.ReactNode }) => {
  const [show, setShow] = useState(false);

  useEffect(() => {
    if (!isRestoring) {
      setShow(false);
      return;
    }
    const t = setTimeout(() => setShow(true), 300);
    return () => clearTimeout(t);
  }, [isRestoring]);

  if (!show) return <>{children}</>;

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>Restoring playback…</Text>
    </View>
  );
};
