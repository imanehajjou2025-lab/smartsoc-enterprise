import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import authReducer, { type AuthState } from '../auth/authSlice';
import IocDetailDrawer from './IocDetailDrawer';
import type { Indicator, Reputation } from './intelligenceApi';
import type { PageResponse } from '../alerts/alertsApi';
import { theme } from '../../app/theme';

const ioc: Indicator = {
  id: 'ioc-1',
  type: 'IPV4',
  value: '45.83.12.7',
  status: 'ACTIVE',
  confidence: 72,
  tlp: 'AMBER',
  feedSource: 'misp',
  externalId: null,
  description: null,
  tags: [],
  firstSeen: '2026-07-20T06:00:00Z',
  lastSeen: '2026-07-20T06:00:00Z',
  validUntil: null,
  revoked: false,
  revocationReason: null,
  revokedAt: null,
};

const emptyAlerts: PageResponse<never> = { items: [], totalElements: 0, page: 0, size: 10, totalPages: 0 };

const reputation: Reputation = {
  id: 'rep-1',
  source: 'virustotal',
  type: 'IPV4',
  value: '45.83.12.7',
  verdict: 'MALICIOUS',
  maliciousCount: 3,
  suspiciousCount: 1,
  harmlessCount: 50,
  undetectedCount: 20,
  firstCheckedAt: '2026-08-09T10:00:00Z',
  checkedAt: '2026-08-09T10:00:00Z',
};

const getReputationMock = vi.fn();

vi.mock('./intelligenceApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./intelligenceApi')>()),
  getIoc: () => Promise.resolve(ioc),
  listMatchingAlerts: () => Promise.resolve(emptyAlerts),
  getReputation: (...args: unknown[]) => getReputationMock(...args),
}));

function renderDrawer(role: 'SOC_ANALYST' | 'VIEWER') {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: {
        user: { username: 'analyst', userId: 'u-1', role, fullName: null },
        status: 'authenticated',
        loginError: null,
        loginPending: false,
      } satisfies AuthState,
    },
  });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <IocDetailDrawer iocId="ioc-1" onClose={() => {}} />
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('IocDetailDrawer — réputation VirusTotal', () => {
  it('interroge la réputation à la demande et affiche le verdict dérivé', async () => {
    getReputationMock.mockResolvedValueOnce(reputation);
    renderDrawer('SOC_ANALYST');

    const button = await screen.findByRole('button', { name: 'Vérifier la réputation' });
    fireEvent.click(button);

    expect(await screen.findByText('Malveillant')).toBeInTheDocument();
    expect(screen.getByText(/3 malveillant\(s\)/)).toBeInTheDocument();
    await waitFor(() => expect(getReputationMock).toHaveBeenCalledWith('IPV4', '45.83.12.7'));
  });

  it("n'affiche aucun déclencheur pour un rôle sans droit d'écriture", async () => {
    renderDrawer('VIEWER');

    await screen.findByText('45.83.12.7');
    expect(screen.queryByRole('button', { name: 'Vérifier la réputation' })).not.toBeInTheDocument();
  });
});
