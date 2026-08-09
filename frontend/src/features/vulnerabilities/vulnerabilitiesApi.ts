import { api } from '../../shared/api/client';
import type { PageResponse } from '../alerts/alertsApi';

export type VulnerabilitySeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'UNTRIAGED';

export type VulnerabilityStatus = 'OPEN' | 'RESOLVED';

export interface Vulnerability {
  id: string;
  assetId: string;
  source: string;
  externalId: string;
  cveId: string;
  severity: VulnerabilitySeverity;
  cvssScore: number | null;
  cvssVersion: string | null;
  description: string | null;
  packageName: string | null;
  packageVersion: string | null;
  packageArchitecture: string | null;
  detectedAt: string | null;
  publishedAt: string | null;
  reference: string | null;
  status: VulnerabilityStatus;
  firstSeenAt: string;
  lastSeenAt: string;
  resolvedAt: string | null;
}

export interface VulnerabilityFilters {
  assetId?: string;
  status?: VulnerabilityStatus | '';
  severity?: VulnerabilitySeverity | '';
  search?: string;
  page: number;
  size: number;
}

/** Vulnérabilités rattachées aux actifs (ADR-014 §1.3) — entièrement alimentées par le connecteur, lecture seule. */
export async function listVulnerabilities(
  filters: VulnerabilityFilters,
): Promise<PageResponse<Vulnerability>> {
  const params = new URLSearchParams();
  if (filters.assetId) params.set('assetId', filters.assetId);
  if (filters.status) params.set('status', filters.status);
  if (filters.severity) params.set('severity', filters.severity);
  if (filters.search) params.set('search', filters.search);
  params.set('page', String(filters.page));
  params.set('size', String(filters.size));
  const { data } = await api.get<PageResponse<Vulnerability>>(`/vulnerabilities?${params}`);
  return data;
}
