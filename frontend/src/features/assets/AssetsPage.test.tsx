import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import AssetsPage from './AssetsPage';
import type { Asset } from './assetsApi';
import type { PageResponse } from '../alerts/alertsApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const assets: Asset[] = [
  {
    id: 'a-1',
    hostname: 'srv-web-01',
    displayName: 'Serveur web principal',
    type: 'SERVER',
    criticality: 'CRITICAL',
    exposure: 'INTERNET_FACING',
    ipAddress: '10.10.1.20',
    owner: 'Équipe infra',
    description: null,
    status: 'ACTIVE',
    registeredAt: '2026-07-18T08:00:00Z',
    decommissionedAt: null,
  },
  {
    id: 'a-2',
    hostname: 'wks-compta-07',
    displayName: 'Poste comptabilité',
    type: 'WORKSTATION',
    criticality: 'LOW',
    exposure: 'INTERNAL',
    ipAddress: null,
    owner: null,
    description: null,
    status: 'DECOMMISSIONED',
    registeredAt: '2026-07-17T09:00:00Z',
    decommissionedAt: '2026-07-18T07:00:00Z',
  },
];

const pageResponse: PageResponse<Asset> = {
  items: assets,
  totalElements: 2,
  page: 0,
  size: 25,
  totalPages: 1,
};

vi.mock('./assetsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./assetsApi')>()),
  listAssets: () => Promise.resolve(pageResponse),
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={['/assets']}>
            <AssetsPage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('AssetsPage', () => {
  it('renders the inventory with hostname, criticality, exposure and status', async () => {
    renderPage();

    expect(await screen.findByText('srv-web-01')).toBeInTheDocument();
    expect(screen.getByText('Serveur web principal')).toBeInTheDocument();
    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
    // L'exposition saillante : « Exposé Internet » n'apparaît que sur la
    // chip pleine (le filtre affiche le label seulement quand il est ouvert).
    expect(screen.getByText('Exposé Internet')).toBeInTheDocument();
    // « Actif » (chip) ≠ titre « Actifs » : correspondance exacte.
    expect(screen.getByText('Actif')).toBeInTheDocument();
    expect(screen.getByText('Décommissionné')).toBeInTheDocument();
  });

  it('offers registration, search and filters', async () => {
    renderPage();
    await screen.findByText('srv-web-01');

    expect(screen.getByRole('button', { name: /Enregistrer un actif/i })).toBeInTheDocument();
    expect(screen.getByLabelText('Recherche')).toBeInTheDocument();
    expect(screen.getByLabelText('Type')).toBeInTheDocument();
    expect(screen.getByLabelText('Criticité')).toBeInTheDocument();
    expect(screen.getByLabelText('Exposition')).toBeInTheDocument();
    expect(screen.getByLabelText('Statut')).toBeInTheDocument();
  });
});
