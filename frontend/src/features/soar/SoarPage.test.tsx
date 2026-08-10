import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import SoarPage from './SoarPage';
import type { Playbook } from './soarApi';
import type { PageResponse } from '../alerts/alertsApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const playbook: Playbook = {
  id: 'pb-1',
  name: 'Confinement ransomware',
  description: 'Isoler puis analyser',
  version: 1,
  steps: [
    { order: 0, title: "Isoler l'hôte", description: null },
    { order: 1, title: "Notifier l'équipe", description: null },
  ],
  archived: false,
  shuffleWorkflowId: null,
  shuffleWebhookPath: null,
};

const playbooksPage: PageResponse<Playbook> = {
  items: [playbook],
  totalElements: 1,
  page: 0,
  size: 25,
  totalPages: 1,
};

vi.mock('./soarApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./soarApi')>()),
  listPlaybooks: () => Promise.resolve(playbooksPage),
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <SoarPage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('SoarPage', () => {
  it('renders the playbook catalog with its step count and status', async () => {
    renderPage();

    expect(await screen.findByText('Confinement ransomware')).toBeInTheDocument();
    expect(screen.getByText('v1')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('Actif')).toBeInTheDocument();
  });

  it('filters by search term', async () => {
    renderPage();
    await screen.findByText('Confinement ransomware');

    fireEvent.change(screen.getByLabelText('Rechercher'), { target: { value: 'ransomware' } });
    await waitFor(() => expect(screen.getByLabelText('Rechercher')).toHaveValue('ransomware'));
    expect(screen.getByText('Confinement ransomware')).toBeInTheDocument();
  });
});
