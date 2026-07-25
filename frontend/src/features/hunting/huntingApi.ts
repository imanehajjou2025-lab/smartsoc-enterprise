import { api } from '../../shared/api/client';
import type { Alert, AlertSeverity, AlertStatus, PageResponse } from '../alerts/alertsApi';

export type HuntField =
  | 'SEVERITY'
  | 'STATUS'
  | 'SOURCE'
  | 'HOSTNAME'
  | 'RULE_ID'
  | 'DETECTED_AT'
  | 'MITRE_TECHNIQUE'
  | 'RAW_PAYLOAD_TEXT';

export type HuntOperator = 'EQUALS' | 'CONTAINS' | 'GREATER_THAN' | 'LESS_THAN';
export type HuntLogicalOperator = 'AND' | 'OR' | 'NOT';
export type HuntVisibility = 'PRIVATE' | 'TEAM';

/**
 * Un nœud de l'arbre de critères — même forme récursive que le backend
 * (discriminant `kind`). La V1 de la console ne construit qu'un groupe AND
 * de conditions plates (miroir de la restriction serveur), mais le type
 * accepte déjà l'arbre complet : aucune rupture le jour de l'imbrication.
 */
export type HuntNode = HuntCondition | HuntGroup;

export interface HuntCondition {
  kind: 'CONDITION';
  field: HuntField;
  operator: HuntOperator;
  value: string;
}

export interface HuntGroup {
  kind: 'GROUP';
  operator: HuntLogicalOperator;
  children: HuntNode[];
}

export interface HuntFieldDescriptor {
  field: HuntField;
  allowedOperators: HuntOperator[];
}

export interface HuntQuery {
  id: string;
  name: string;
  description: string | null;
  criteria: HuntGroup;
  visibility: HuntVisibility;
  lastExecutedAt: string | null;
}

export interface DeclareHuntPayload {
  name: string;
  description?: string;
  criteria: HuntGroup;
  visibility?: HuntVisibility;
}

/** Métadonnées d'une exécution — sans le contenu des résultats. */
export interface HuntExecutionSummary {
  huntId: string | null;
  executedAt: string;
  tookMillis: number;
  matchedCount: number;
  truncated: boolean;
}

/** Répartition du jeu de résultats de cette exécution (pas du parc entier). */
export interface HuntStatistics {
  bySeverity: Partial<Record<AlertSeverity, number>>;
  byStatus: Partial<Record<AlertStatus, number>>;
  bySource: Record<string, number>;
}

export interface HuntExecutionResult {
  summary: HuntExecutionSummary;
  statistics: HuntStatistics;
  matches: PageResponse<Alert>;
}

export function flatAndGroup(conditions: HuntCondition[]): HuntGroup {
  return { kind: 'GROUP', operator: 'AND', children: conditions };
}

export async function listHuntableFields(): Promise<HuntFieldDescriptor[]> {
  const { data } = await api.get<HuntFieldDescriptor[]>('/hunts/fields');
  return data;
}

export async function listHunts(search: string, page: number, size: number) {
  const params = new URLSearchParams();
  if (search) params.set('search', search);
  params.set('page', String(page));
  params.set('size', String(size));
  const { data } = await api.get<PageResponse<HuntQuery>>(`/hunts?${params}`);
  return data;
}

export async function getHunt(id: string): Promise<HuntQuery> {
  const { data } = await api.get<HuntQuery>(`/hunts/${id}`);
  return data;
}

export async function createHunt(payload: DeclareHuntPayload): Promise<HuntQuery> {
  const { data } = await api.post<HuntQuery>('/hunts', payload);
  return data;
}

export async function updateHunt(id: string, payload: DeclareHuntPayload): Promise<HuntQuery> {
  const { data } = await api.patch<HuntQuery>(`/hunts/${id}`, payload);
  return data;
}

export async function deleteHunt(id: string): Promise<void> {
  await api.delete(`/hunts/${id}`);
}

/** Exécute une chasse sauvegardée — marque son horodatage de dernière exécution. */
export async function executeHunt(
  id: string,
  page: number,
  size: number,
): Promise<HuntExecutionResult> {
  const { data } = await api.post<HuntExecutionResult>(
    `/hunts/${id}/execute?page=${page}&size=${size}`,
  );
  return data;
}

/** Exécution ad hoc, sans sauvegarde — aucune écriture. */
export async function executeAdHoc(
  criteria: HuntGroup,
  page: number,
  size: number,
): Promise<HuntExecutionResult> {
  const { data } = await api.post<HuntExecutionResult>(`/hunts/execute?page=${page}&size=${size}`, {
    criteria,
  });
  return data;
}
