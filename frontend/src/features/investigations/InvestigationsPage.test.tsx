import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import InvestigationsPage from './InvestigationsPage';
import type { Case } from './investigationsApi';
import type { PageResponse } from '../alerts/alertsApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const cases: Case[] = [
  {
    id: 'c-1',
    reference: 'CASE-2026-0001',
    title: 'Campagne de phishing ciblée',
    description: null,
    priority: 'HIGH',
    status: 'OPEN',
    assigneeUsername: null,
    conclusion: null,
    originCaseId: null,
    openedAt: '2026-07-18T08:00:00Z',
    closedAt: null,
  },
  {
    id: 'c-2',
    reference: 'CASE-2026-0002',
    title: 'Exfiltration de données confirmée',
    description: null,
    priority: 'CRITICAL',
    status: 'CLOSED',
    assigneeUsername: 'analyst01',
    conclusion: 'Vrai positif : fuite contenue',
    originCaseId: null,
    openedAt: '2026-07-17T09:00:00Z',
    closedAt: '2026-07-18T07:00:00Z',
  },
];

const pageResponse: PageResponse<Case> = {
  items: cases,
  totalElements: 2,
  page: 0,
  size: 25,
  totalPages: 1,
};

vi.mock('./investigationsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./investigationsApi')>()),
  listCases: () => Promise.resolve(pageResponse),
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <InvestigationsPage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('InvestigationsPage', () => {
  it('renders cases with reference, status and assignee', async () => {
    renderPage();

    expect(await screen.findByText('CASE-2026-0001')).toBeInTheDocument();
    expect(screen.getByText('Campagne de phishing ciblée')).toBeInTheDocument();
    // « Ouvert » (chip) ne doit pas se confondre avec l'en-tête « Ouvert le » :
    // findByText est en correspondance exacte, le piège est couvert.
    expect(screen.getByText('Ouvert')).toBeInTheDocument();
    expect(screen.getByText('Clôturé')).toBeInTheDocument();
    expect(screen.getByText('analyst01')).toBeInTheDocument();
  });

  it('offers creation and filters', async () => {
    renderPage();
    await screen.findByText('CASE-2026-0001');

    expect(screen.getByRole('button', { name: /Nouveau cas/i })).toBeInTheDocument();
    expect(screen.getByLabelText('Statut')).toBeInTheDocument();
    expect(screen.getByLabelText('Priorité')).toBeInTheDocument();
  });
});
