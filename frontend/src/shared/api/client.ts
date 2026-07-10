import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setAccessToken,
  setRefreshToken,
} from './tokens';

/** Client API unique de la plateforme. Tout passe par /api (proxy dev / reverse proxy prod). */
export const api = axios.create({ baseURL: '/api/v1' });

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * Refresh "single-flight" : si dix requêtes reçoivent un 401 en même temps,
 * un seul appel /auth/refresh part (la rotation des tokens rend un second
 * appel avec le même token non seulement inutile mais invalide).
 */
let refreshInFlight: Promise<void> | null = null;

async function performRefresh(): Promise<void> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }
  // Client axios nu : ne pas repasser par les intercepteurs de `api`.
  const { data } = await axios.post('/api/v1/auth/refresh', { refreshToken });
  setAccessToken(data.accessToken);
  setRefreshToken(data.refreshToken);
}

export function refreshSession(): Promise<void> {
  refreshInFlight ??= performRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean };

/** Endpoints où un 401 est une réponse métier, pas un token expiré. */
const NO_RETRY_PATHS = ['/auth/login', '/auth/refresh', '/auth/logout'];

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const config = error.config as RetriableConfig | undefined;
  const status = error.response?.status;
  const skipRetry = NO_RETRY_PATHS.some((path) => config?.url?.startsWith(path));

  if (status === 401 && config && !config._retry && !skipRetry) {
    config._retry = true;
    try {
      await refreshSession();
    } catch {
      // Session irrécupérable : purge et retour au login.
      clearTokens();
      window.location.assign('/login');
      throw error;
    }
    return api(config);
  }
  throw error;
});

/** Extrait le message lisible d'une réponse d'erreur RFC 9457 du backend. */
export function problemDetail(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { detail?: string } | undefined;
    if (data?.detail) {
      return data.detail;
    }
  }
  return fallback;
}
