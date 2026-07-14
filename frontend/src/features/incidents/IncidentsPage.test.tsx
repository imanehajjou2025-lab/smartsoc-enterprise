import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import IncidentsPage from './IncidentsPage';
import type { Incident } from './incidentsApi';
import type { PageResponse } from '../alerts/alertsApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const incidents: Incident[] = [
  {
    id: 'i-1',
    reference: 'INC-2026-0001',
    title: 'Compromission srv-web-01',
    description: null,
    severity: 'HIGH',
    status: 'OPEN',
    assigneeUsername: null,
    openedAt: '2026-07-14T08:00:00Z',
  },
  {
    id: 'i-2',
    reference: 'INC-2026-0002',
    title: 'Scan réseau détecté',
    description: null,
    severity: 'MEDIUM',
    status: 'RESOLVED',
    assigneeUsername: 'analyst01',
    openedAt: '2026-07-14T07:00:00Z',
  },
];

const pageResponse: PageResponse<Incident> = {
  items: incidents,
  totalElements: 2,
  page: 0,
  size: 25,
  totalPages: 1,
};

vi.mock('./incidentsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./incidentsApi')>()),
  listIncidents: () => Promise.resolve(pageResponse),
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <IncidentsPage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('IncidentsPage', () => {
  it('renders incidents with reference, status and assignee', async () => {
    renderPage();

    expect(await screen.findByText('INC-2026-0001')).toBeInTheDocument();
    expect(screen.getByText('Compromission srv-web-01')).toBeInTheDocument();
    expect(screen.getByText('Ouvert')).toBeInTheDocument();
    expect(screen.getByText('Résolu')).toBeInTheDocument();
    expect(screen.getByText('analyst01')).toBeInTheDocument();
  });

  it('offers creation and filters', async () => {
    renderPage();
    await screen.findByText('INC-2026-0001');

    expect(screen.getByRole('button', { name: /Nouvel incident/i })).toBeInTheDocument();
    expect(screen.getByLabelText('Statut')).toBeInTheDocument();
    expect(screen.getByLabelText('Sévérité')).toBeInTheDocument();
  });
});
