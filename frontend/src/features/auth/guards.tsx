import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import type { Role } from './authApi';

/**
 * Garde d'authentification : tant que la session se restaure (refresh au
 * chargement), un loader ; sans session, redirection vers /login en
 * mémorisant la destination pour y revenir après connexion.
 */
export function RequireAuth() {
  const status = useAppSelector((state) => state.auth.status);
  const location = useLocation();

  if (status === 'initializing') {
    return (
      <Box
        sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      >
        <CircularProgress />
      </Box>
    );
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <Outlet />;
}

/** Garde RBAC : réservé aux rôles listés, sinon page 403. */
export function RequireRole({ roles }: { roles: Role[] }) {
  const user = useAppSelector((state) => state.auth.user);

  if (!user || !roles.includes(user.role)) {
    return <Navigate to="/forbidden" replace />;
  }
  return <Outlet />;
}
