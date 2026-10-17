import { useCallback, useEffect, useRef, useState } from 'react';
import { errorMessage } from '../api/client';

/**
 * Runs an async loader whenever `deps` change and exposes data/error/loading plus a manual reload.
 * Stale responses from superseded calls are ignored.
 */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const callId = useRef(0);

  const run = useCallback(loader, deps);

  const reload = useCallback(
    async (silent = false) => {
      const id = ++callId.current;
      if (!silent) setLoading(true);
      try {
        const result = await run();
        if (id === callId.current) {
          setData(result);
          setError(null);
        }
      } catch (e) {
        if (id === callId.current) setError(errorMessage(e));
      } finally {
        if (id === callId.current) setLoading(false);
      }
    },
    [run],
  );

  useEffect(() => {
    reload();
  }, [reload]);

  return { data, error, loading, reload, setData };
}
