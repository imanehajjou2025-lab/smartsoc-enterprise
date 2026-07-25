import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import MitrePage from './MitrePage';
import type { MitreCoverage, MitreTactic, MitreTechnique } from './mitreApi';
import type { PageResponse } from '../alerts/alertsApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const tactics: MitreTactic[] = [
  { attackId: 'TA0002', shortName: 'execution', name: 'Execution' },
  { attackId: 'TA0003', shortName: 'persistence', name: 'Persistence' },
];

const technique = (over: Partial<MitreTechnique>): MitreTechnique => ({
  attackId: 'T0000',
  subTechnique: false,
  parentId: null,
  name: 'Technique',
  description: null,
  url: null,
  tactics: ['execution'],
  deprecated: false,
  attackVersion: '16.1',
  ...over,
});

const techniques: PageResponse<MitreTechnique> = {
  items: [
    technique({ attackId: 'T1059', name: 'Command and Scripting Interpreter' }),
    technique({ attackId: 'T1547', name: 'Boot or Logon Autostart', tactics: ['persistence'] }),
    // Une sous-technique : elle ne doit PAS encombrer la matrice.
    technique({ attackId: 'T1059.001', name: 'PowerShell', subTechnique: true, parentId: 'T1059' }),
  ],
  totalElements: 3,
  page: 0,
  size: 200,
  totalPages: 1,
};

const coverage: MitreCoverage[] = [{ attackId: 'T1059', alertCount: 5 }];

vi.mock('./mitreApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./mitreApi')>()),
  listTactics: () => Promise.resolve(tactics),
  listTechniques: () => Promise.resolve(techniques),
  getCoverage: () => Promise.resolve(coverage),
}));

// jsdom n'a pas de canvas : le donut ECharts est simulé (comme le dashboard).
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
            <MitrePage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('MitrePage', () => {
  it('renders the coverage dashboard: KPIs, matrix and top techniques', async () => {
    renderPage();

    // Cartes KPI et panneaux du tableau de bord.
    expect(await screen.findByText('Techniques observées')).toBeInTheDocument();
    expect(screen.getByText('Techniques les plus citées')).toBeInTheDocument();
    expect(screen.getByText('Couverture MITRE')).toBeInTheDocument();

    // Les tactiques en colonnes, les techniques de base dans leurs cases.
    expect(screen.getByText('Execution')).toBeInTheDocument();
    expect(screen.getByText('Persistence')).toBeInTheDocument();
    expect(screen.getByText('T1547')).toBeInTheDocument();

    // T1059 (couvert) apparaît dans la matrice ET le panneau « plus citées ».
    expect(screen.getAllByText('T1059').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Command and Scripting Interpreter').length).toBeGreaterThanOrEqual(
      1,
    );

    // La sous-technique n'apparaît pas dans la matrice (drawer seulement).
    expect(screen.queryByText('PowerShell')).not.toBeInTheDocument();
  });
});
