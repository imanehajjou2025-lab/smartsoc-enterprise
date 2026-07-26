import { useEffect, useState } from 'react';

const STORAGE_KEY = 'smartsoc.sidebarCollapsed';

/** État replié/déployé de la sidebar, persisté entre sessions. */
export function useSidebarCollapsed() {
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(STORAGE_KEY) === 'true');

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, String(collapsed));
  }, [collapsed]);

  return { collapsed, toggle: () => setCollapsed((c) => !c) };
}
