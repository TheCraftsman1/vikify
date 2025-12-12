import { useEffect, useState } from 'react';

export const getIsOnline = () => {
  if (typeof navigator === 'undefined') return true;
  // navigator.onLine can be undefined in some environments; treat that as online.
  return navigator.onLine !== false;
};

export const useOnlineStatus = () => {
  const [isOnline, setIsOnline] = useState(getIsOnline);

  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    // Sync once on mount.
    setIsOnline(getIsOnline());

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return isOnline;
};
