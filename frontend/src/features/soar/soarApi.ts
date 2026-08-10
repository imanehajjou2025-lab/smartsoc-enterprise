import { api } from '../../shared/api/client';
import type { PageResponse } from '../alerts/alertsApi';

export type StepStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'SKIPPED';
export type ExecutionStatus =
  'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'START_FAILED' | 'ORPHANED' | 'PARTIAL_FAILURE';

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
  /** Les deux présents ensemble, ou aucun — voir {@link isLinkedToShuffle}. */
  shuffleWorkflowId: string | null;
  shuffleWebhookPath: string | null;
}

export function isLinkedToShuffle(playbook: Playbook): boolean {
  return Boolean(playbook.shuffleWorkflowId && playbook.shuffleWebhookPath);
}

export interface DeclarePlaybookPayload {
  name: string;
  description?: string;
  steps: { order: number; title: string; description: string }[];
  shuffleWorkflowId?: string;
  shuffleWebhookPath?: string;
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
  /** Identifiant Shuffle mémorisé au déclenchement — {@code null} pour une exécution guidée manuelle. */
  externalExecutionId: string | null;
  resultSummary: string | null;
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

/**
 * Déclenchement RÉEL d'un workflow Shuffle (ADR-014 phase 5) —
 * {@code confirmPlaybookName} doit correspondre EXACTEMENT au nom du
 * playbook ciblé, le serveur revérifie de toute façon.
 */
export async function triggerShuffleExecution(
  incidentId: string,
  playbookId: string,
  confirmPlaybookName: string,
  reason: string,
): Promise<PlaybookExecution> {
  const { data } = await api.post<PlaybookExecution>(
    `/incidents/${incidentId}/playbook-executions/trigger-shuffle`,
    { playbookId, confirmPlaybookName, reason },
  );
  return data;
}

/** Réconciliation à la demande (lecture) : interroge Shuffle et aligne le statut sur la réalité. */
export async function refreshShuffleStatus(executionId: string): Promise<PlaybookExecution> {
  const { data } = await api.post<PlaybookExecution>(
    `/playbook-executions/${executionId}/refresh-shuffle-status`,
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
