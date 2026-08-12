import { api } from '../../shared/api/client';
import type { PageResponse } from '../alerts/alertsApi';

export interface SecuritySettings {
  jwtAccessTokenExpirationMinutes: number;
  jwtRefreshTokenExpirationDays: number;
  ingestWebhookConfigured: boolean;
  aiToolsApiKeyConfigured: boolean;
}

export type AiServiceHealthStatus = 'UP' | 'DOWN' | 'NOT_CONFIGURED';

export interface AiServiceStatus {
  configured: boolean;
  url: string;
  status: AiServiceHealthStatus;
}

export interface AiSettings {
  mode: 'simulation' | 'live';
  classifier: AiServiceStatus;
  assistant: AiServiceStatus;
}

export interface NotificationsSettings {
  mode: 'simulation' | 'live';
  fromAddress: string;
  smtpConfigured: boolean;
}

export interface AboutInfo {
  version: string;
  javaVersion: string;
  activeProfile: string;
  startedAt: string | null;
  uptimeSeconds: number | null;
}

export type AuditAction =
  | 'LOGIN_SUCCEEDED'
  | 'LOGIN_FAILED'
  | 'USER_CREATED'
  | 'USER_UPDATED'
  | 'USER_ROLE_CHANGED'
  | 'USER_ENABLED'
  | 'USER_DISABLED'
  | 'USER_DELETED'
  | 'BACKUP_EXPORTED'
  | 'WAZUH_AGENT_RESTART_REQUESTED'
  | 'WAZUH_AGENT_FIREWALL_DROP_REQUESTED'
  | 'SHUFFLE_WORKFLOW_TRIGGER_REQUESTED'
  | 'ALERT_ASSIGNED'
  | 'ALERT_UNASSIGNED';

export interface AuditLogEntry {
  id: string;
  occurredAt: string;
  action: AuditAction;
  actorUsername: string;
  actorId: string | null;
  targetType: string | null;
  targetId: string | null;
  details: string | null;
  ipAddress: string | null;
}

export interface AuditLogFilters {
  action?: AuditAction | '';
  actorUsername?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
}

export type ConnectorType = 'WAZUH' | 'OPENSEARCH' | 'MISP' | 'VIRUSTOTAL' | 'SHUFFLE';

export type ConnectorStatus =
  'NOT_CONFIGURED' | 'DISABLED' | 'CONNECTED' | 'DEGRADED' | 'DISCONNECTED';

export type SyncOutcome = 'SUCCESS' | 'PARTIAL' | 'FAILURE';

export interface ConnectorLastSync {
  startedAt: string;
  finishedAt: string | null;
  outcome: SyncOutcome | null;
  itemsProcessed: number;
  itemsRejected: number;
  errorMessage: string | null;
}

export interface ConnectorOverview {
  type: ConnectorType;
  status: ConnectorStatus;
  detectedVersion: string | null;
  capabilities: string[];
  lastCheckedAt: string | null;
  lastSuccessfulSyncAt: string | null;
  lastError: string | null;
  lastSync: ConnectorLastSync | null;
}

/** État des connecteurs SOC (ADR-014) — un connecteur sans backend réel n'apparaît pas dans la réponse. */
export async function listConnectors(): Promise<ConnectorOverview[]> {
  const { data } = await api.get<ConnectorOverview[]>('/connectors');
  return data;
}

export async function getSecuritySettings(): Promise<SecuritySettings> {
  const { data } = await api.get<SecuritySettings>('/settings/security');
  return data;
}

export async function getAiSettings(): Promise<AiSettings> {
  const { data } = await api.get<AiSettings>('/settings/ai');
  return data;
}

export async function getNotificationsSettings(): Promise<NotificationsSettings> {
  const { data } = await api.get<NotificationsSettings>('/settings/notifications');
  return data;
}

export async function sendTestNotification(recipientEmail: string): Promise<void> {
  await api.post('/settings/notifications/test', { recipientEmail });
}

export async function getAboutInfo(): Promise<AboutInfo> {
  const { data } = await api.get<AboutInfo>('/settings/about');
  return data;
}

/** Déclenche un téléchargement navigateur — la réponse est un blob, pas du JSON. */
export async function downloadBackup(): Promise<void> {
  const response = await api.get('/settings/backup', { responseType: 'blob' });
  const disposition: string = response.headers['content-disposition'] ?? '';
  const match = /filename="?([^"]+)"?/.exec(disposition);
  const filename = match?.[1] ?? 'smartsoc-backup.dump';

  const url = URL.createObjectURL(response.data as Blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export interface PlatformHealth {
  status: 'UP' | 'DOWN' | 'UNKNOWN';
}

/**
 * `/actuator/health` n'est pas préfixé `/api/v1` (endpoint Spring Boot
 * natif, public par conception — cf. SecurityConfig) : appel direct, pas
 * via le client `api`.
 */
export async function getPlatformHealth(): Promise<PlatformHealth> {
  try {
    const response = await fetch('/actuator/health');
    const body = (await response.json()) as { status?: string };
    return { status: body.status === 'UP' ? 'UP' : 'DOWN' };
  } catch {
    return { status: 'UNKNOWN' };
  }
}

export async function listAuditLogs(
  filters: AuditLogFilters,
): Promise<PageResponse<AuditLogEntry>> {
  const params = new URLSearchParams();
  if (filters.action) params.set('action', filters.action);
  if (filters.actorUsername) params.set('actorUsername', filters.actorUsername);
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  params.set('page', String(filters.page));
  params.set('size', String(filters.size));
  const { data } = await api.get<PageResponse<AuditLogEntry>>(`/audit-logs?${params}`);
  return data;
}
