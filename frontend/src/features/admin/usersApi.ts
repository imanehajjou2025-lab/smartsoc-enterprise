import { api } from '../../shared/api/client';
import type { Role } from '../auth/authApi';

export interface PlatformUser {
  id: string;
  username: string;
  email: string;
  fullName: string;
  role: Role;
  enabled: boolean;
}

export interface CreateUserPayload {
  username: string;
  email: string;
  password: string;
  fullName: string;
  role: Role;
}

/** PATCH sémantique : les champs absents ne changent pas. */
export interface UpdateUserPayload {
  fullName?: string;
  role?: Role;
  enabled?: boolean;
}

export async function listUsers(): Promise<PlatformUser[]> {
  const { data } = await api.get<PlatformUser[]>('/users');
  return data;
}

export async function createUser(payload: CreateUserPayload): Promise<PlatformUser> {
  const { data } = await api.post<PlatformUser>('/users', payload);
  return data;
}

export async function updateUser(id: string, payload: UpdateUserPayload): Promise<PlatformUser> {
  const { data } = await api.patch<PlatformUser>(`/users/${id}`, payload);
  return data;
}

export async function deleteUser(id: string): Promise<void> {
  await api.delete(`/users/${id}`);
}
