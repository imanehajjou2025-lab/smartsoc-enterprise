import { api } from '../../shared/api/client';
import type { AlertSeverity, AlertStatus } from '../alerts/alertsApi';

export interface DailyCount {
  date: string;
  count: number;
}

export interface AlertStats {
  total: number;
  bySeverity: Partial<Record<AlertSeverity, number>>;
  byStatus: Partial<Record<AlertStatus, number>>;
  bySource: Record<string, number>;
  timeline: DailyCount[];
}

export async function getAlertStats(): Promise<AlertStats> {
  const { data } = await api.get<AlertStats>('/alerts/stats');
  return data;
}
