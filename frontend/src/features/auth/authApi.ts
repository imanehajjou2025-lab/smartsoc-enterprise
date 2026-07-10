import { api } from '../../shared/api/client';

export type Role = 'ADMIN' | 'SOC_MANAGER' | 'SOC_ANALYST' | 'VIEWER';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface SessionUser {
  username: string;
  userId: string;
  role: Role;
}

export async function loginRequest(username: string, password: string): Promise<TokenResponse> {
  const { data } = await api.post<TokenResponse>('/auth/login', { username, password });
  return data;
}

export async function fetchMe(): Promise<SessionUser> {
  const { data } = await api.get<SessionUser>('/auth/me');
  return data;
}

export async function logoutRequest(refreshToken: string): Promise<void> {
  await api.post('/auth/logout', { refreshToken });
}
