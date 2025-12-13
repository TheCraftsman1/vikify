declare module 'react-native-track-player' {
  export enum Event {
    RemotePlay = 'remote-play',
    RemotePause = 'remote-pause',
    RemoteSeek = 'remote-seek',
    RemoteNext = 'remote-next',
    RemotePrevious = 'remote-previous',
    PlaybackProgressUpdated = 'playback-progress-updated',
  }

  export enum Capability {
    Play = 'play',
    Pause = 'pause',
    SeekTo = 'seek-to',
    SkipToNext = 'skip-to-next',
    SkipToPrevious = 'skip-to-previous',
  }

  export type PlaybackState = unknown;

  const TrackPlayer: {
    setupPlayer(options?: Record<string, unknown>): Promise<void>;
    updateOptions(options: Record<string, unknown>): Promise<void>;

    addEventListener(event: Event, listener: (event: any) => void | Promise<void>): void;

    play(): Promise<void>;
    pause(): Promise<void>;
    seekTo(seconds: number): Promise<void>;

    getCurrentTrack(): Promise<string | number | null>;
    getPosition(): Promise<number>;
    getRate(): Promise<number>;
    getPlaybackState(): Promise<any>;

    skipToNext(): Promise<void>;
    skipToPrevious(): Promise<void>;
  };

  export default TrackPlayer;
}
