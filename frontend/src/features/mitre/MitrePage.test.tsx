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
  it('renders the ATT&CK matrix with tactic columns and technique coverage', async () => {
    renderPage();

    // Les tactiques en colonnes, les techniques de base dans leurs cases.
    expect(await screen.findByText('Execution')).toBeInTheDocument();
    expect(screen.getByText('Persistence')).toBeInTheDocument();
    expect(screen.getByText('T1059')).toBeInTheDocument();
    expect(screen.getByText('Command and Scripting Interpreter')).toBeInTheDocument();
    expect(screen.getByText('T1547')).toBeInTheDocument();

    // La couverture colore la case et affiche le compte.
    expect(screen.getByText('5')).toBeInTheDocument();

    // La sous-technique n'apparaît pas dans la matrice (drawer seulement).
    expect(screen.queryByText('PowerShell')).not.toBeInTheDocument();
  });
});
