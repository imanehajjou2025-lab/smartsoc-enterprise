import type { Role } from './authApi';

/** Libellés d'affichage des rôles RBAC — uniques pour toute la console. */
export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateur',
  SOC_MANAGER: 'SOC Manager',
  SOC_ANALYST: 'SOC Analyst',
  VIEWER: 'Lecture seule',
};

export const ROLE_OPTIONS = Object.entries(ROLE_LABELS) as [Role, string][];
