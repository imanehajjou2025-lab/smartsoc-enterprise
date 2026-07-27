import Chip from '@mui/material/Chip';
import type { CaseStatus, CaseTaskStatus } from './investigationsApi';

export const CASE_STATUS_LABELS: Record<CaseStatus, string> = {
  OPEN: 'Ouvert',
  IN_PROGRESS: 'En cours',
  CLOSED: 'Clôturé',
};

const STATUS_COLORS: Record<CaseStatus, 'info' | 'warning' | 'default'> = {
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
  return <Chip label={CASE_STATUS_LABELS[status]} size="small" color={STATUS_COLORS[status]} />;
}
