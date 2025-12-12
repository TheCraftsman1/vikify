// This workspace is a Vite web app; `react-native` is not installed here.
// We keep RN-only UI in `*.native.tsx` files for portability; this minimal shim
// prevents TypeScript from erroring in the web workspace.
//
// In a real React Native app, delete this file and use the real `react-native` types.

declare module 'react-native' {
  export const Modal: any;
  export const Pressable: any;
  export const StyleSheet: any;
  export const Text: any;
  export const View: any;
  export const FlatList: any;
  export const NativeModules: any;
  export const NativeEventEmitter: any;
}
