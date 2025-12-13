import { logWarn } from './playbackLogger';

export type RetryOptions = {
  retries?: number;
  baseDelayMs?: number;
  maxDelayMs?: number;
  // Provide your own online check (NetInfo etc.)
  isOnline?: () => Promise<boolean>;
};

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export const withRetryBackoff = async <T>(fn: () => Promise<T>, label: string, opts?: RetryOptions): Promise<T> => {
  const retries = opts?.retries ?? 3;
  const baseDelayMs = opts?.baseDelayMs ?? 250;
  const maxDelayMs = opts?.maxDelayMs ?? 2500;

  let attempt = 0;
  // eslint-disable-next-line no-constant-condition
  while (true) {
    attempt += 1;
    try {
      if (opts?.isOnline) {
        const online = await opts.isOnline();
        if (!online) throw new Error('offline');
      }
      return await fn();
    } catch (e) {
      if (attempt > retries) throw e;
      const delay = Math.min(maxDelayMs, baseDelayMs * Math.pow(2, attempt - 1));
      logWarn('Retry', { label, attempt, delayMs: delay, error: String(e) });
      await sleep(delay);
    }
  }
};
