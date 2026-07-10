/**
 * Stockage des tokens — compromis de sécurité documenté :
 * - L'access token vit UNIQUEMENT en mémoire (jamais dans le storage) :
 *   un XSS ne peut pas l'exfiltrer d'un coup d'œil, et il expire en 15 min.
 * - Le refresh token va dans localStorage pour survivre au rechargement de
 *   la page. Risque XSS assumé et mitigé côté backend : rotation à chaque
 *   usage + détection de réutilisation qui révoque toute la famille
 *   (voir ADR backend, PR #14).
 */
const REFRESH_TOKEN_KEY = 'smartsoc.refreshToken';

let accessToken: string | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setRefreshToken(token: string | null): void {
  if (token) {
    localStorage.setItem(REFRESH_TOKEN_KEY, token);
  } else {
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }
}

export function clearTokens(): void {
  setAccessToken(null);
  setRefreshToken(null);
}
