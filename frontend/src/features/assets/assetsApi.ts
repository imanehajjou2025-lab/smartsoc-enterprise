import { api } from '../../shared/api/client';
import type { Alert, PageResponse } from '../alerts/alertsApi';

export type AssetType =
  | 'SERVER'
  | 'WORKSTATION'
  | 'NETWORK_DEVICE'
  | 'DATABASE'
  | 'APPLICATION'
  | 'CLOUD_RESOURCE'
  | 'OTHER';

export type AssetCriticality = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type AssetExposure = 'INTERNET_FACING' | 'INTERNAL' | 'ISOLATED';

export type AssetStatus = 'ACTIVE' | 'DECOMMISSIONED';

/**
 * Statut de connexion RAPPORTÉ par un connecteur d'agents (Wazuh) — pas le
 * cycle de vie de l'actif (AssetStatus). `null` pour un actif enregistré à
 * la main, jamais rapporté par un connecteur.
 */
export type AgentConnectionStatus = 'ACTIVE' | 'DISCONNECTED' | 'NEVER_CONNECTED';

export interface Asset {
  id: string;
  hostname: string;
  displayName: string;
  type: AssetType;
  criticality: AssetCriticality;
  exposure: AssetExposure;
  ipAddress: string | null;
  owner: string | null;
  description: string | null;
  status: AssetStatus;
  registeredAt: string;
  decommissionedAt: string | null;
  operatingSystem: string | null;
  lastSeenAt: string | null;
  hardwareSummary: string | null;
  externalSource: string | null;
  agentConnectionStatus: AgentConnectionStatus | null;
}

export interface AssetFilters {
  type?: AssetType | '';
  criticality?: AssetCriticality | '';
  exposure?: AssetExposure | '';
  status?: AssetStatus | '';
  search?: string;
  page: number;
  size: number;
}

export interface RegisterAssetPayload {
  hostname: string;
  displayName?: string;
  type: AssetType;
  criticality: AssetCriticality;
  exposure: AssetExposure;
  ipAddress?: string;
  owner?: string;
  description?: string;
}

export type UpdateAssetPayload = Omit<RegisterAssetPayload, 'hostname'>;

export async function listAssets(filters: AssetFilters): Promise<PageResponse<Asset>> {
  const params = new URLSearchParams();
  if (filters.type) params.set('type', filters.type);
  if (filters.criticality) params.set('criticality', filters.criticality);
  if (filters.exposure) params.set('exposure', filters.exposure);
  if (filters.status) params.set('status', filters.status);
  if (filters.search) params.set('search', filters.search);
  params.set('page', String(filters.page));
  params.set('size', String(filters.size));
  const { data } = await api.get<PageResponse<Asset>>(`/assets?${params}`);
  return data;
}

export async function getAsset(id: string): Promise<Asset> {
  const { data } = await api.get<Asset>(`/assets/${id}`);
  return data;
}

/**
 * Résolution par hostname (ex. depuis le tiroir d'une alerte). La valeur
 * est envoyée telle quelle (encodée pour l'URL) : la normalisation
 * casse/espaces appartient au serveur. Un 404 est une information
 * métier — « aucun actif inventorié » — pas une erreur technique.
 */
export async function getAssetByHostname(hostname: string): Promise<Asset> {
  const { data } = await api.get<Asset>(`/assets/by-hostname/${encodeURIComponent(hostname)}`);
  return data;
}

export async function registerAsset(payload: RegisterAssetPayload): Promise<Asset> {
  const { data } = await api.post<Asset>('/assets', payload);
  return data;
}

export async function updateAsset(id: string, payload: UpdateAssetPayload): Promise<Asset> {
  const { data } = await api.put<Asset>(`/assets/${id}`, payload);
  return data;
}

export async function decommissionAsset(id: string): Promise<Asset> {
  const { data } = await api.post<Asset>(`/assets/${id}/decommission`);
  return data;
}

export async function reactivateAsset(id: string): Promise<Asset> {
  const { data } = await api.post<Asset>(`/assets/${id}/reactivate`);
  return data;
}

/**
 * Redémarrage RÉEL de l'agent Wazuh (ADR-014 phase 5) — effet réel sur
 * une vraie machine, jamais une simple lecture. `confirmHostname` doit
 * correspondre EXACTEMENT au hostname de l'actif (le serveur revérifie,
 * cet appel n'est qu'un relais) ; le motif est obligatoire.
 */
export async function restartAgent(
  id: string,
  confirmHostname: string,
  reason: string,
): Promise<void> {
  await api.post(`/assets/${id}/restart-agent`, { confirmHostname, reason });
}

/**
 * Active-response Wazuh `firewall-drop` (ADR-014 phase 5) — bloque une IP
 * sur le pare-feu LOCAL de l'agent ciblé, effet réel. Mêmes garde-fous que
 * `restartAgent` : `confirmHostname` revérifié côté serveur, motif
 * obligatoire ; l'IP est aussi revalidée côté serveur.
 */
export async function blockIp(
  id: string,
  confirmHostname: string,
  ipAddress: string,
  reason: string,
): Promise<void> {
  await api.post(`/assets/${id}/block-ip`, { confirmHostname, ipAddress, reason });
}

/** Alertes corrélées : totalElements = LE compteur de corrélation. */
export async function listCorrelatedAlerts(
  id: string,
  page: number,
  size: number,
): Promise<PageResponse<Alert>> {
  const { data } = await api.get<PageResponse<Alert>>(
    `/assets/${id}/alerts?page=${page}&size=${size}`,
  );
  return data;
}
