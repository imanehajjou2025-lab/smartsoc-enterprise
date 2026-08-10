import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import PlaybookExecutionDrawer from './PlaybookExecutionDrawer';
import type { PlaybookExecution } from './soarApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const execution: PlaybookExecution = {
  id: 'exec-1',
  playbookId: 'pb-1',
  playbookVersion: 2,
  playbookName: 'Confinement ransomware',
  incidentId: 'inc-1',
  status: 'IN_PROGRESS',
  startedAt: '2026-07-26T10:00:00Z',
  completedAt: null,
  externalExecutionId: null,
  resultSummary: null,
  steps: [
    {
      id: 'step-1',
      order: 0,
      title: "Isoler l'hôte",
      status: 'DONE',
      note: 'Pare-feu coupé',
      completedAt: '2026-07-26T10:05:00Z',
    },
    {
      id: 'step-2',
      order: 1,
      title: "Notifier l'équipe",
      status: 'TODO',
      note: null,
      completedAt: null,
    },
  ],
};

vi.mock('./soarApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./soarApi')>()),
  getExecution: () => Promise.resolve(execution),
}));

function renderDrawer() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <PlaybookExecutionDrawer executionId="exec-1" onClose={vi.fn()} />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('PlaybookExecutionDrawer', () => {
  it('shows the execution metadata and every step with its status', async () => {
    renderDrawer();

    expect(await screen.findByText('Confinement ransomware')).toBeInTheDocument();
    expect(screen.getByText('v2')).toBeInTheDocument();
    expect(screen.getByText(/1\. Isoler l'hôte/)).toBeInTheDocument();
    expect(screen.getByText(/2\. Notifier l'équipe/)).toBeInTheDocument();
    // Le select fermé affiche le libellé du statut, la note son propre texte.
    expect(screen.getByText('Fait')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Pare-feu coupé')).toBeInTheDocument();
  });
});
