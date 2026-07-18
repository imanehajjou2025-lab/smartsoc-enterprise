import { useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import LockIcon from '@mui/icons-material/Lock';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import { SeverityChip } from '../alerts/chips';
import { CaseStatusChip, CASE_STATUS_LABELS, TASK_STATUS_LABELS } from './caseChips';
import CloseCaseDialog from './CloseCaseDialog';
import CreateCaseDialog from './CreateCaseDialog';
import {
  ALLOWED_TRANSITIONS,
  addCaseNote,
  addCaseTask,
  assignCase,
  getCase,
  unassignCase,
  unlinkAlertFromCase,
  unlinkIncidentFromCase,
  updateCaseStatus,
  updateCaseTask,
  type CaseStatus,
  type CaseTask,
} from './investigationsApi';

interface Props {
  caseId: string | null;
  onClose: () => void;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
        {label}
      </Typography>
      {children}
    </Box>
  );
}

/**
 * Tiroir de détail d'un cas d'investigation. Un cas CLOSED est immuable :
 * badge dédié, tous les contrôles d'écriture masqués — seule reste
 * l'ouverture d'un cas de suivi (l'unique voie de reprise du domaine).
 */
function CaseDetailDrawer({ caseId, onClose }: Props) {
  const queryClient = useQueryClient();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canWrite = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';
  const [assignee, setAssignee] = useState('');
  const [note, setNote] = useState('');
  const [taskTitle, setTaskTitle] = useState('');
  const [closeOpen, setCloseOpen] = useState(false);
  const [followUpOpen, setFollowUpOpen] = useState(false);

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['investigation', caseId],
    queryFn: () => getCase(caseId!),
    enabled: Boolean(caseId),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['investigation', caseId] });
    void queryClient.invalidateQueries({ queryKey: ['investigations'] });
  };

  const statusMutation = useMutation({
    mutationFn: (status: CaseStatus) => updateCaseStatus(caseId!, status),
    onSuccess: invalidate,
  });
  const assignMutation = useMutation({
    mutationFn: () => assignCase(caseId!, assignee),
    onSuccess: () => {
      setAssignee('');
      invalidate();
    },
  });
  const unassignMutation = useMutation({
    mutationFn: () => unassignCase(caseId!),
    onSuccess: invalidate,
  });
  const noteMutation = useMutation({
    mutationFn: () => addCaseNote(caseId!, note),
    onSuccess: () => {
      setNote('');
      invalidate();
    },
  });
  const addTaskMutation = useMutation({
    mutationFn: () => addCaseTask(caseId!, taskTitle),
    onSuccess: () => {
      setTaskTitle('');
      invalidate();
    },
  });
  const toggleTaskMutation = useMutation({
    mutationFn: (task: CaseTask) =>
      updateCaseTask(caseId!, task.id, {
        status: task.status === 'DONE' ? 'TODO' : 'DONE',
      }),
    onSuccess: invalidate,
  });
  const unlinkIncidentMutation = useMutation({
    mutationFn: (incidentId: string) => unlinkIncidentFromCase(caseId!, incidentId),
    onSuccess: invalidate,
  });
  const unlinkAlertMutation = useMutation({
    mutationFn: (alertId: string) => unlinkAlertFromCase(caseId!, alertId),
    onSuccess: invalidate,
  });

  const mutationError =
    statusMutation.error ??
    assignMutation.error ??
    noteMutation.error ??
    addTaskMutation.error ??
    toggleTaskMutation.error ??
    unlinkIncidentMutation.error ??
    unlinkAlertMutation.error;

  const isClosed = data?.investigation.status === 'CLOSED';
  const canEdit = canWrite && !isClosed;

  return (
    <Drawer anchor="right" open={Boolean(caseId)} onClose={onClose}>
      <Box sx={{ width: 560, maxWidth: '92vw', p: 3 }}>
        {isPending && (
          <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
            <CircularProgress />
          </Box>
        )}
        {isError && (
          <Alert severity="error">{problemDetail(error, 'Chargement impossible.')}</Alert>
        )}

        {data && (
          <>
            <Stack direction="row" spacing={1} sx={{ mb: 1, alignItems: 'center' }}>
              <Typography variant="subtitle2" color="text.secondary">
                {data.investigation.reference}
              </Typography>
              <SeverityChip severity={data.investigation.priority} />
              <CaseStatusChip status={data.investigation.status} />
              {isClosed && (
                <Chip
                  icon={<LockIcon />}
                  label="Cas clôturé — immuable"
                  size="small"
                  color="default"
                />
              )}
            </Stack>
            <Typography variant="h6" sx={{ mb: 2 }}>
              {data.investigation.title}
            </Typography>

            {mutationError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {problemDetail(mutationError, 'Action impossible.')}
              </Alert>
            )}

            {isClosed && (
              <Field label="Conclusion">
                <Typography variant="body2">{data.investigation.conclusion}</Typography>
                <Typography variant="caption" color="text.secondary">
                  Clôturé le {formatDate(data.investigation.closedAt!)}
                </Typography>
              </Field>
            )}

            {canWrite && isClosed && (
              <Button
                variant="outlined"
                size="small"
                sx={{ mb: 2 }}
                onClick={() => setFollowUpOpen(true)}
              >
                Ouvrir un cas de suivi
              </Button>
            )}

            {canEdit && (
              <>
                <Field label="Changer le statut">
                  <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                    {ALLOWED_TRANSITIONS[data.investigation.status]
                      .filter((target) => target !== 'CLOSED')
                      .map((target) => (
                        <Button
                          key={target}
                          size="small"
                          variant="outlined"
                          disabled={statusMutation.isPending}
                          onClick={() => statusMutation.mutate(target)}
                        >
                          {CASE_STATUS_LABELS[target]}
                        </Button>
                      ))}
                    {ALLOWED_TRANSITIONS[data.investigation.status].includes('CLOSED') && (
                      <Button
                        size="small"
                        variant="outlined"
                        color="error"
                        onClick={() => setCloseOpen(true)}
                      >
                        Clôturer…
                      </Button>
                    )}
                  </Stack>
                </Field>

                <Field label="Affectation">
                  {data.investigation.assigneeUsername ? (
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <Typography variant="body2">
                        {data.investigation.assigneeUsername}
                      </Typography>
                      <Button
                        size="small"
                        onClick={() => unassignMutation.mutate()}
                        disabled={unassignMutation.isPending}
                      >
                        Désassigner
                      </Button>
                    </Stack>
                  ) : (
                    <Stack direction="row" spacing={1}>
                      <TextField
                        size="small"
                        placeholder="nom d'utilisateur"
                        value={assignee}
                        onChange={(e) => setAssignee(e.target.value)}
                      />
                      <Button
                        size="small"
                        variant="outlined"
                        disabled={!assignee || assignMutation.isPending}
                        onClick={() => assignMutation.mutate()}
                      >
                        Assigner
                      </Button>
                    </Stack>
                  )}
                </Field>
              </>
            )}

            {data.investigation.description && (
              <Field label="Description">
                <Typography variant="body2">{data.investigation.description}</Typography>
              </Field>
            )}

            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Tâches ({data.tasks.filter((t) => t.status === 'DONE').length}/{data.tasks.length})
            </Typography>
            {canEdit && (
              <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
                <TextField
                  size="small"
                  fullWidth
                  placeholder="Nouvelle tâche d'analyse…"
                  value={taskTitle}
                  onChange={(e) => setTaskTitle(e.target.value)}
                />
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<AddIcon />}
                  disabled={!taskTitle || addTaskMutation.isPending}
                  onClick={() => addTaskMutation.mutate()}
                >
                  Ajouter
                </Button>
              </Stack>
            )}
            {data.tasks.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Aucune tâche.
              </Typography>
            )}
            {data.tasks.map((task) => (
              <Stack
                key={task.id}
                direction="row"
                spacing={1}
                sx={{ alignItems: 'center', mb: 0.5 }}
              >
                <Checkbox
                  size="small"
                  checked={task.status === 'DONE'}
                  disabled={!canEdit || toggleTaskMutation.isPending}
                  onChange={() => toggleTaskMutation.mutate(task)}
                  slotProps={{ input: { 'aria-label': `Tâche ${task.title}` } }}
                />
                <Typography
                  variant="body2"
                  sx={{
                    flexGrow: 1,
                    textDecoration: task.status === 'DONE' ? 'line-through' : 'none',
                  }}
                >
                  {task.title}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {task.status === 'DONE' && task.completedAt
                    ? `${TASK_STATUS_LABELS.DONE} le ${formatDate(task.completedAt)}`
                    : TASK_STATUS_LABELS[task.status]}
                </Typography>
              </Stack>
            ))}

            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Incidents liés ({data.linkedIncidents.length})
            </Typography>
            {data.linkedIncidents.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Aucun incident lié.
              </Typography>
            )}
            {data.linkedIncidents.map((incident) => (
              <Stack
                key={incident.id}
                direction="row"
                spacing={1}
                sx={{ alignItems: 'center', mb: 0.5 }}
              >
                <SeverityChip severity={incident.severity} />
                <Typography variant="body2" sx={{ flexGrow: 1 }} noWrap>
                  {incident.reference} — {incident.title}
                </Typography>
                {canEdit && (
                  <IconButton
                    size="small"
                    aria-label={`Délier ${incident.reference}`}
                    onClick={() => unlinkIncidentMutation.mutate(incident.id)}
                  >
                    <LinkOffIcon fontSize="small" />
                  </IconButton>
                )}
              </Stack>
            ))}

            <Typography variant="subtitle2" sx={{ mb: 1, mt: 2 }}>
              Alertes liées ({data.linkedAlerts.length})
            </Typography>
            {data.linkedAlerts.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Aucune alerte liée.
              </Typography>
            )}
            {data.linkedAlerts.map((alert) => (
              <Stack
                key={alert.id}
                direction="row"
                spacing={1}
                sx={{ alignItems: 'center', mb: 0.5 }}
              >
                <SeverityChip severity={alert.severity} />
                <Typography variant="body2" sx={{ flexGrow: 1 }} noWrap>
                  {alert.title}
                </Typography>
                {canEdit && (
                  <IconButton
                    size="small"
                    aria-label={`Délier ${alert.id}`}
                    onClick={() => unlinkAlertMutation.mutate(alert.id)}
                  >
                    <LinkOffIcon fontSize="small" />
                  </IconButton>
                )}
              </Stack>
            ))}

            {data.followUps.length > 0 && (
              <>
                <Divider sx={{ my: 2 }} />
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  Cas de suivi ({data.followUps.length})
                </Typography>
                {data.followUps.map((followUp) => (
                  <Stack
                    key={followUp.id}
                    direction="row"
                    spacing={1}
                    sx={{ alignItems: 'center', mb: 0.5 }}
                  >
                    <Typography variant="body2" color="text.secondary">
                      {followUp.reference}
                    </Typography>
                    <Typography variant="body2" sx={{ flexGrow: 1 }} noWrap>
                      {followUp.title}
                    </Typography>
                    <CaseStatusChip status={followUp.status} />
                  </Stack>
                ))}
              </>
            )}

            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Timeline
            </Typography>
            {canEdit && (
              <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
                <TextField
                  size="small"
                  fullWidth
                  placeholder="Ajouter une note…"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                />
                <Button
                  size="small"
                  variant="outlined"
                  disabled={!note || noteMutation.isPending}
                  onClick={() => noteMutation.mutate()}
                >
                  Noter
                </Button>
              </Stack>
            )}
            {data.timeline.map((entry, index) => (
              <Box key={index} sx={{ mb: 1.5 }}>
                <Typography variant="body2">{entry.message}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {entry.type} · {entry.author} · {formatDate(entry.occurredAt)}
                </Typography>
              </Box>
            ))}

            <CloseCaseDialog
              caseId={data.investigation.id}
              reference={data.investigation.reference}
              open={closeOpen}
              onClose={() => setCloseOpen(false)}
            />
            <CreateCaseDialog
              open={followUpOpen}
              onClose={() => setFollowUpOpen(false)}
              originCase={followUpOpen ? data.investigation : null}
            />
          </>
        )}
      </Box>
    </Drawer>
  );
}

export default CaseDetailDrawer;
