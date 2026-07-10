import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import AppLayout from './AppLayout';
import { navigation } from './navigation';
import { theme } from '../../app/theme';

function renderLayout() {
  const router = createMemoryRouter(
    [{ path: '/', element: <AppLayout />, children: [{ index: true, element: <p>contenu</p> }] }],
    { initialEntries: ['/'] },
  );
  return render(
    <ThemeProvider theme={theme}>
      <RouterProvider router={router} />
    </ThemeProvider>,
  );
}

describe('AppLayout', () => {
  it('renders the platform title and every navigation module', () => {
    renderLayout();

    expect(screen.getByRole('heading', { name: /SmartSOC/i })).toBeInTheDocument();
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
