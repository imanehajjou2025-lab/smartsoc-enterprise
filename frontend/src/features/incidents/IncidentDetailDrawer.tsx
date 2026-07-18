import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import { SeverityChip } from '../alerts/chips';
import { openCaseFromIncident } from '../investigations/investigationsApi';
import { IncidentStatusChip, INCIDENT_STATUS_LABELS } from './incidentChips';
import {
  ALLOWED_TRANSITIONS,
  addIncidentNote,
  assignIncident,
  getIncident,
  unassignIncident,
  unlinkAlertFromIncident,
  updateIncidentStatus,
  type IncidentStatus,
} from './incidentsApi';

interface Props {
  incidentId: string | null;
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

function IncidentDetailDrawer({ incidentId, onClose }: Props) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canWrite = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';
  const [assignee, setAssignee] = useState('');
  const [note, setNote] = useState('');

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['incident', incidentId],
    queryFn: () => getIncident(incidentId!),
    enabled: Boolean(incidentId),
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
            <Stack direction="row" spacing={1} sx={{ mb: 1, alignItems: 'center' }}>
              <Typography variant="subtitle2" color="text.secondary">
                {data.incident.reference}
              </Typography>
              <SeverityChip severity={data.incident.severity} />
              <IncidentStatusChip status={data.incident.status} />
            </Stack>
            <Typography variant="h6" sx={{ mb: 2 }}>
              {data.incident.title}
            </Typography>

            {mutationError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {problemDetail(mutationError, 'Action impossible.')}
              </Alert>
            )}

            {canWrite && (
              <>
                <Button
                  size="small"
                  variant="contained"
                  startIcon={<TravelExploreIcon />}
                  disabled={openCaseMutation.isPending}
                  onClick={() => openCaseMutation.mutate()}
                  sx={{ mb: 2 }}
                >
                  {openCaseMutation.isPending ? 'Ouverture…' : 'Ouvrir un cas'}
                </Button>

                <Field label="Changer le statut">
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
                      <Typography variant="body2" color="text.secondary">
                        Statut terminal.
                      </Typography>
                    )}
                  </Stack>
                </Field>

                <Field label="Affectation">
                  {data.incident.assigneeUsername ? (
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <Typography variant="body2">{data.incident.assigneeUsername}</Typography>
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

            {data.incident.description && (
              <Field label="Description">
                <Typography variant="body2">{data.incident.description}</Typography>
              </Field>
            )}

            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
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
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Timeline
            </Typography>
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
            {data.timeline.map((entry, index) => (
              <Box key={index} sx={{ mb: 1.5 }}>
                <Typography variant="body2">{entry.message}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {entry.author} · {formatDate(entry.occurredAt)}
                </Typography>
              </Box>
            ))}
          </>
        )}
      </Box>
    </Drawer>
  );
}

export default IncidentDetailDrawer;
