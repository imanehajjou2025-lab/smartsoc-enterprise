import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import AppLayout from './AppLayout';
import { navigation } from './navigation';
import { ThemeModeProvider } from '../../app/ThemeModeProvider';
import { store } from '../../app/store';

// jsdom : ni WebSocket ni requêtes réseau réelles — même patron que DashboardPage.test.tsx.
vi.mock('../../features/alerts/useAlertsRealtime', () => ({
  useAlertsRealtime: () => ({ connected: true }),
}));
vi.mock('../../features/dashboard/dashboardApi', () => ({
  getAlertStats: () =>
    Promise.resolve({ total: 0, bySeverity: {}, byStatus: {}, bySource: {}, timeline: [] }),
}));

function renderLayout() {
  const router = createMemoryRouter(
    [{ path: '/', element: <AppLayout />, children: [{ index: true, element: <p>contenu</p> }] }],
    { initialEntries: ['/'] },
  );
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeModeProvider>
          <RouterProvider router={router} />
        </ThemeModeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('AppLayout', () => {
  it('renders the platform title and every navigation module', () => {
    renderLayout();

    expect(screen.getAllByAltText('ISIX').length).toBeGreaterThan(0);
    for (const section of navigation) {
      for (const item of section.items) {
        expect(screen.getByRole('link', { name: item.label })).toHaveAttribute('href', item.path);
      }
    }
  });

  it('renders the routed content area', () => {
    renderLayout();
    expect(screen.getByText('contenu')).toBeInTheDocument();
  });
});
