import Chip from '@mui/material/Chip';
import { useTheme } from '@mui/material/styles';
import {
  resolveChipColor,
  softChipSx,
  type ChipPaletteColor,
} from '../../shared/components/chipStyles';
import type { IncidentStatus } from './incidentsApi';

export const INCIDENT_STATUS_LABELS: Record<IncidentStatus, string> = {
  OPEN: 'Ouvert',
  INVESTIGATING: 'Investigation',
  CONTAINED: 'Contenu',
  RESOLVED: 'Résolu',
  CLOSED: 'Clôturé',
};

const STATUS_COLORS: Record<IncidentStatus, ChipPaletteColor> = {
  OPEN: 'info',
  INVESTIGATING: 'warning',
  CONTAINED: 'secondary',
  RESOLVED: 'success',
  CLOSED: 'default',
};

export function IncidentStatusChip({ status }: { status: IncidentStatus }) {
  const theme = useTheme();
  const color = resolveChipColor(theme, STATUS_COLORS[status]);
  return <Chip label={INCIDENT_STATUS_LABELS[status]} size="small" sx={softChipSx(color)} />;
}
