import { api } from '../../shared/api/client';
import type { PageResponse } from '../alerts/alertsApi';

export type StepStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'SKIPPED';
export type ExecutionStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

/** {@code order} est ignoré à l'envoi : le serveur le dérive toujours de la position dans la liste. */
export interface PlaybookStep {
  order: number;
  title: string;
  description: string | null;
}

export interface Playbook {
  id: string;
  name: string;
  description: string | null;
  version: number;
  steps: PlaybookStep[];
  archived: boolean;
}

export interface DeclarePlaybookPayload {
  name: string;
  description?: string;
  steps: { order: number; title: string; description: string }[];
}

export interface PlaybookExecutionStep {
  id: string;
  order: number;
  title: string;
  status: StepStatus;
  note: string | null;
  completedAt: string | null;
}

export interface PlaybookExecution {
  id: string;
  playbookId: string;
  playbookVersion: number;
  playbookName: string;
  incidentId: string;
  status: ExecutionStatus;
  startedAt: string;
  completedAt: string | null;
  steps: PlaybookExecutionStep[];
}

export async function listPlaybooks(
  search: string,
  includeArchived: boolean,
  page: number,
  size: number,
): Promise<PageResponse<Playbook>> {
  const params = new URLSearchParams();
  if (search) params.set('search', search);
  if (includeArchived) params.set('includeArchived', 'true');
  params.set('page', String(page));
  params.set('size', String(size));
  const { data } = await api.get<PageResponse<Playbook>>(`/playbooks?${params}`);
  return data;
}

export async function getPlaybook(id: string): Promise<Playbook> {
  const { data } = await api.get<Playbook>(`/playbooks/${id}`);
  return data;
}

export async function createPlaybook(payload: DeclarePlaybookPayload): Promise<Playbook> {
  const { data } = await api.post<Playbook>('/playbooks', payload);
  return data;
}

export async function updatePlaybook(
  id: string,
  payload: DeclarePlaybookPayload,
): Promise<Playbook> {
  const { data } = await api.patch<Playbook>(`/playbooks/${id}`, payload);
  return data;
}

export async function archivePlaybook(id: string): Promise<Playbook> {
  const { data } = await api.post<Playbook>(`/playbooks/${id}/archive`);
  return data;
}

/** Démarre un suivi de playbook contre un incident — fige une copie des étapes. */
export async function startExecution(
  incidentId: string,
  playbookId: string,
): Promise<PlaybookExecution> {
  const { data } = await api.post<PlaybookExecution>(
    `/incidents/${incidentId}/playbook-executions`,
    { playbookId },
  );
  return data;
}

export async function listExecutionsForIncident(
  incidentId: string,
): Promise<PageResponse<PlaybookExecution>> {
  const { data } = await api.get<PageResponse<PlaybookExecution>>(
    `/incidents/${incidentId}/playbook-executions`,
  );
  return data;
}

export async function getExecution(id: string): Promise<PlaybookExecution> {
  const { data } = await api.get<PlaybookExecution>(`/playbook-executions/${id}`);
  return data;
}

export async function updateExecutionStep(
  executionId: string,
  stepId: string,
  status: StepStatus,
  note: string,
): Promise<PlaybookExecution> {
  const { data } = await api.patch<PlaybookExecution>(
    `/playbook-executions/${executionId}/steps/${stepId}`,
    { status, note },
  );
  return data;
}

export async function completeExecution(id: string): Promise<PlaybookExecution> {
  const { data } = await api.post<PlaybookExecution>(`/playbook-executions/${id}/complete`);
  return data;
}

export async function cancelExecution(id: string): Promise<PlaybookExecution> {
  const { data } = await api.post<PlaybookExecution>(`/playbook-executions/${id}/cancel`);
  return data;
}
