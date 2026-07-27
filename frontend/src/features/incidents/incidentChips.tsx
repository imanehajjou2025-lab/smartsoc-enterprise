import Chip from '@mui/material/Chip';
import type { IncidentStatus } from './incidentsApi';

export const INCIDENT_STATUS_LABELS: Record<IncidentStatus, string> = {
  OPEN: 'Ouvert',
  INVESTIGATING: 'Investigation',
  CONTAINED: 'Contenu',
  RESOLVED: 'Résolu',
  CLOSED: 'Clôturé',
};

const STATUS_COLORS: Record<
  IncidentStatus,
  'info' | 'warning' | 'secondary' | 'success' | 'default'
> = {
  OPEN: 'info',
  INVESTIGATING: 'warning',
  CONTAINED: 'secondary',
  RESOLVED: 'success',
  CLOSED: 'default',
};

export function IncidentStatusChip({ status }: { status: IncidentStatus }) {
  return <Chip label={INCIDENT_STATUS_LABELS[status]} size="small" color={STATUS_COLORS[status]} />;
}
