import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@fontsource/roboto/300.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';
import './app/scrollbars.css';
import { ThemeModeProvider } from './app/ThemeModeProvider';
import { router } from './app/routes';
import { store } from './app/store';
import { bootstrapSession } from './features/auth/authSlice';

// Restaure la session (refresh token) avant même le premier rendu utile.
void store.dispatch(bootstrapSession());

// Etat serveur (React Query) : cache court — une console SOC doit rester fraîche.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1 },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeModeProvider>
          <RouterProvider router={router} />
        </ThemeModeProvider>
      </QueryClientProvider>
    </Provider>
  </StrictMode>,
);
