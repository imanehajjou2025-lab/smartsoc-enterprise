import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Vitest tourne sans "globals" : le cleanup automatique de Testing Library
// ne se declenche pas tout seul, on le branche explicitement.
afterEach(() => {
  cleanup();
});
