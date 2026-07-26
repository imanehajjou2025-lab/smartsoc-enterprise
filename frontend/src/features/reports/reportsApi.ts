import { api } from '../../shared/api/client';
import type { AlertSeverity, AlertStatus, PageResponse } from '../alerts/alertsApi';

export interface AlertMetrics {
  total: number;
  bySeverity: Partial<Record<AlertSeverity, number>>;
  byStatus: Partial<Record<AlertStatus, number>>;
}

export interface IncidentMetrics {
  opened: number;
  closed: number;
  avgResolutionHours: number | null;
}

export interface SoarMetrics {
  started: number;
  completed: number;
  cancelled: number;
}

export interface MitreTechniqueCount {
  attackId: string;
  alertCount: number;
}

export interface ReportMetrics {
  alerts: AlertMetrics;
  incidents: IncidentMetrics;
  soar: SoarMetrics;
  huntQueriesExecuted: number;
  mitreDistinctTechniquesCovered: number;
  mitreTopTechniques: MitreTechniqueCount[];
}

export interface ReportSummary {
  id: string;
  title: string;
  periodStart: string;
  periodEnd: string;
  generatedAt: string;
  generatedBy: string;
}

export interface Report extends ReportSummary {
  metrics: ReportMetrics;
}

export interface GenerateReportPayload {
  title: string;
  periodStart: string;
  periodEnd: string;
}

export async function listReports(
  page: number,
  size: number,
): Promise<PageResponse<ReportSummary>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  const { data } = await api.get<PageResponse<ReportSummary>>(`/reports?${params}`);
  return data;
}

export async function getReport(id: string): Promise<Report> {
  const { data } = await api.get<Report>(`/reports/${id}`);
  return data;
}

export async function generateReport(payload: GenerateReportPayload): Promise<Report> {
  const { data } = await api.post<Report>('/reports', payload);
  return data;
}

/** Déclenche un téléchargement navigateur — la réponse est un blob, pas du JSON. */
async function downloadExport(id: string, format: 'csv' | 'pdf', filename: string): Promise<void> {
  const { data } = await api.get<Blob>(`/reports/${id}/export/${format}`, { responseType: 'blob' });
  const url = URL.createObjectURL(data);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function downloadReportCsv(report: ReportSummary): Promise<void> {
  return downloadExport(report.id, 'csv', `${report.title}.csv`);
}

export function downloadReportPdf(report: ReportSummary): Promise<void> {
  return downloadExport(report.id, 'pdf', `${report.title}.pdf`);
}
