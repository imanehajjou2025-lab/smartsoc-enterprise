import { api } from '../../shared/api/client';

export type AlertSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
export type AlertStatus = 'NEW' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'RESOLVED' | 'FALSE_POSITIVE';
export type AiVerdict = 'TRUE_POSITIVE' | 'FALSE_POSITIVE';

export interface Alert {
  id: string;
  source: string;
  externalId: string;
  title: string;
  description: string | null;
  severity: AlertSeverity;
  status: AlertStatus;
  detectedAt: string;
  receivedAt: string;
  hostname: string | null;
  ruleId: string | null;
  mitreTechniques: string[];
  rawPayload: string | null;
  aiScore: number | null;
  aiVerdict: AiVerdict | null;
}

export interface PageResponse<T> {
  items: T[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface AlertFilters {
  status?: AlertStatus | '';
  severity?: AlertSeverity | '';
  source?: string;
  page: number;
  size: number;
}

/**
 * Transitions autorisées — miroir du cycle de vie du domaine backend
 * (AlertStatus.allowedTransitions). Le backend reste l'autorité : une
 * transition illégale renverrait 422.
 */
export const ALLOWED_TRANSITIONS: Record<AlertStatus, AlertStatus[]> = {
  NEW: ['ACKNOWLEDGED', 'FALSE_POSITIVE'],
  ACKNOWLEDGED: ['IN_PROGRESS', 'RESOLVED', 'FALSE_POSITIVE'],
  IN_PROGRESS: ['RESOLVED', 'FALSE_POSITIVE'],
  RESOLVED: [],
  FALSE_POSITIVE: [],
};

export async function listAlerts(filters: AlertFilters): Promise<PageResponse<Alert>> {
  const params = new URLSearchParams();
  if (filters.status) params.set('status', filters.status);
  if (filters.severity) params.set('severity', filters.severity);
  if (filters.source) params.set('source', filters.source);
  params.set('page', String(filters.page));
  params.set('size', String(filters.size));
  const { data } = await api.get<PageResponse<Alert>>(`/alerts?${params}`);
  return data;
}

export async function getAlert(id: string): Promise<Alert> {
  const { data } = await api.get<Alert>(`/alerts/${id}`);
  return data;
}

export async function updateAlertStatus(id: string, status: AlertStatus): Promise<Alert> {
  const { data } = await api.patch<Alert>(`/alerts/${id}/status`, { status });
  return data;
}
