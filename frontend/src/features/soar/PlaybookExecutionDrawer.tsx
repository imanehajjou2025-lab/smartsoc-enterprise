import { useEffect, useState } from 'react';
import axios from 'axios';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Drawer from '@mui/material/Drawer';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import { ExecutionStatusChip, STEP_STATUS_LABELS } from './soarChips';
import {
  cancelExecution,
  completeExecution,
  getExecution,
  updateExecutionStep,
  type PlaybookExecutionStep,
  type StepStatus,
} from './soarApi';

interface Props {
  executionId: string | null;
  onClose: () => void;
}

const STEP_STATUSES: StepStatus[] = ['TODO', 'IN_PROGRESS', 'DONE', 'SKIPPED'];

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Suivi guidé d'un playbook contre un incident — cocher, noter, terminer. Aucune action n'est automatisée. */
function PlaybookExecutionDrawer({ executionId, onClose }: Props) {
  const queryClient = useQueryClient();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canWrite = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';
  const [notes, setNotes] = useState<Record<string, string>>({});

  const {
    data: execution,
    isPending,
    isError,
    error,
  } = useQuery({
    queryKey: ['playbook-execution', executionId],
    queryFn: () => getExecution(executionId!),
    enabled: Boolean(executionId),
    retry: (failureCount, err) =>
      !(axios.isAxiosError(err) && err.response?.status === 404) && failureCount < 2,
  });

  const notFound = isError && axios.isAxiosError(error) && error.response?.status === 404;
  useEffect(() => {
    if (notFound) onClose();
  }, [notFound, onClose]);

  useEffect(() => {
    if (execution) {
      setNotes(Object.fromEntries(execution.steps.map((s) => [s.id, s.note ?? ''])));
    }
  }, [execution]);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['playbook-execution', executionId] });
    void queryClient.invalidateQueries({ queryKey: ['playbook-executions'] });
  };

  const stepMutation = useMutation({
    mutationFn: ({ step, status }: { step: PlaybookExecutionStep; status: StepStatus }) =>
      updateExecutionStep(executionId!, step.id, status, notes[step.id] ?? ''),
    onSuccess: invalidate,
  });
  const completeMutation = useMutation({
    mutationFn: () => completeExecution(executionId!),
    onSuccess: invalidate,
  });
  const cancelMutation = useMutation({
    mutationFn: () => cancelExecution(executionId!),
    onSuccess: invalidate,
  });

  const mutationError = stepMutation.error ?? completeMutation.error ?? cancelMutation.error;
  const inProgress = execution?.status === 'IN_PROGRESS';

  return (
    <Drawer anchor="right" open={Boolean(executionId)} onClose={onClose}>
      <Box sx={{ width: 480, maxWidth: '92vw', p: 3 }}>
        {isPending && (
          <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
            <CircularProgress />
          </Box>
        )}
        {isError && !notFound && (
          <Alert severity="error">{problemDetail(error, 'Chargement impossible.')}</Alert>
        )}

        {execution && (
          <>
            <Stack direction="row" spacing={1} sx={{ mb: 1, alignItems: 'center' }}>
              <ExecutionStatusChip status={execution.status} />
              <Typography variant="caption" color="text.secondary">
                v{execution.playbookVersion}
              </Typography>
            </Stack>
            <Typography variant="h6" sx={{ mb: 0.5 }}>
              {execution.playbookName}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
              Démarrée {formatDate(execution.startedAt)}
              {execution.completedAt ? ` · terminée ${formatDate(execution.completedAt)}` : ''}
            </Typography>

            {mutationError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {problemDetail(mutationError, 'Action impossible.')}
              </Alert>
            )}

            <Stack spacing={2}>
              {execution.steps.map((step) => (
                <Box key={step.id}>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {step.order + 1}. {step.title}
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ mt: 0.5, alignItems: 'flex-start' }}>
                    <TextField
                      select
                      size="small"
                      value={step.status}
                      disabled={!canWrite || !inProgress || stepMutation.isPending}
                      onChange={(e) =>
                        stepMutation.mutate({ step, status: e.target.value as StepStatus })
                      }
                      sx={{ minWidth: 140 }}
                    >
                      {STEP_STATUSES.map((s) => (
                        <MenuItem key={s} value={s}>
                          {STEP_STATUS_LABELS[s]}
                        </MenuItem>
                      ))}
                    </TextField>
                    <TextField
                      size="small"
                      placeholder="Note…"
                      fullWidth
                      disabled={!canWrite || !inProgress}
                      value={notes[step.id] ?? ''}
                      onChange={(e) => setNotes((prev) => ({ ...prev, [step.id]: e.target.value }))}
                      onBlur={() => {
                        if ((notes[step.id] ?? '') !== (step.note ?? '')) {
                          stepMutation.mutate({ step, status: step.status });
                        }
                      }}
                    />
                  </Stack>
                </Box>
              ))}
            </Stack>

            {canWrite && inProgress && (
              <Stack direction="row" spacing={1} sx={{ mt: 3 }}>
                <Button
                  variant="contained"
                  color="success"
                  disabled={completeMutation.isPending}
                  onClick={() => completeMutation.mutate()}
                >
                  Terminer
                </Button>
                <Button
                  variant="outlined"
                  color="error"
                  disabled={cancelMutation.isPending}
                  onClick={() => cancelMutation.mutate()}
                >
                  Annuler
                </Button>
              </Stack>
            )}
          </>
        )}
      </Box>
    </Drawer>
  );
}

export default PlaybookExecutionDrawer;
