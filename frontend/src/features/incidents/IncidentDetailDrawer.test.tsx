import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import authReducer, { type AuthState } from '../auth/authSlice';
import IncidentDetailDrawer from './IncidentDetailDrawer';
import type { IncidentDetail } from './incidentsApi';
import type { Playbook } from '../soar/soarApi';
import type { PageResponse } from '../alerts/alertsApi';
import { theme } from '../../app/theme';

const incidentDetail: IncidentDetail = {
  incident: {
    id: 'inc-1',
    reference: 'INC-2026-0004',
    title: 'Executable file dropped in folder commonly used by malware',
    description: null,
    severity: 'CRITICAL',
    status: 'OPEN',
    assigneeUsername: null,
    openedAt: '2026-08-09T23:58:28Z',
  },
  linkedAlerts: [],
  timeline: [],
};

const linkedPlaybook: Playbook = {
  id: 'pb-1',
  name: 'Confinement ransomware',
  description: null,
  version: 1,
  steps: [{ order: 0, title: 'Isoler le poste', description: null }],
  archived: false,
  shuffleWorkflowId: 'fb0e09e3-402f-4d20-9bc1-f7fa845d4314',
  shuffleWebhookPath: 'webhook_a0fa6c78-fa6c-41a1-ac56-3c7f514ba8f4',
};

const unlinkedPlaybook: Playbook = {
  id: 'pb-2',
  name: 'Procédure documentaire seule',
  description: null,
  version: 1,
  steps: [{ order: 0, title: 'Consigner', description: null }],
  archived: false,
  shuffleWorkflowId: null,
  shuffleWebhookPath: null,
};

const emptyExecutions: PageResponse<never> = {
  items: [],
  totalElements: 0,
  page: 0,
  size: 25,
  totalPages: 0,
};

const playbooksPage: PageResponse<Playbook> = {
  items: [linkedPlaybook, unlinkedPlaybook],
  totalElements: 2,
  page: 0,
  size: 100,
  totalPages: 1,
};

const triggerShuffleExecutionMock = vi.fn();

vi.mock('./incidentsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./incidentsApi')>()),
  getIncident: () => Promise.resolve(incidentDetail),
}));

vi.mock('../soar/soarApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../soar/soarApi')>()),
  listExecutionsForIncident: () => Promise.resolve(emptyExecutions),
  listPlaybooks: () => Promise.resolve(playbooksPage),
  triggerShuffleExecution: (...args: unknown[]) => triggerShuffleExecutionMock(...args),
}));

function renderDrawer(role: 'SOC_ANALYST' | 'VIEWER') {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: {
        user: { username: 'analyst', userId: 'u-1', role, fullName: null },
        status: 'authenticated',
        loginError: null,
        loginPending: false,
      } satisfies AuthState,
    },
  });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <IncidentDetailDrawer incidentId="inc-1" onClose={() => {}} />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('IncidentDetailDrawer — déclenchement Shuffle', () => {
  it('ne propose que les playbooks liés à un workflow Shuffle dans le sélecteur', async () => {
    renderDrawer('SOC_ANALYST');

    fireEvent.click(await screen.findByRole('button', { name: /Déclencher via Shuffle/i }));
    fireEvent.mouseDown(await screen.findByLabelText('Playbook lié à Shuffle'));

    expect(await screen.findByText('Confinement ransomware (v1)')).toBeInTheDocument();
    expect(screen.queryByText(/Procédure documentaire seule/)).not.toBeInTheDocument();
  });

  it('exige le nom exact du playbook avant d’activer la confirmation', async () => {
    renderDrawer('SOC_ANALYST');

    fireEvent.click(await screen.findByRole('button', { name: /Déclencher via Shuffle/i }));
    fireEvent.mouseDown(await screen.findByLabelText('Playbook lié à Shuffle'));
    fireEvent.click(await screen.findByText('Confinement ransomware (v1)'));

    const confirmButton = await screen.findByRole('button', { name: 'Confirmer le déclenchement' });
    expect(confirmButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Nom du playbook/), {
      target: { value: 'mauvais nom' },
    });
    fireEvent.change(screen.getByLabelText(/Motif/), { target: { value: 'test' } });
    expect(confirmButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Nom du playbook/), {
      target: { value: 'Confinement ransomware' },
    });
    expect(confirmButton).not.toBeDisabled();
  });

  it('envoie le déclenchement avec le playbook, le nom confirmé et le motif', async () => {
    triggerShuffleExecutionMock.mockResolvedValueOnce({
      id: 'exec-1',
      playbookId: 'pb-1',
      playbookVersion: 1,
      playbookName: 'Confinement ransomware',
      incidentId: 'inc-1',
      status: 'IN_PROGRESS',
      startedAt: '2026-08-10T20:00:00Z',
      completedAt: null,
      externalExecutionId: 'exec-ext-1',
      resultSummary: null,
      steps: [],
    });
    renderDrawer('SOC_ANALYST');

    fireEvent.click(await screen.findByRole('button', { name: /Déclencher via Shuffle/i }));
    fireEvent.mouseDown(await screen.findByLabelText('Playbook lié à Shuffle'));
    fireEvent.click(await screen.findByText('Confinement ransomware (v1)'));
    fireEvent.change(screen.getByLabelText(/Nom du playbook/), {
      target: { value: 'Confinement ransomware' },
    });
    fireEvent.change(screen.getByLabelText(/Motif/), { target: { value: 'Alerte critique' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer le déclenchement' }));

    await waitFor(() =>
      expect(triggerShuffleExecutionMock).toHaveBeenCalledWith(
        'inc-1',
        'pb-1',
        'Confinement ransomware',
        'Alerte critique',
      ),
    );
  });

  it("n'affiche aucun déclencheur pour un rôle sans droit d'écriture", async () => {
    renderDrawer('VIEWER');

    await screen.findByText('INC-2026-0004');
    expect(
      screen.queryByRole('button', { name: /Déclencher via Shuffle/i }),
    ).not.toBeInTheDocument();
  });
});
