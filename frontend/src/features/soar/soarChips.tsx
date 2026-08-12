import Chip from '@mui/material/Chip';
import { useTheme } from '@mui/material/styles';
import { resolveChipColor, softChipSx, type ChipPaletteColor } from '../../shared/components/chipStyles';
import type { ExecutionStatus, StepStatus } from './soarApi';

export const EXECUTION_STATUS_LABELS: Record<ExecutionStatus, string> = {
  IN_PROGRESS: 'En cours',
  COMPLETED: 'Terminée',
  CANCELLED: 'Annulée',
  START_FAILED: 'Échec du démarrage',
  ORPHANED: 'Orpheline',
  PARTIAL_FAILURE: 'Échec',
};

const EXECUTION_STATUS_COLORS: Record<ExecutionStatus, ChipPaletteColor> = {
  IN_PROGRESS: 'info',
  COMPLETED: 'success',
  CANCELLED: 'default',
  START_FAILED: 'error',
  ORPHANED: 'warning',
  PARTIAL_FAILURE: 'error',
};

export function ExecutionStatusChip({ status }: { status: ExecutionStatus }) {
  const theme = useTheme();
  const color = resolveChipColor(theme, EXECUTION_STATUS_COLORS[status]);
  return <Chip label={EXECUTION_STATUS_LABELS[status]} size="small" sx={softChipSx(color)} />;
}

export const STEP_STATUS_LABELS: Record<StepStatus, string> = {
  TODO: 'À faire',
  IN_PROGRESS: 'En cours',
  DONE: 'Fait',
  SKIPPED: 'Non applicable',
};
