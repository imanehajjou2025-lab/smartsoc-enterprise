import { api } from '../../shared/api/client';
import type { Alert, PageResponse } from '../alerts/alertsApi';

/** Une tactique de la matrice — identifiée par son shortName ATT&CK. */
export interface MitreTactic {
  attackId: string;
  shortName: string;
  name: string;
}

/**
 * Une technique du catalogue. `tactics` porte les shortNames ATT&CK, dans
 * l'ordre des colonnes de la matrice — on joint dessus pour la heatmap.
 */
export interface MitreTechnique {
  attackId: string;
  subTechnique: boolean;
  parentId: string | null;
  name: string;
  description: string | null;
  url: string | null;
  tactics: string[];
  deprecated: boolean;
  attackVersion: string | null;
}

export interface TechniqueFilters {
  tactic?: string;
  search?: string;
  includeDeprecated?: boolean;
  page: number;
  size: number;
}

/**
 * Une technique citée par une alerte, résolue contre le catalogue.
 * `technique` est null quand l'identifiant est inconnu (hors format ou
 * absent) — mais `rawId` le garde toujours VISIBLE, jamais masqué.
 */
export interface ResolvedTechnique {
  rawId: string;
  known: boolean;
  technique: MitreTechnique | null;
}

/** Une case de la heatmap : nombre d'alertes citant cette technique. */
export interface MitreCoverage {
  attackId: string;
  alertCount: number;
}

export async function listTactics(): Promise<MitreTactic[]> {
  const { data } = await api.get<MitreTactic[]>('/mitre/tactics');
  return data;
}

export async function listTechniques(
  filters: TechniqueFilters,
): Promise<PageResponse<MitreTechnique>> {
  const params = new URLSearchParams();
  if (filters.tactic) params.set('tactic', filters.tactic);
  if (filters.search) params.set('search', filters.search);
  if (filters.includeDeprecated) params.set('includeDeprecated', 'true');
  params.set('page', String(filters.page));
  params.set('size', String(filters.size));
  const { data } = await api.get<PageResponse<MitreTechnique>>(`/mitre/techniques?${params}`);
  return data;
}

export async function getTechnique(attackId: string): Promise<MitreTechnique> {
  const { data } = await api.get<MitreTechnique>(
    `/mitre/techniques/${encodeURIComponent(attackId)}`,
  );
  return data;
}

/**
 * Retro-hunt : alertes citant cette technique. totalElements = LE compteur
 * de corrélation (même prédicat que la liste). La correspondance est
 * calculée à la lecture : une alerte d'hier remonte sans rattrapage.
 */
export async function listTechniqueAlerts(
  attackId: string,
  page: number,
  size: number,
): Promise<PageResponse<Alert>> {
  const { data } = await api.get<PageResponse<Alert>>(
    `/mitre/techniques/${encodeURIComponent(attackId)}/alerts?page=${page}&size=${size}`,
  );
  return data;
}

/** Couverture : nombre d'alertes par technique — la donnée de la heatmap. */
export async function getCoverage(): Promise<MitreCoverage[]> {
  const { data } = await api.get<MitreCoverage[]>('/mitre/coverage');
  return data;
}

/**
 * Enrichissement MITRE d'une alerte : ses techniques résolues contre le
 * catalogue. Strictement en lecture ; les identifiants inconnus restent
 * dans la liste (rawId), simplement non résolus.
 */
export async function getAlertMitre(alertId: string): Promise<ResolvedTechnique[]> {
  const { data } = await api.get<ResolvedTechnique[]>(`/alerts/${alertId}/mitre`);
  return data;
}
