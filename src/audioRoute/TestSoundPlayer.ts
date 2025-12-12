/*
  TestSoundPlayer.ts

  Plays a short 0.5–1s test tone so the user can confirm output routing.

  - Web: uses WebAudio OscillatorNode.
  - React Native: requires a small native module `TestToneNative` OR you can replace
    this implementation to use your audio stack (expo-av, react-native-sound, track-player, etc.).
*/

export type PlayTestSoundOptions = {
  durationMs?: number; // default 700
  frequencyHz?: number; // default 440
  volume?: number; // 0..1 default 0.35
};

function isWebAudioAvailable(): boolean {
  return typeof window !== 'undefined' && (window as any).AudioContext;
}

async function playWebTone({ durationMs, frequencyHz, volume }: Required<PlayTestSoundOptions>): Promise<void> {
  const AudioContextCtor = (window as any).AudioContext || (window as any).webkitAudioContext;
  const ctx: AudioContext = new AudioContextCtor();

  const osc = ctx.createOscillator();
  osc.type = 'sine';
  osc.frequency.value = frequencyHz;

  const gain = ctx.createGain();
  gain.gain.value = volume;

  osc.connect(gain);
  gain.connect(ctx.destination);

  osc.start();
  await new Promise((r) => setTimeout(r, durationMs));
  osc.stop();

  // Ensure resources are released.
  await ctx.close();
}

async function playReactNativeTone({ durationMs, frequencyHz, volume }: Required<PlayTestSoundOptions>): Promise<void> {
  // Avoid importing react-native at module scope so web builds/tests don’t break.
  // Also avoid `import('react-native')` directly; Vite/Vitest may try to resolve it.
  let rn: any = null;
  try {
    const importer = new Function('m', 'return import(m)') as (m: string) => Promise<any>;
    rn = await importer('react-native');
  } catch {
    rn = null;
  }
  const native = rn?.NativeModules?.TestToneNative;

  if (!native?.playTone) {
    throw new Error(
      'Test tone playback requires a native module `TestToneNative.playTone({durationMs, frequencyHz, volume})` ' +
        'or replace TestSoundPlayer.ts to use your audio library (expo-av / react-native-sound / track-player).'
    );
  }

  await native.playTone({ durationMs, frequencyHz, volume });
}

export async function playTestSound(options: PlayTestSoundOptions = {}): Promise<void> {
  const durationMs = Math.max(500, Math.min(1000, options.durationMs ?? 700));
  const frequencyHz = Math.max(80, Math.min(2000, options.frequencyHz ?? 440));
  const volume = Math.max(0.05, Math.min(1, options.volume ?? 0.35));

  const resolved: Required<PlayTestSoundOptions> = { durationMs, frequencyHz, volume };

  if (isWebAudioAvailable()) {
    return playWebTone(resolved);
  }

  // React Native path
  return playReactNativeTone(resolved);
}
