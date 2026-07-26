import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import ReportsPage from './ReportsPage';
import type { ReportSummary } from './reportsApi';
import type { PageResponse } from '../alerts/alertsApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const report: ReportSummary = {
  id: 'rpt-1',
  title: 'Hebdo SOC',
  periodStart: '2026-07-19T00:00:00Z',
  periodEnd: '2026-07-26T00:00:00Z',
  generatedAt: '2026-07-26T09:00:00Z',
  generatedBy: 'admin',
};

const reportsPage: PageResponse<ReportSummary> = {
  items: [report],
  totalElements: 1,
  page: 0,
  size: 25,
  totalPages: 1,
};

vi.mock('./reportsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./reportsApi')>()),
  listReports: () => Promise.resolve(reportsPage),
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <ReportsPage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('ReportsPage', () => {
  it('renders the report list with title and generator', async () => {
    renderPage();

    expect(await screen.findByText('Hebdo SOC')).toBeInTheDocument();
    expect(screen.getByText('admin')).toBeInTheDocument();
  });
});
