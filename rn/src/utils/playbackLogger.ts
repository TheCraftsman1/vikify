type LogContext = Record<string, unknown>;

const prefix = '[VikifyPlayback]';

export const logInfo = (message: string, ctx?: LogContext) => {
  if (ctx) {
    console.info(prefix, message, ctx);
  } else {
    console.info(prefix, message);
  }
};

export const logWarn = (message: string, ctx?: LogContext) => {
  if (ctx) {
    console.warn(prefix, message, ctx);
  } else {
    console.warn(prefix, message);
  }
};

export const logError = (message: string, ctx?: LogContext) => {
  if (ctx) {
    console.error(prefix, message, ctx);
  } else {
    console.error(prefix, message);
  }
};
