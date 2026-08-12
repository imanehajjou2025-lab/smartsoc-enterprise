import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import TechniqueDetailDrawer from './TechniqueDetailDrawer';
import type { MitreTechnique } from './mitreApi';
import type { Alert, PageResponse } from '../alerts/alertsApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const technique: MitreTechnique = {
  attackId: 'T1059',
  subTechnique: false,
  parentId: null,
  name: 'Command and Scripting Interpreter',
  description: 'Adversaries abuse command and script interpreters.',
  url: 'https://attack.mitre.org/techniques/T1059/',
  tactics: ['execution'],
  deprecated: false,
  attackVersion: '16.1',
};

const citingAlert: Alert = {
  id: 'alert-1',
  source: 'wazuh',
  externalId: 'evt-1',
  title: 'PowerShell execution detected',
  description: null,
  severity: 'HIGH',
  status: 'NEW',
  detectedAt: '2026-07-24T10:00:00Z',
  receivedAt: '2026-07-24T10:00:01Z',
  hostname: 'srv-01',
  ruleId: '100',
  mitreTechniques: ['T1059'],
  observables: [],
  rawPayload: null,
  aiScore: null,
  aiVerdict: null,
  aiZone: null,
  aiHardOverride: false,
  aiJustifications: [],
  assignedTier: null,
  assignedToUsername: null,
};

const correlated: PageResponse<Alert> = {
  items: [citingAlert],
  totalElements: 1,
  page: 0,
  size: 10,
  totalPages: 1,
};

vi.mock('./mitreApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./mitreApi')>()),
  getTechnique: () => Promise.resolve(technique),
  listTechniqueAlerts: () => Promise.resolve(correlated),
}));

function renderDrawer() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <TechniqueDetailDrawer attackId="T1059" onClose={vi.fn()} />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('TechniqueDetailDrawer', () => {
  it('shows technique metadata and the alerts citing it (retro-hunt)', async () => {
    renderDrawer();

    expect(await screen.findByText('Command and Scripting Interpreter')).toBeInTheDocument();
    expect(screen.getByText('execution')).toBeInTheDocument();
    expect(
      screen.getByText('Adversaries abuse command and script interpreters.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Voir sur attack.mitre.org')).toBeInTheDocument();

    // Le retro-hunt : l'alerte qui cite la technique remonte dans le tiroir.
    expect(screen.getByText('PowerShell execution detected')).toBeInTheDocument();
  });
});
