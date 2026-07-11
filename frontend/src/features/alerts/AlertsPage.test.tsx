import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import AlertsPage from './AlertsPage';
import type { Alert, PageResponse } from './alertsApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const alerts: Alert[] = [
  {
    id: 'a-1',
    source: 'wazuh',
    externalId: 'evt-1',
    title: 'sshd: brute force trying to get access',
    description: null,
    severity: 'CRITICAL',
    status: 'NEW',
    detectedAt: '2026-07-11T08:00:00Z',
    receivedAt: '2026-07-11T08:00:05Z',
    hostname: 'srv-web-01',
    ruleId: '5712',
    mitreTechniques: ['T1110'],
    rawPayload: '{"rule":{"id":"5712"}}',
    aiScore: null,
    aiVerdict: null,
  },
  {
    id: 'a-2',
    source: 'suricata',
    externalId: 'evt-2',
    title: 'ET SCAN Nmap TCP scan',
    description: null,
    severity: 'MEDIUM',
    status: 'RESOLVED',
    detectedAt: '2026-07-11T07:00:00Z',
    receivedAt: '2026-07-11T07:00:03Z',
    hostname: 'fw-dmz-01',
    ruleId: '2009582',
    mitreTechniques: ['T1046'],
    rawPayload: null,
    aiScore: 0.87,
    aiVerdict: 'TRUE_POSITIVE',
  },
];

const pageResponse: PageResponse<Alert> = {
  items: alerts,
  totalElements: 2,
  page: 0,
  size: 25,
  totalPages: 1,
};

vi.mock('./alertsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./alertsApi')>()),
  listAlerts: () => Promise.resolve(pageResponse),
  updateAlertStatus: vi.fn(),
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <AlertsPage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('AlertsPage', () => {
  it('renders the triage queue with severities, statuses and AI score', async () => {
    renderPage();

    expect(await screen.findByText(/brute force/i)).toBeInTheDocument();
    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
    expect(screen.getByText('Nouvelle')).toBeInTheDocument();
    expect(screen.getByText('Résolue')).toBeInTheDocument();
    expect(screen.getByText('87 %')).toBeInTheDocument();
    expect(screen.getByText('srv-web-01')).toBeInTheDocument();
  });

  it('offers status and severity filters', async () => {
    renderPage();
    await screen.findByText(/brute force/i);

    expect(screen.getByLabelText('Statut')).toBeInTheDocument();
    expect(screen.getByLabelText('Sévérité')).toBeInTheDocument();
  });
});
