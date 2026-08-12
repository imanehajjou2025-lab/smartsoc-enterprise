import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import authReducer, { type AuthState } from '../auth/authSlice';
import AssetDetailDrawer from './AssetDetailDrawer';
import type { Asset } from './assetsApi';
import type { PageResponse } from '../alerts/alertsApi';
import { theme } from '../../app/theme';

const wazuhAsset: Asset = {
  id: 'asset-1',
  hostname: 'win10-client',
  displayName: 'Poste WIN10-CLIENT',
  type: 'OTHER',
  criticality: 'MEDIUM',
  exposure: 'INTERNAL',
  ipAddress: '10.100.0.9',
  owner: null,
  description: null,
  status: 'ACTIVE',
  registeredAt: '2026-07-21T01:16:00Z',
  decommissionedAt: null,
  operatingSystem: 'Microsoft Windows 10 Home',
  lastSeenAt: '2026-08-10T14:43:03Z',
  hardwareSummary: null,
  externalSource: 'wazuh',
  agentConnectionStatus: 'ACTIVE',
};

const emptyPage: PageResponse<never> = {
  items: [],
  totalElements: 0,
  page: 0,
  size: 10,
  totalPages: 0,
};
const restartAgentMock = vi.fn();
const blockIpMock = vi.fn();

vi.mock('./assetsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./assetsApi')>()),
  getAsset: () => Promise.resolve(wazuhAsset),
  listCorrelatedAlerts: () => Promise.resolve(emptyPage),
  restartAgent: (...args: unknown[]) => restartAgentMock(...args),
  blockIp: (...args: unknown[]) => blockIpMock(...args),
}));

vi.mock('../vulnerabilities/vulnerabilitiesApi', () => ({
  listVulnerabilities: () => Promise.resolve(emptyPage),
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
          <AssetDetailDrawer assetId="asset-1" onClose={() => {}} />
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('AssetDetailDrawer — redémarrage agent Wazuh', () => {
  it('exige de retaper le hostname exact avant d’activer la confirmation', async () => {
    renderDrawer('SOC_ANALYST');

    fireEvent.click(await screen.findByRole('button', { name: /Redémarrer l'agent/i }));

    const confirmButton = await screen.findByRole('button', { name: 'Confirmer le redémarrage' });
    expect(confirmButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Hostname/), { target: { value: 'mauvais-hostname' } });
    fireEvent.change(screen.getByLabelText(/Motif/), { target: { value: 'test' } });
    expect(confirmButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Hostname/), { target: { value: 'win10-client' } });
    expect(confirmButton).not.toBeDisabled();
  });

  it('envoie la demande avec le hostname confirmé et le motif une fois validée', async () => {
    restartAgentMock.mockResolvedValueOnce(undefined);
    renderDrawer('SOC_ANALYST');

    fireEvent.click(await screen.findByRole('button', { name: /Redémarrer l'agent/i }));
    fireEvent.change(await screen.findByLabelText(/Hostname/), {
      target: { value: 'win10-client' },
    });
    fireEvent.change(screen.getByLabelText(/Motif/), { target: { value: 'Agent bloqué' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer le redémarrage' }));

    await waitFor(() =>
      expect(restartAgentMock).toHaveBeenCalledWith('asset-1', 'win10-client', 'Agent bloqué'),
    );
  });

  it("n'affiche aucun déclencheur pour un rôle sans droit d'écriture", async () => {
    renderDrawer('VIEWER');

    await screen.findByText('Poste WIN10-CLIENT');
    expect(screen.queryByRole('button', { name: /Redémarrer l'agent/i })).not.toBeInTheDocument();
  });
});

describe('AssetDetailDrawer — blocage IP (active-response)', () => {
  it('exige hostname exact et IPv4 valide avant d’activer la confirmation', async () => {
    renderDrawer('SOC_ANALYST');

    fireEvent.click(await screen.findByRole('button', { name: /Bloquer une IP/i }));

    const confirmButton = await screen.findByRole('button', { name: 'Confirmer le blocage' });
    expect(confirmButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Hostname/), { target: { value: 'win10-client' } });
    fireEvent.change(screen.getByLabelText(/Adresse IP/), { target: { value: 'not-an-ip' } });
    fireEvent.change(screen.getByLabelText(/Motif/), { target: { value: 'test' } });
    expect(confirmButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Adresse IP/), { target: { value: '203.0.113.42' } });
    expect(confirmButton).not.toBeDisabled();
  });

  it('envoie la demande avec le hostname, l’IP et le motif une fois validée', async () => {
    blockIpMock.mockResolvedValueOnce(undefined);
    renderDrawer('SOC_ANALYST');

    fireEvent.click(await screen.findByRole('button', { name: /Bloquer une IP/i }));
    fireEvent.change(await screen.findByLabelText(/Hostname/), {
      target: { value: 'win10-client' },
    });
    fireEvent.change(screen.getByLabelText(/Adresse IP/), { target: { value: '203.0.113.42' } });
    fireEvent.change(screen.getByLabelText(/Motif/), { target: { value: 'IP malveillante' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer le blocage' }));

    await waitFor(() =>
      expect(blockIpMock).toHaveBeenCalledWith(
        'asset-1',
        'win10-client',
        '203.0.113.42',
        'IP malveillante',
      ),
    );
  });

  it("n'affiche aucun déclencheur pour un rôle sans droit d'écriture", async () => {
    renderDrawer('VIEWER');

    await screen.findByText('Poste WIN10-CLIENT');
    expect(screen.queryByRole('button', { name: /Bloquer une IP/i })).not.toBeInTheDocument();
  });
});
