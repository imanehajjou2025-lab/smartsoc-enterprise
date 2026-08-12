import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import PersonOutlineIcon from '@mui/icons-material/PersonOutlineOutlined';
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutlined';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import { useTheme } from '@mui/material/styles';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import { severityColors } from '../../app/theme';
import { listUsers } from '../admin/usersApi';
import ActionCard from '../../shared/components/ActionCard';
import AssigneeAutocomplete from '../../shared/components/AssigneeAutocomplete';
import DetailDrawerHeader from '../../shared/components/DetailDrawerHeader';
import DetailField from '../../shared/components/DetailField';
import MutedText from '../../shared/components/MutedText';
import SectionLabel from '../../shared/components/SectionLabel';
import Timeline, { type TimelineRow } from '../../shared/components/Timeline';
import { SeverityChip } from '../alerts/chips';
import { setAssistantContext, summarizeIncidentForAssistant } from '../assistant/assistantContext';
import { openCaseFromIncident } from '../investigations/investigationsApi';
import { ExecutionStatusChip } from '../soar/soarChips';
import { listExecutionsForIncident } from '../soar/soarApi';
import StartPlaybookExecutionDialog from '../soar/StartPlaybookExecutionDialog';
import TriggerShuffleExecutionDialog from '../soar/TriggerShuffleExecutionDialog';
import { IncidentStatusChip, INCIDENT_STATUS_LABELS } from './incidentChips';
import {
  ALLOWED_TRANSITIONS,
  addIncidentNote,
  assignIncident,
  getIncident,
  unassignIncident,
  unlinkAlertFromIncident,
  updateIncidentStatus,
  type IncidentEventType,
  type IncidentStatus,
} from './incidentsApi';

const EVENT_TYPE_META: Record<IncidentEventType, { label: string; color: string }> = {
  CREATED: { label: 'Création', color: severityColors.info },
  STATUS_CHANGED: { label: 'Statut', color: severityColors.high },
  ASSIGNED: { label: 'Affectation', color: '#2f81f7' },
  UNASSIGNED: { label: 'Désaffectation', color: '#8b949e' },
  NOTE: { label: 'Note', color: '#8ecfff' },
  ALERT_LINKED: { label: 'Alerte liée', color: severityColors.critical },
  ALERT_UNLINKED: { label: 'Alerte déliée', color: '#8b949e' },
};

interface Props {
  incidentId: string | null;
  onClose: () => void;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

function IncidentDetailDrawer({ incidentId, onClose }: Props) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const theme = useTheme();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canWrite = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';
  const [assignee, setAssignee] = useState('');
  const [note, setNote] = useState('');
  const [startPlaybookOpen, setStartPlaybookOpen] = useState(false);
  const [triggerShuffleOpen, setTriggerShuffleOpen] = useState(false);

