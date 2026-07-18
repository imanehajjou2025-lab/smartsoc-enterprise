import { api } from '../../shared/api/client';
import type { Alert, AlertSeverity, PageResponse } from '../alerts/alertsApi';
import type { Incident } from '../incidents/incidentsApi';

export type CaseStatus = 'OPEN' | 'IN_PROGRESS' | 'CLOSED';

export type CaseTaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

export type CaseEventType =
  | 'CREATED'
  | 'STATUS_CHANGED'
  | 'ASSIGNED'
  | 'UNASSIGNED'
  | 'INCIDENT_LINKED'
  | 'INCIDENT_UNLINKED'
  | 'ALERT_LINKED'
  | 'ALERT_UNLINKED'
  | 'TASK_ADDED'
  | 'TASK_UPDATED'
  | 'TASK_COMPLETED'
  | 'NOTE_ADDED'
  | 'CLOSED'
  | 'FOLLOW_UP_OPENED';

export interface Case {
  id: string;
  reference: string;
  title: string;
  description: string | null;
  priority: AlertSeverity;
  status: CaseStatus;
  assigneeUsername: string | null;
  conclusion: string | null;
  originCaseId: string | null;
  openedAt: string;
  closedAt: string | null;
}

export interface CaseTask {
  id: string;
  title: string;
  status: CaseTaskStatus;
  assigneeUsername: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface CaseTimelineEntry {
  type: CaseEventType;
  message: string;
  author: string;
  occurredAt: string;
}

export interface CaseDetail {
  investigation: Case;
  linkedIncidents: Incident[];
  linkedAlerts: Alert[];
  tasks: CaseTask[];
  timeline: CaseTimelineEntry[];
  followUps: Case[];
}

export interface CaseFilters {
  status?: CaseStatus | '';
  priority?: AlertSeverity | '';
  assignee?: string;
  page: number;
  size: number;
}

/**
 * Transitions autorisées — miroir du cycle de vie du domaine backend.
 * CLOSED est strictement terminal (pas de réouverture : la reprise passe
 * par un cas de suivi) et ne s'atteint que via closeCase (conclusion
 * obligatoire), jamais par un simple changement de statut.
 */
export const ALLOWED_TRANSITIONS: Record<CaseStatus, CaseStatus[]> = {
  OPEN: ['IN_PROGRESS', 'CLOSED'],
  IN_PROGRESS: ['CLOSED'],
  CLOSED: [],
};

export async function listCases(filters: CaseFilters): Promise<PageResponse<Case>> {
  const params = new URLSearchParams();
  if (filters.status) params.set('status', filters.status);
  if (filters.priority) params.set('priority', filters.priority);
  if (filters.assignee) params.set('assignee', filters.assignee);
  params.set('page', String(filters.page));
  params.set('size', String(filters.size));
  const { data } = await api.get<PageResponse<Case>>(`/investigations?${params}`);
  return data;
}

export async function getCase(id: string): Promise<CaseDetail> {
  const { data } = await api.get<CaseDetail>(`/investigations/${id}`);
  return data;
}

export async function createCase(payload: {
  title: string;
  description?: string;
  priority: AlertSeverity;
}): Promise<Case> {
  const { data } = await api.post<Case>('/investigations', payload);
  return data;
}

export async function openCaseFromIncident(incidentId: string): Promise<Case> {
  const { data } = await api.post<Case>(`/investigations/from-incident/${incidentId}`);
  return data;
}

/** Reprise d'une enquête clôturée : nouveau cas lié au cas d'origine. */
export async function openFollowUpCase(
  originCaseId: string,
  payload: { title: string; description?: string; priority: AlertSeverity },
): Promise<Case> {
  const { data } = await api.post<Case>(`/investigations/${originCaseId}/follow-up`, payload);
  return data;
}

export async function updateCaseStatus(id: string, status: CaseStatus): Promise<Case> {
  const { data } = await api.patch<Case>(`/investigations/${id}/status`, { status });
  return data;
}

/** Clôture formelle : conclusion obligatoire, définitive. */
export async function closeCase(id: string, conclusion: string): Promise<Case> {
  const { data } = await api.post<Case>(`/investigations/${id}/close`, { conclusion });
  return data;
}

export async function assignCase(id: string, username: string): Promise<Case> {
  const { data } = await api.put<Case>(`/investigations/${id}/assignee`, { username });
  return data;
}

export async function unassignCase(id: string): Promise<Case> {
  const { data } = await api.delete<Case>(`/investigations/${id}/assignee`);
  return data;
}

export async function addCaseNote(id: string, message: string): Promise<void> {
  await api.post(`/investigations/${id}/notes`, { message });
}

export async function unlinkIncidentFromCase(id: string, incidentId: string): Promise<void> {
  await api.delete(`/investigations/${id}/incidents/${incidentId}`);
}

export async function unlinkAlertFromCase(id: string, alertId: string): Promise<void> {
  await api.delete(`/investigations/${id}/alerts/${alertId}`);
}

export async function addCaseTask(id: string, title: string): Promise<CaseTask> {
  const { data } = await api.post<CaseTask>(`/investigations/${id}/tasks`, { title });
  return data;
}

export async function updateCaseTask(
  id: string,
  taskId: string,
  payload: { title?: string; status?: CaseTaskStatus; assignee?: string },
): Promise<CaseTask> {
  const { data } = await api.patch<CaseTask>(`/investigations/${id}/tasks/${taskId}`, payload);
  return data;
}
