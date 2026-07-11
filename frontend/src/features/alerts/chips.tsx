import Chip from '@mui/material/Chip';
import { severityColors } from '../../app/theme';
import type { AlertSeverity, AlertStatus } from './alertsApi';

export const STATUS_LABELS: Record<AlertStatus, string> = {
  NEW: 'Nouvelle',
  ACKNOWLEDGED: 'Prise en charge',
  IN_PROGRESS: 'En cours',
  RESOLVED: 'Résolue',
  FALSE_POSITIVE: 'Faux positif',
};

/** Sévérité : couleur issue de la palette centralisée du thème. */
export function SeverityChip({ severity }: { severity: AlertSeverity }) {
  const color = severityColors[severity.toLowerCase() as keyof typeof severityColors];
  return (
    <Chip
      label={severity}
      size="small"
      variant="outlined"
      sx={{ color, borderColor: color, fontWeight: 600, minWidth: 82 }}
    />
  );
}

const STATUS_CHIP_COLORS: Record<
  AlertStatus,
  'info' | 'warning' | 'secondary' | 'success' | 'default'
> = {
  NEW: 'info',
  ACKNOWLEDGED: 'warning',
  IN_PROGRESS: 'secondary',
  RESOLVED: 'success',
  FALSE_POSITIVE: 'default',
};

export function StatusChip({ status }: { status: AlertStatus }) {
  return (
    <Chip
      label={STATUS_LABELS[status]}
      size="small"
      color={STATUS_CHIP_COLORS[status]}
      variant="outlined"
    />
  );
}