  const { data: executions } = useQuery({
    queryKey: ['playbook-executions', incidentId],
    queryFn: () => listExecutionsForIncident(incidentId!),
    enabled: Boolean(incidentId),
  });

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['incident', incidentId],
    queryFn: () => getIncident(incidentId!),
    enabled: Boolean(incidentId),
  });

  const { data: platformUsers } = useQuery({
    queryKey: ['platform-users'],
    queryFn: listUsers,
    enabled: canWrite,
    staleTime: 5 * 60_000,
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['incident', incidentId] });
    void queryClient.invalidateQueries({ queryKey: ['incidents'] });
  };

  const statusMutation = useMutation({
    mutationFn: (status: IncidentStatus) => updateIncidentStatus(incidentId!, status),
    onSuccess: invalidate,
  });
  const assignMutation = useMutation({
    mutationFn: () => assignIncident(incidentId!, assignee),
    onSuccess: () => {
      setAssignee('');
      invalidate();
    },
  });
  const unassignMutation = useMutation({
    mutationFn: () => unassignIncident(incidentId!),
    onSuccess: invalidate,
  });
  const noteMutation = useMutation({
    mutationFn: () => addIncidentNote(incidentId!, note),
    onSuccess: () => {
      setNote('');
      invalidate();
    },
  });
  const unlinkMutation = useMutation({
    mutationFn: (alertId: string) => unlinkAlertFromIncident(incidentId!, alertId),
    onSuccess: invalidate,
  });
  // Miroir de l'escalade alerte → incident : ouvre un cas d'enquête
  // depuis l'incident, le lie, et emmène l'analyste sur le module.
  const openCaseMutation = useMutation({
    mutationFn: () => openCaseFromIncident(incidentId!),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['investigations'] });
      onClose();
      navigate('/investigations');
    },
  });

  const mutationError =
    statusMutation.error ??
    assignMutation.error ??
    noteMutation.error ??
    unlinkMutation.error ??
    openCaseMutation.error;

  return (
    <Drawer anchor="right" open={Boolean(incidentId)} onClose={onClose}>
      <Box sx={{ width: 520, maxWidth: '92vw', p: 3 }}>
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
              color={severityColors[data.incident.severity.toLowerCase() as keyof typeof severityColors]}
              icon={<ReportProblemOutlinedIcon sx={{ fontSize: 26 }} />}
              title={data.incident.title}
              onClose={onClose}
            />
            <Stack direction="row" spacing={1} sx={{ mb: 2, alignItems: 'center', flexWrap: 'wrap' }} useFlexGap>
              <Chip label={data.incident.reference} size="small" variant="outlined" sx={{ fontWeight: 700 }} />
              <IncidentStatusChip status={data.incident.status} />
            </Stack>

            {mutationError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {problemDetail(mutationError, 'Action impossible.')}
              </Alert>
            )}

            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
                gap: 1.25,
                mb: 2,
              }}
            >
              {canWrite && (
                <>
                  <ActionCard
                    icon={<TravelExploreIcon />}
                    title={openCaseMutation.isPending ? 'Ouverture…' : 'Ouvrir un cas'}
                    description="Lancer une investigation approfondie"
                    color="#2f81f7"
                    filled
                    disabled={openCaseMutation.isPending}
                    onClick={() => openCaseMutation.mutate()}
                  />
                  <ActionCard
                    icon={<PlayCircleOutlineIcon />}
                    title="Exécuter un playbook"
                    description="Lancer une réponse automatisée"
                    color="#2f81f7"
                    onClick={() => setStartPlaybookOpen(true)}
                  />
                  <ActionCard
                    icon={<AccountTreeOutlinedIcon />}
                    title="Déclencher via Shuffle"
                    description="Lancer un workflow SOAR externe"
                    color={severityColors.high}
                    onClick={() => setTriggerShuffleOpen(true)}
                  />
                </>
              )}
              <ActionCard
                icon={<SmartToyOutlinedIcon />}
                title="Demander à l'assistant"
                description="Obtenir de l'aide pour cet incident"
                color="#2f81f7"
                onClick={() => {
                  setAssistantContext({
                    incidentId: data.incident.id,
                    summary: summarizeIncidentForAssistant(data.incident),
                  });
                  onClose();
                  navigate('/assistant');
                }}
              />
            </Box>

            {canWrite && (
              <>
                <DetailField label="Changer le statut" color={severityColors.high}>
                  <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                    {ALLOWED_TRANSITIONS[data.incident.status].map((target) => (
                      <Button
                        key={target}
                        size="small"
                        variant="outlined"
                        disabled={statusMutation.isPending}
                        onClick={() => statusMutation.mutate(target)}
                      >
                        {INCIDENT_STATUS_LABELS[target]}
                      </Button>
                    ))}
                    {ALLOWED_TRANSITIONS[data.incident.status].length === 0 && (
                      <MutedText>Statut terminal.</MutedText>
                    )}
                  </Stack>
                </DetailField>

                <DetailField label="Affectation" color={theme.palette.primary.main}>
                  {data.incident.assigneeUsername ? (
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <PersonOutlineIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {data.incident.assigneeUsername}
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
                      <AssigneeAutocomplete
                        users={platformUsers ?? []}
                        value={assignee}
                        onChange={setAssignee}
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

            {data.incident.description && (
              <DetailField label="Description">
                <Typography variant="body2">{data.incident.description}</Typography>
              </DetailField>
            )}

            <Divider sx={{ my: 2 }} />
            <SectionLabel color={severityColors.critical}>
              Alertes liées ({data.linkedAlerts.length})
            </SectionLabel>
            {data.linkedAlerts.length === 0 && (
              <MutedText>Aucune alerte liée.</MutedText>
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
                {canWrite && (
                  <IconButton
                    size="small"
                    aria-label={`Délier ${alert.id}`}
                    onClick={() => unlinkMutation.mutate(alert.id)}
                  >
                    <LinkOffIcon fontSize="small" />
                  </IconButton>
                )}
              </Stack>
            ))}

            <Divider sx={{ my: 2 }} />
            <SectionLabel color={theme.palette.primary.main}>
              Réponses ({executions?.items.length ?? 0})
            </SectionLabel>
            {executions && executions.items.length === 0 && (
              <MutedText>Aucun playbook exécuté.</MutedText>
            )}
            {executions?.items.map((execution) => (
              <Stack
                key={execution.id}
                direction="row"
                spacing={1}
                sx={{ alignItems: 'center', mb: 0.5, cursor: 'pointer' }}
                onClick={() => navigate(`/soar?execution=${execution.id}`)}
              >
                <ExecutionStatusChip status={execution.status} />
                <Typography variant="body2" sx={{ flexGrow: 1 }} noWrap>
                  {execution.playbookName}
                </Typography>
              </Stack>
            ))}

            <Divider sx={{ my: 2 }} />
            <SectionLabel color="#8ecfff">Timeline</SectionLabel>
            {canWrite && (
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
          </>
        )}
      </Box>

      {incidentId && (
        <StartPlaybookExecutionDialog
          open={startPlaybookOpen}
          onClose={() => setStartPlaybookOpen(false)}
          incidentId={incidentId}
          onStarted={(execution) => {
            setStartPlaybookOpen(false);
            onClose();
            navigate(`/soar?execution=${execution.id}`);
          }}
        />
      )}
      {incidentId && (
        <TriggerShuffleExecutionDialog
          open={triggerShuffleOpen}
          onClose={() => setTriggerShuffleOpen(false)}
          incidentId={incidentId}
          onTriggered={(execution) => {
            setTriggerShuffleOpen(false);
            onClose();
            navigate(`/soar?execution=${execution.id}`);
          }}
        />
      )}
    </Drawer>
  );
}

export default IncidentDetailDrawer;
