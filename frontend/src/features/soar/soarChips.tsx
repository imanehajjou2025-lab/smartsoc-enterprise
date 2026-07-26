import Chip from '@mui/material/Chip';
import type { ExecutionStatus, StepStatus } from './soarApi';

export const EXECUTION_STATUS_LABELS: Record<ExecutionStatus, string> = {
  IN_PROGRESS: 'En cours',
  COMPLETED: 'Terminée',
  CANCELLED: 'Annulée',
};

const EXECUTION_STATUS_COLORS: Record<ExecutionStatus, 'info' | 'success' | 'default'> = {
  IN_PROGRESS: 'info',
  COMPLETED: 'success',
  CANCELLED: 'default',
};

export function ExecutionStatusChip({ status }: { status: ExecutionStatus }) {
  return (
    <Chip
      label={EXECUTION_STATUS_LABELS[status]}
      size="small"
      color={EXECUTION_STATUS_COLORS[status]}
    />
  );
}

export const STEP_STATUS_LABELS: Record<StepStatus, string> = {
  TODO: 'À faire',
  IN_PROGRESS: 'En cours',
  DONE: 'Fait',
  SKIPPED: 'Non applicable',
};
