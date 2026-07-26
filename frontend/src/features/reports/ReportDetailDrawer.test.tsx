import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import ReportDetailDrawer from './ReportDetailDrawer';
import type { Report } from './reportsApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const report: Report = {
  id: 'rpt-1',
  title: 'Hebdo SOC',
  periodStart: '2026-07-19T00:00:00Z',
  periodEnd: '2026-07-26T00:00:00Z',
  generatedAt: '2026-07-26T09:00:00Z',
  generatedBy: 'admin',
  metrics: {
    alerts: { total: 5, bySeverity: { CRITICAL: 3, HIGH: 2 }, byStatus: { NEW: 5 } },
    incidents: { opened: 2, closed: 1, avgResolutionHours: 4.5 },
    soar: { started: 3, completed: 2, cancelled: 1 },
    huntQueriesExecuted: 7,
    mitreDistinctTechniquesCovered: 2,
    mitreTopTechniques: [
      { attackId: 'T1110', alertCount: 4 },
      { attackId: 'T1059', alertCount: 2 },
    ],
  },
};

vi.mock('./reportsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./reportsApi')>()),
  getReport: () => Promise.resolve(report),
}));

// jsdom n'a pas de canvas : le donut de sévérité est simulé.
vi.mock('../../shared/components/EChart', () => ({
  default: () => <div data-testid="echart" />,
}));

function renderDrawer() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <ReportDetailDrawer reportId="rpt-1" onClose={vi.fn()} />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('ReportDetailDrawer', () => {
  it('shows the report title and every metric group', async () => {
    renderDrawer();

    expect(await screen.findByText('Hebdo SOC')).toBeInTheDocument();
    // Valeurs uniques dans la page (les KPI comme "2" se répètent entre
    // sections — on vérifie ici les métriques non ambiguës).
    expect(screen.getByText('4.5 h')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument(); // requêtes hunting exécutées
    expect(screen.getByText('T1110')).toBeInTheDocument();
    expect(screen.getByText('T1059')).toBeInTheDocument();
  });
});
