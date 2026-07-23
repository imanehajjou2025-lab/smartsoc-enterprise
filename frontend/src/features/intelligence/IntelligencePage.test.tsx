import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import IntelligencePage from './IntelligencePage';
import type { Indicator } from './intelligenceApi';
import type { PageResponse } from '../alerts/alertsApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const indicators: Indicator[] = [
  {
    id: 'ioc-1',
    type: 'IPV4',
    value: '45.83.12.7',
    status: 'ACTIVE',
    confidence: 72,
    tlp: 'AMBER',
    feedSource: 'misp',
    externalId: null,
    description: null,
    tags: ['scanner'],
    firstSeen: '2026-07-20T06:00:00Z',
    lastSeen: '2026-07-20T06:00:00Z',
    validUntil: null,
    revoked: false,
    revocationReason: null,
    revokedAt: null,
  },
  {
    id: 'ioc-2',
    type: 'DOMAIN',
    value: 'evil-c2.example',
    status: 'REVOKED',
    confidence: 90,
    tlp: 'RED',
    feedSource: 'misp',
    externalId: null,
    description: null,
    tags: ['c2'],
    firstSeen: '2026-07-19T06:00:00Z',
    lastSeen: '2026-07-19T06:00:00Z',
    validUntil: null,
    revoked: true,
    revocationReason: 'Faux positif',
    revokedAt: '2026-07-20T09:00:00Z',
  },
  {
    id: 'ioc-3',
    type: 'SHA256',
    value: 'a'.repeat(64),
    status: 'EXPIRED',
    confidence: 60,
    tlp: 'GREEN',
    feedSource: 'otx',
    externalId: null,
    description: null,
    tags: [],
    firstSeen: '2026-07-01T06:00:00Z',
    lastSeen: '2026-07-01T06:00:00Z',
    validUntil: '2026-07-10T00:00:00Z',
    revoked: false,
    revocationReason: null,
    revokedAt: null,
  },
];

const pageResponse: PageResponse<Indicator> = {
  items: indicators,
  totalElements: 3,
  page: 0,
  size: 25,
  totalPages: 1,
};

vi.mock('./intelligenceApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./intelligenceApi')>()),
  listIocs: () => Promise.resolve(pageResponse),
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <IntelligencePage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('IntelligencePage', () => {
  it('renders the referential with type, status, TLP, confidence and source', async () => {
    renderPage();

    // Les valeurs et leurs types traduits.
    expect(await screen.findByText('45.83.12.7')).toBeInTheDocument();
    expect(screen.getByText('evil-c2.example')).toBeInTheDocument();
    expect(screen.getByText('IPv4')).toBeInTheDocument();
    expect(screen.getByText('Domaine')).toBeInTheDocument();
    expect(screen.getByText('SHA-256')).toBeInTheDocument();

    // Le statut vient du serveur : les trois états se lisent tels quels.
    expect(screen.getByText('Actif')).toBeInTheDocument();
    expect(screen.getByText('Révoqué')).toBeInTheDocument();
    expect(screen.getByText('Expiré')).toBeInTheDocument();

    // TLP, confiance et source.
    expect(screen.getByText('TLP:AMBER')).toBeInTheDocument();
    expect(screen.getByText('TLP:RED')).toBeInTheDocument();
    expect(screen.getByText('72')).toBeInTheDocument();
    expect(screen.getByText('otx')).toBeInTheDocument();
  });

  it('offers the CTI-specific filters', async () => {
    renderPage();
    await screen.findByText('45.83.12.7');

    expect(screen.getByLabelText('Recherche')).toBeInTheDocument();
    expect(screen.getByLabelText('Type')).toBeInTheDocument();
    expect(screen.getByLabelText('Statut')).toBeInTheDocument();
    expect(screen.getByLabelText('Source')).toBeInTheDocument();
    expect(screen.getByLabelText('Tag')).toBeInTheDocument();
    expect(screen.getByLabelText('Confiance min.')).toBeInTheDocument();
  });
});
