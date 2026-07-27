import Chip from '@mui/material/Chip';
import { useTheme } from '@mui/material/styles';
import {
  resolveChipColor,
  softChipSx,
  type ChipPaletteColor,
} from '../../shared/components/chipStyles';
import type { CaseStatus, CaseTaskStatus } from './investigationsApi';

export const CASE_STATUS_LABELS: Record<CaseStatus, string> = {
  OPEN: 'Ouvert',
  IN_PROGRESS: 'En cours',
  CLOSED: 'Clôturé',
};

const STATUS_COLORS: Record<CaseStatus, ChipPaletteColor> = {
  OPEN: 'info',
  IN_PROGRESS: 'warning',
  CLOSED: 'default',
};

export const TASK_STATUS_LABELS: Record<CaseTaskStatus, string> = {
  TODO: 'À faire',
  IN_PROGRESS: 'En cours',
  DONE: 'Terminée',
};

export function CaseStatusChip({ status }: { status: CaseStatus }) {
  const theme = useTheme();
  const color = resolveChipColor(theme, STATUS_COLORS[status]);
  return <Chip label={CASE_STATUS_LABELS[status]} size="small" sx={softChipSx(color)} />;
}
