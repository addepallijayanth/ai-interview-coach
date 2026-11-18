import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { MetaResponse } from '../api/types';

let cached: Promise<MetaResponse> | undefined;

/** Public server settings (AI configured? interview limits), fetched once per page load. */
export function useMeta(): MetaResponse | undefined {
  const [meta, setMeta] = useState<MetaResponse>();
  useEffect(() => {
    cached ??= api.meta();
    let active = true;
    cached.then((value) => active && setMeta(value)).catch(() => {
      cached = undefined;
    });
    return () => {
      active = false;
    };
  }, []);
  return meta;
}
