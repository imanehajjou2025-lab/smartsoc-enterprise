import { api } from '../../shared/api/client';
import type { Alert, AlertSeverity, PageResponse } from '../alerts/alertsApi';

export type IncidentStatus = 'OPEN' | 'INVESTIGATING' | 'CONTAINED' | 'RESOLVED' | 'CLOSED';

export type IncidentEventType =
  | 'CREATED'
  | 'STATUS_CHANGED'
  | 'ASSIGNED'
  | 'UNASSIGNED'
  | 'NOTE'
  | 'ALERT_LINKED'
  | 'ALERT_UNLINKED';

export interface Incident {
  id: string;
  reference: string;
  title: string;
  description: string | null;
  severity: AlertSeverity;
  status: IncidentStatus;
  assigneeUsername: string | null;
  openedAt: string;
}

export interface TimelineEntry {
  type: IncidentEventType;
  message: string;
  author: string;
  occurredAt: string;
}

export interface IncidentDetail {
  incident: Incident;
  linkedAlerts: Alert[];
  timeline: TimelineEntry[];
}

export interface IncidentFilters {
  status?: IncidentStatus | '';
  severity?: AlertSeverity | '';
  assignee?: string;
  page: number;
  size: number;
}

/** Transitions autorisées — miroir du cycle de vie du domaine backend. */
export const ALLOWED_TRANSITIONS: Record<IncidentStatus, IncidentStatus[]> = {
  OPEN: ['INVESTIGATING', 'CLOSED'],
  INVESTIGATING: ['CONTAINED', 'RESOLVED'],
  CONTAINED: ['RESOLVED'],
  RESOLVED: ['CLOSED', 'INVESTIGATING'],
  CLOSED: [],
};

export async function listIncidents(filters: IncidentFilters): Promise<PageResponse<Incident>> {
  const params = new URLSearchParams();
  if (filters.status) params.set('status', filters.status);
  if (filters.severity) params.set('severity', filters.severity);
  if (filters.assignee) params.set('assignee', filters.assignee);
  params.set('page', String(filters.page));
  params.set('size', String(filters.size));
  const { data } = await api.get<PageResponse<Incident>>(`/incidents?${params}`);
  return data;
}

export async function getIncident(id: string): Promise<IncidentDetail> {
  const { data } = await api.get<IncidentDetail>(`/incidents/${id}`);
  return data;
}

export async function createIncident(payload: {
  title: string;
  description?: string;
  severity: AlertSeverity;
}): Promise<Incident> {
  const { data } = await api.post<Incident>('/incidents', payload);
  return data;
}

export async function escalateFromAlert(alertId: string): Promise<Incident> {
  const { data } = await api.post<Incident>(`/incidents/from-alert/${alertId}`);
  return data;
}

export async function updateIncidentStatus(id: string, status: IncidentStatus): Promise<Incident> {
  const { data } = await api.patch<Incident>(`/incidents/${id}/status`, { status });
  return data;
}

export async function assignIncident(id: string, username: string): Promise<Incident> {
  const { data } = await api.put<Incident>(`/incidents/${id}/assignee`, { username });
  return data;
}

export async function unassignIncident(id: string): Promise<Incident> {
  const { data } = await api.delete<Incident>(`/incidents/${id}/assignee`);
  return data;
}

export async function addIncidentNote(id: string, message: string): Promise<void> {
  await api.post(`/incidents/${id}/notes`, { message });
}

export async function unlinkAlertFromIncident(id: string, alertId: string): Promise<void> {
  await api.delete(`/incidents/${id}/alerts/${alertId}`);
}
