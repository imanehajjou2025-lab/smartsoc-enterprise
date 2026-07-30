import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import HuntingPage from './HuntingPage';
import type { HuntExecutionResult, HuntFieldDescriptor, HuntQuery } from './huntingApi';
import type { Alert, PageResponse } from '../alerts/alertsApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const fields: HuntFieldDescriptor[] = [
  { field: 'SEVERITY', allowedOperators: ['EQUALS'] },
  { field: 'STATUS', allowedOperators: ['EQUALS'] },
  { field: 'SOURCE', allowedOperators: ['EQUALS', 'CONTAINS'] },
  { field: 'HOSTNAME', allowedOperators: ['EQUALS', 'CONTAINS'] },
  { field: 'RULE_ID', allowedOperators: ['EQUALS', 'CONTAINS'] },
  { field: 'DETECTED_AT', allowedOperators: ['GREATER_THAN', 'LESS_THAN'] },
  { field: 'MITRE_TECHNIQUE', allowedOperators: ['CONTAINS'] },
  { field: 'RAW_PAYLOAD_TEXT', allowedOperators: ['CONTAINS'] },
];

const savedHunt: HuntQuery = {
  id: 'hunt-1',
  name: 'Mimikatz sur srv-web',
  description: null,
  criteria: {
    kind: 'GROUP',
    operator: 'AND',
    children: [{ kind: 'CONDITION', field: 'SEVERITY', operator: 'EQUALS', value: 'CRITICAL' }],
  },
  visibility: 'PRIVATE',
  lastExecutedAt: '2026-07-25T10:00:00Z',
};

const savedHuntsPage: PageResponse<HuntQuery> = {
  items: [savedHunt],
  totalElements: 1,
  page: 0,
  size: 50,
  totalPages: 1,
};

const matchedAlert: Alert = {
  id: 'alert-1',
  source: 'wazuh',
  externalId: 'evt-1',
  title: 'PowerShell execution detected',
  description: null,
  severity: 'CRITICAL',
  status: 'NEW',
  detectedAt: '2026-07-25T09:00:00Z',
  receivedAt: '2026-07-25T09:00:01Z',
  hostname: 'srv-web-01',
  ruleId: '5710',
  mitreTechniques: ['T1059'],
  observables: [],
  rawPayload: null,
  aiScore: null,
  aiVerdict: null,
  aiZone: null,
  aiHardOverride: false,
  aiJustifications: [],
};

const executionResult: HuntExecutionResult = {
  summary: {
    huntId: null,
    executedAt: '2026-07-25T10:05:00Z',
    tookMillis: 12,
    matchedCount: 1,
    truncated: false,
  },
  statistics: { bySeverity: { CRITICAL: 1 }, byStatus: { NEW: 1 }, bySource: { wazuh: 1 } },
  matches: { items: [matchedAlert], totalElements: 1, page: 0, size: 10, totalPages: 1 },
};

const executeAdHocMock = vi.fn().mockResolvedValue(executionResult);

vi.mock('./huntingApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./huntingApi')>()),
  listHuntableFields: () => Promise.resolve(fields),
  listHunts: () => Promise.resolve(savedHuntsPage),
  executeAdHoc: (...args: unknown[]) => executeAdHocMock(...args),
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <HuntingPage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('HuntingPage', () => {
  it('renders the saved hunts list and the query builder', async () => {
    renderPage();

    expect(await screen.findByText('Mimikatz sur srv-web')).toBeInTheDocument();
    expect(screen.getByText(/Exécutée/)).toBeInTheDocument();
    expect(screen.getAllByLabelText('Champ')).toHaveLength(1);
  });

  it('executes an ad hoc hunt and shows the matching alerts', async () => {
    renderPage();
    await screen.findByText('Mimikatz sur srv-web');

    // Le champ par défaut est SEVERITY : "Valeur" est un select MUI, pas
    // un input natif — ouverture puis sélection de l'option.
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Valeur' }));
    fireEvent.click(await screen.findByRole('option', { name: 'CRITICAL' }));
    // Le bouton icône "▶" de la liste des chasses sauvegardées partage le
    // même nom accessible (attribut title) : on cible celui du constructeur
    // par son libellé visible.
    const runButton = screen
      .getAllByRole('button', { name: 'Exécuter' })
      .find((button) => button.textContent === 'Exécuter');
    fireEvent.click(runButton!);

    await waitFor(() => expect(executeAdHocMock).toHaveBeenCalled());
    expect(await screen.findByText('PowerShell execution detected')).toBeInTheDocument();
    expect(screen.getByText(/1 correspondance/)).toBeInTheDocument();
  });

  it('adds and removes condition rows', async () => {
    renderPage();
    await screen.findByText('Mimikatz sur srv-web');

    fireEvent.click(screen.getByRole('button', { name: /Ajouter une condition/ }));
    expect(screen.getAllByLabelText('Champ')).toHaveLength(2);
  });
});
