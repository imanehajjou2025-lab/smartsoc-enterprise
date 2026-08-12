import { useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import LockIcon from '@mui/icons-material/Lock';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
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
import { useTheme } from '@mui/material/styles';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAppSelector } from '../../app/hooks';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import DetailDrawerHeader from '../../shared/components/DetailDrawerHeader';
import DetailField from '../../shared/components/DetailField';
import MutedText from '../../shared/components/MutedText';
import SectionLabel from '../../shared/components/SectionLabel';
import Timeline, { type TimelineRow } from '../../shared/components/Timeline';
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
  type CaseEventType,
  type CaseStatus,
  type CaseTask,
} from './investigationsApi';

const EVENT_TYPE_META: Record<CaseEventType, { label: string; color: string }> = {
  CREATED: { label: 'Création', color: severityColors.info },
  STATUS_CHANGED: { label: 'Statut', color: severityColors.high },
  ASSIGNED: { label: 'Affectation', color: '#2f81f7' },
  UNASSIGNED: { label: 'Désaffectation', color: '#8b949e' },
  INCIDENT_LINKED: { label: 'Incident lié', color: severityColors.high },
  INCIDENT_UNLINKED: { label: 'Incident délié', color: '#8b949e' },
  ALERT_LINKED: { label: 'Alerte liée', color: severityColors.critical },
  ALERT_UNLINKED: { label: 'Alerte déliée', color: '#8b949e' },
  TASK_ADDED: { label: 'Tâche', color: severityColors.medium },
  TASK_UPDATED: { label: 'Tâche', color: severityColors.medium },
  TASK_COMPLETED: { label: 'Tâche terminée', color: severityColors.low },
  NOTE_ADDED: { label: 'Note', color: '#8ecfff' },
  CLOSED: { label: 'Clôture', color: severityColors.low },
  FOLLOW_UP_OPENED: { label: 'Suivi ouvert', color: '#2f81f7' },
};

interface Props {
  caseId: string | null;
  onClose: () => void;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/**
 * Tiroir de détail d'un cas d'investigation. Un cas CLOSED est immuable :
 * badge dédié, tous les contrôles d'écriture masqués — seule reste
 * l'ouverture d'un cas de suivi (l'unique voie de reprise du domaine).
 */
function CaseDetailDrawer({ caseId, onClose }: Props) {
  const theme = useTheme();
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
            <DetailDrawerHeader
              color={severityColors[data.investigation.priority.toLowerCase() as keyof typeof severityColors]}
              icon={<TravelExploreIcon sx={{ fontSize: 26 }} />}
              title={data.investigation.title}
              onClose={onClose}
            />
            <Stack direction="row" spacing={1} sx={{ mb: 2, alignItems: 'center', flexWrap: 'wrap' }} useFlexGap>
              <Chip
                label={data.investigation.reference}
                size="small"
                variant="outlined"
                sx={{ fontWeight: 700 }}
              />
              <CaseStatusChip status={data.investigation.status} />
              {isClosed && (
                <Chip icon={<LockIcon />} label="Cas clôturé — immuable" size="small" color="default" />
              )}
            </Stack>

            {mutationError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {problemDetail(mutationError, 'Action impossible.')}
              </Alert>
            )}

            {isClosed && (
              <DetailField label="Conclusion" color={severityColors.low}>
                <Typography variant="body2">{data.investigation.conclusion}</Typography>
                <MutedText>Clôturé le {formatDate(data.investigation.closedAt!)}</MutedText>
              </DetailField>
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
                <DetailField label="Changer le statut" color={severityColors.high}>
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
                </DetailField>

                <DetailField label="Affectation" color={theme.palette.primary.main}>
                  {data.investigation.assigneeUsername ? (
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
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
                </DetailField>
              </>
            )}

            {data.investigation.description && (
              <DetailField label="Description">
                <Typography variant="body2">{data.investigation.description}</Typography>
              </DetailField>
            )}

            <Divider sx={{ my: 2 }} />
            <SectionLabel color={severityColors.medium}>
              Tâches ({data.tasks.filter((t) => t.status === 'DONE').length}/{data.tasks.length})
            </SectionLabel>
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
            {data.tasks.length === 0 && <MutedText>Aucune tâche.</MutedText>}
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
                <Typography variant="caption" color="text.secondary" sx={{ opacity: 0.65 }}>
                  {task.status === 'DONE' && task.completedAt
                    ? `${TASK_STATUS_LABELS.DONE} le ${formatDate(task.completedAt)}`
                    : TASK_STATUS_LABELS[task.status]}
                </Typography>
              </Stack>
            ))}

            <Divider sx={{ my: 2 }} />
            <SectionLabel color={severityColors.critical}>
              Incidents liés ({data.linkedIncidents.length})
            </SectionLabel>
            {data.linkedIncidents.length === 0 && <MutedText>Aucun incident lié.</MutedText>}
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

            <Box sx={{ mt: 2 }}>
              <SectionLabel color="#8ecfff">Alertes liées ({data.linkedAlerts.length})</SectionLabel>
            </Box>
            {data.linkedAlerts.length === 0 && <MutedText>Aucune alerte liée.</MutedText>}
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
                <SectionLabel color={severityColors.medium}>
                  Cas de suivi ({data.followUps.length})
                </SectionLabel>
                {data.followUps.map((followUp) => (
                  <Stack
                    key={followUp.id}
                    direction="row"
                    spacing={1}
                    sx={{ alignItems: 'center', mb: 0.5 }}
                  >
                    <MutedText>{followUp.reference}</MutedText>
                    <Typography variant="body2" sx={{ flexGrow: 1 }} noWrap>
                      {followUp.title}
                    </Typography>
                    <CaseStatusChip status={followUp.status} />
                  </Stack>
                ))}
              </>
            )}

            <Divider sx={{ my: 2 }} />
            <SectionLabel color={theme.palette.primary.main}>Timeline</SectionLabel>
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
            <Timeline
              rows={data.timeline.map(
                (entry, index): TimelineRow => ({
                  key: `${entry.type}-${index}`,
                  label: EVENT_TYPE_META[entry.type].label,
                  color: EVENT_TYPE_META[entry.type].color,
                  message: entry.message,
                  date: `${entry.author} · ${formatDate(entry.occurredAt)}`,
                }),
              )}
            />

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
