declare module 'react-native' {
  export type AppStateStatus = 'active' | 'background' | 'inactive';

  export const AppState: {
    currentState: AppStateStatus;
    addEventListener(
      type: 'change',
      handler: (state: AppStateStatus) => void,
    ): { remove(): void };
  };

  export const View: any;
  export const Text: any;
}
