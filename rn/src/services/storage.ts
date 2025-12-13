export type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };

export interface KeyValueStorage {
  getString(key: string): string | undefined;
  setString(key: string, value: string): void;
  delete(key: string): void;
}

export const createAsyncStorageAdapter = (asyncStorage: {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}): {
  getString(key: string): Promise<string | undefined>;
  setString(key: string, value: string): Promise<void>;
  delete(key: string): Promise<void>;
} => {
  return {
    async getString(key) {
      const value = await asyncStorage.getItem(key);
      return value ?? undefined;
    },
    async setString(key, value) {
      await asyncStorage.setItem(key, value);
    },
    async delete(key) {
      await asyncStorage.removeItem(key);
    },
  };
};

export const safeJsonParse = <T>(value: string | undefined): T | null => {
  if (!value) return null;
  try {
    return JSON.parse(value) as T;
  } catch {
    return null;
  }
};
