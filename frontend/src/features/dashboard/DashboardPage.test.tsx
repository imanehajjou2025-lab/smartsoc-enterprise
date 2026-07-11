import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import DashboardPage from './DashboardPage';
import type { AlertStats } from './dashboardApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const stats: AlertStats = {
  total: 12,
  bySeverity: { CRITICAL: 3, HIGH: 4, MEDIUM: 3, LOW: 1, INFO: 1 },
  byStatus: { NEW: 7, ACKNOWLEDGED: 2, RESOLVED: 2, FALSE_POSITIVE: 1 },
  bySource: { wazuh: 8, suricata: 3, shuffle: 1 },
  timeline: Array.from({ length: 7 }, (_, i) => ({
    date: `2026-07-0${i + 5}`,
    count: i,
  })),
};

vi.mock('./dashboardApi', () => ({
  getAlertStats: () => Promise.resolve(stats),
}));

// jsdom : ni WebSocket ni canvas — temps réel et graphes simulés.
vi.mock('../alerts/useAlertsRealtime', () => ({
  useAlertsRealtime: () => ({ connected: true }),
}));
vi.mock('../../shared/components/EChart', () => ({
  default: () => <div data-testid="echart" />,
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <DashboardPage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('DashboardPage', () => {
  it('renders KPI cards from the stats endpoint', async () => {
    renderPage();

    expect(await screen.findByText('12')).toBeInTheDocument();
    expect(screen.getByText('Alertes totales')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('Nouvelles (à trier)')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('Critiques')).toBeInTheDocument();
  });

  it('renders the three charts and the realtime badge', async () => {
    renderPage();
    await screen.findByText('12');

    expect(screen.getAllByTestId('echart')).toHaveLength(3);
    expect(screen.getByText('Temps réel')).toBeInTheDocument();
    expect(screen.getByText('Activité — 7 derniers jours')).toBeInTheDocument();
  });
});
