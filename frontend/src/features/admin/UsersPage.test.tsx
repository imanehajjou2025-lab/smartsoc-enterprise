import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import UsersPage from './UsersPage';
import type { PlatformUser } from './usersApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const users: PlatformUser[] = [
  {
    id: 'u-1',
    username: 'admin',
    email: 'admin@smartsoc.local',
    fullName: 'Platform Administrator',
    role: 'ADMIN',
    enabled: true,
  },
  {
    id: 'u-2',
    username: 'analyst',
    email: 'analyst@smartsoc.io',
    fullName: 'Jane Analyst',
    role: 'SOC_ANALYST',
    enabled: false,
  },
];

vi.mock('./usersApi', () => ({
  listUsers: () => Promise.resolve(users),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUser: vi.fn(),
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter>
            <UsersPage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('UsersPage', () => {
  it('renders the user list with roles and status', async () => {
    renderPage();

    expect(await screen.findByText('admin')).toBeInTheDocument();
    expect(screen.getByText('analyst')).toBeInTheDocument();
    expect(screen.getByText('Administrateur')).toBeInTheDocument();
    expect(screen.getByText('SOC Analyst')).toBeInTheDocument();
    expect(screen.getByText('Actif')).toBeInTheDocument();
    expect(screen.getByText('Désactivé')).toBeInTheDocument();
  });

  it('offers creation and per-user actions', async () => {
    renderPage();

    expect(await screen.findByRole('button', { name: 'Modifier analyst' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Supprimer analyst' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Nouvel utilisateur/i })).toBeInTheDocument();
  });
});
