import Chip from '@mui/material/Chip';
import { useTheme } from '@mui/material/styles';
import { severityColors } from '../../app/theme';
import {
  resolveChipColor,
  softChipSx,
  type ChipPaletteColor,
} from '../../shared/components/chipStyles';
import type { AiZone, AlertSeverity, AlertStatus } from './alertsApi';

export const STATUS_LABELS: Record<AlertStatus, string> = {
  NEW: 'Nouvelle',
  ACKNOWLEDGED: 'Prise en charge',
  IN_PROGRESS: 'En cours',
  RESOLVED: 'Résolue',
  FALSE_POSITIVE: 'Faux positif',
};

/** Sévérité : couleur issue de la palette centralisée du thème, badge doux (lisible dans les deux modes). */
export function SeverityChip({ severity }: { severity: AlertSeverity }) {
  const color = severityColors[severity.toLowerCase() as keyof typeof severityColors];
  return <Chip label={severity} size="small" sx={{ ...softChipSx(color), minWidth: 82 }} />;
}

const STATUS_CHIP_COLORS: Record<AlertStatus, ChipPaletteColor> = {
  NEW: 'info',
  ACKNOWLEDGED: 'warning',
  IN_PROGRESS: 'secondary',
  RESOLVED: 'success',
  FALSE_POSITIVE: 'default',
};

export function StatusChip({ status }: { status: AlertStatus }) {
  const theme = useTheme();
  const color = resolveChipColor(theme, STATUS_CHIP_COLORS[status]);
  return <Chip label={STATUS_LABELS[status]} size="small" sx={softChipSx(color)} />;
}

export const AI_ZONE_LABELS: Record<AiZone, string> = {
  SOAR_ESCALATION: 'Escalade SOAR',
  ANALYST_REVIEW: 'Revue analyste',
  ARCHIVE: 'Archive',
};

const AI_ZONE_CHIP_COLORS: Record<AiZone, ChipPaletteColor> = {
  SOAR_ESCALATION: 'error',
  ANALYST_REVIEW: 'warning',
  ARCHIVE: 'default',
};

/** Zone de routage recommandée par le classifieur (contrat v1.1.0, complémentaire au verdict TP/FP). */
export function AiZoneChip({ zone }: { zone: AiZone }) {
  const theme = useTheme();
  const color = resolveChipColor(theme, AI_ZONE_CHIP_COLORS[zone]);
  return <Chip label={AI_ZONE_LABELS[zone]} size="small" sx={softChipSx(color)} />;
}
