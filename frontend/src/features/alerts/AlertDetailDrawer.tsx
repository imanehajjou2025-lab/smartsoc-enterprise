import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import ArrowOutwardIcon from '@mui/icons-material/ArrowOutward';
import AssignmentTurnedInOutlinedIcon from '@mui/icons-material/AssignmentTurnedInOutlined';
import CheckCircleOutlineOutlinedIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import DnsIcon from '@mui/icons-material/Dns';
import GppMaybeIcon from '@mui/icons-material/GppMaybe';
import PersonOutlineOutlinedIcon from '@mui/icons-material/PersonOutlineOutlined';
import PlayCircleOutlineOutlinedIcon from '@mui/icons-material/PlayCircleOutlineOutlined';
import PlaylistAddCheckOutlinedIcon from '@mui/icons-material/PlaylistAddCheckOutlined';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import ThumbDownOutlinedIcon from '@mui/icons-material/ThumbDownOutlined';
import { useTheme } from '@mui/material/styles';
import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import { severityColors } from '../../app/theme';
import ActionCard from '../../shared/components/ActionCard';
import AssigneeAutocomplete from '../../shared/components/AssigneeAutocomplete';
import DetailDrawerHeader from '../../shared/components/DetailDrawerHeader';
import DetailField from '../../shared/components/DetailField';
import MutedText from '../../shared/components/MutedText';
import { listUsers } from '../admin/usersApi';
import { getAssetByHostname } from '../assets/assetsApi';
import { ExposureChip } from '../assets/assetChips';
import { setAssistantContext, summarizeAlertForAssistant } from '../assistant/assistantContext';
import { getAlertThreatIntel } from '../intelligence/intelligenceApi';
import { getAlertMitre, type ResolvedTechnique } from '../mitre/mitreApi';
import { escalateFromAlert } from '../incidents/incidentsApi';
import {
  ALLOWED_TRANSITIONS,
  assignAlert,
  unassignAlert,
  updateAlertStatus,
  type Alert as SocAlert,
  type AlertStatus,
  type AnalystTier,
} from './alertsApi';
import { AiZoneChip, ANALYST_TIER_LABELS, AnalystTierChip, StatusChip, STATUS_LABELS } from './chips';

const ANALYST_TIERS: AnalystTier[] = ['N1', 'N2', 'N3'];

interface Props {
  alert: SocAlert | null;
  onClose: () => void;
  onUpdated: (alert: SocAlert) => void;
}

const TRANSITION_META: Record<
  AlertStatus,
  { icon: React.ReactNode; description: string; color: string }
> = {
  NEW: { icon: <PlaylistAddCheckOutlinedIcon />, description: '', color: severityColors.info },
  ACKNOWLEDGED: {
    icon: <AssignmentTurnedInOutlinedIcon />,
    description: 'Se positionner sur cette alerte',
    color: '#2f81f7',
  },
  IN_PROGRESS: {
    icon: <PlayCircleOutlineOutlinedIcon />,
    description: "Poursuivre l'investigation",
    color: '#2f81f7',
  },
  RESOLVED: {
    icon: <CheckCircleOutlineOutlinedIcon />,
    description: 'Marquer comme résolue',
    color: severityColors.low,
  },
  FALSE_POSITIVE: {
    icon: <ThumbDownOutlinedIcon />,
    description: 'Marquer comme faux positif',
    color: severityColors.high,
  },
};

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
}

/** Score IA : même palette sémantique que le tableau des alertes. */
function aiScoreColor(score: number | null): string {
  if (score == null) return severityColors.info;
  if (score >= 0.7) return severityColors.critical;
  if (score >= 0.4) return severityColors.high;
  return severityColors.low;
}

/** Détail d'une alerte : contexte SOC, payload brut (évidence) et triage. */
function AlertDetailDrawer({ alert, onClose, onUpdated }: Props) {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canTriage = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';
  const [tier, setTier] = useState<AnalystTier | ''>('');
  const [assignee, setAssignee] = useState('');

  // Roster réel pour le sélecteur d'affectation — lecture seule (le
  // triage n'a pas besoin de créer/modifier des comptes), demandée
  // uniquement pour les rôles qui peuvent effectivement affecter une
  // alerte : GET /users est ouvert à leur lecture, pas à leur écriture.
  const { data: platformUsers } = useQuery({
    queryKey: ['platform-users'],
    queryFn: listUsers,
    enabled: canTriage,
    staleTime: 5 * 60_000,
  });

  const mutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: AlertStatus }) =>
      updateAlertStatus(id, status),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ['alerts'] });
      onUpdated(updated);
    },
  });

  const escalateMutation = useMutation({
    mutationFn: (alertId: string) => escalateFromAlert(alertId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['incidents'] });
      onClose();
      navigate('/incidents');
    },
  });

  const assignMutation = useMutation({
    mutationFn: ({ id, tier: t, username }: { id: string; tier: AnalystTier; username: string }) =>
      assignAlert(id, t, username),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ['alerts'] });
      setTier('');
      setAssignee('');
      onUpdated(updated);
    },
  });

  const unassignMutation = useMutation({
    mutationFn: (id: string) => unassignAlert(id),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ['alerts'] });
      onUpdated(updated);
    },
  });

  // Enrichissement progressif : la puce d'actif n'apparaît QUE si le
  // hostname est inventorié. Un 404 est une information métier (« aucun
  // actif ») : pas de retry, pas de message, pas d'état de chargement —
  // le tiroir s'ouvre aussi vite qu'avant.
  const { data: linkedAsset } = useQuery({
    queryKey: ['asset-by-hostname', alert?.hostname],
    queryFn: () => getAssetByHostname(alert!.hostname!),
    enabled: Boolean(alert?.hostname),
    retry: (failureCount, error) =>
      !(axios.isAxiosError(error) && error.response?.status === 404) && failureCount < 2,
    staleTime: 60_000,
  });

  // Enrichissement CTI, calculé à la lecture côté serveur. Toujours 200
  // (deux listes vides si l'alerte ne cite aucun observable) : pas de
  // gestion de 404, la section n'apparaît que s'il y a quelque chose à
  // montrer.
  const { data: threatIntel } = useQuery({
    queryKey: ['alert-threat-intel', alert?.id],
    queryFn: () => getAlertThreatIntel(alert!.id),
    enabled: Boolean(alert?.id),
    staleTime: 60_000,
  });

  // Enrichissement MITRE, calculé à la lecture. Les techniques connues du
  // catalogue sont résolues (nom, tactiques) et deviennent cliquables vers
  // la matrice ; les inconnues restent affichées telles quelles.
  const { data: mitreTechniques } = useQuery({
    queryKey: ['alert-mitre', alert?.id],
    queryFn: () => getAlertMitre(alert!.id),
    enabled: Boolean(alert?.id),
    staleTime: 60_000,
  });

  const transitions = alert ? ALLOWED_TRANSITIONS[alert.status] : [];

  const rawPayloadPretty = (() => {
    if (!alert?.rawPayload) return null;
    try {
      return JSON.stringify(JSON.parse(alert.rawPayload), null, 2);
    } catch {
      return alert.rawPayload;
    }
  })();

  return (
    <Drawer anchor="right" open={Boolean(alert)} onClose={onClose}>
      {alert && (
        <Box sx={{ width: 480, maxWidth: '90vw', p: 3 }}>
          <DetailDrawerHeader
            color={severityColors[alert.severity.toLowerCase() as keyof typeof severityColors]}
            icon={<GppMaybeIcon sx={{ fontSize: 26 }} />}
            title={alert.title}
            onClose={onClose}
          />
          <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap' }} useFlexGap>
            <StatusChip status={alert.status} />
            {alert.assignedTier && <AnalystTierChip tier={alert.assignedTier} />}
          </Stack>

          {mutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {problemDetail(mutation.error, 'Transition impossible.')}
            </Alert>
          )}
          {escalateMutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {problemDetail(escalateMutation.error, 'Escalade impossible.')}
            </Alert>
          )}
          {(assignMutation.isError || unassignMutation.isError) && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {problemDetail(assignMutation.error ?? unassignMutation.error, 'Affectation impossible.')}
            </Alert>
          )}

          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
              gap: 1.25,
              mb: canTriage && transitions.length > 0 ? 1.25 : 2,
            }}
          >
            {canTriage && (
              <ActionCard
                icon={<ArrowOutwardIcon />}
                title={escalateMutation.isPending ? 'Escalade…' : 'Escalader en incident'}
                description="Créer un incident à partir de cette alerte"
                color="#2f81f7"
                filled
                disabled={escalateMutation.isPending}
                onClick={() => escalateMutation.mutate(alert.id)}
              />
            )}
            <ActionCard
              icon={<SmartToyOutlinedIcon />}
              title="Demander à l'assistant"
              description="Obtenir de l'aide pour cette alerte"
              color="#2f81f7"
              onClick={() => {
                setAssistantContext({
                  alertId: alert.id,
                  summary: summarizeAlertForAssistant(alert),
                });
                onClose();
                navigate('/assistant');
              }}
            />
            {canTriage &&
              transitions.map((target) => (
                <ActionCard
                  key={target}
                  icon={TRANSITION_META[target].icon}
                  title={STATUS_LABELS[target]}
                  description={TRANSITION_META[target].description}
                  color={TRANSITION_META[target].color}
                  disabled={mutation.isPending}
                  onClick={() => mutation.mutate({ id: alert.id, status: target })}
                />
              ))}
          </Box>
          {canTriage && transitions.length > 0 && <Divider sx={{ mb: 2 }} />}

          <DetailField label="Affectation de triage" color={severityColors.high}>
            {alert.assignedTier ? (
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }} useFlexGap>
                <AnalystTierChip tier={alert.assignedTier} />
                <MutedText>{ANALYST_TIER_LABELS[alert.assignedTier]}</MutedText>
                {alert.assignedToUsername && (
                  <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                    <PersonOutlineOutlinedIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {alert.assignedToUsername}
                    </Typography>
                  </Stack>
                )}
                {canTriage && (
                  <Button
                    size="small"
                    disabled={unassignMutation.isPending}
                    onClick={() => unassignMutation.mutate(alert.id)}
                  >
                    Retirer l'affectation
                  </Button>
                )}
              </Stack>
            ) : canTriage ? (
              <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                <TextField
                  select
                  size="small"
                  label="Niveau"
                  value={tier}
                  onChange={(e) => setTier(e.target.value as AnalystTier | '')}
                  sx={{ minWidth: 170 }}
                >
                  {ANALYST_TIERS.map((t) => (
                    <MenuItem key={t} value={t}>
                      {ANALYST_TIER_LABELS[t]}
                    </MenuItem>
                  ))}
                </TextField>
                <AssigneeAutocomplete
                  users={platformUsers ?? []}
                  value={assignee}
                  onChange={setAssignee}
                  placeholder="Analyste (optionnel)"
                />
                <Button
                  size="small"
                  variant="outlined"
                  disabled={!tier || assignMutation.isPending}
                  onClick={() => tier && assignMutation.mutate({ id: alert.id, tier, username: assignee })}
                >
                  Affecter
                </Button>
              </Stack>
            ) : (
              <MutedText>Non affectée</MutedText>
            )}
          </DetailField>

          {alert.description && (
            <DetailField label="Description">
              <Typography variant="body2">{alert.description}</Typography>
            </DetailField>
          )}
          <DetailField label="Source / Identifiant externe">
            <MutedText>
              {alert.source} · {alert.externalId}
            </MutedText>
          </DetailField>
          <DetailField label="Détection / Réception">
            <MutedText>
              {formatDate(alert.detectedAt)} · reçue {formatDate(alert.receivedAt)}
            </MutedText>
          </DetailField>
          {alert.hostname && (
            <DetailField label="Actif concerné" color={severityColors.info}>
              <Stack
                direction="row"
                spacing={1}
                useFlexGap
                sx={{ alignItems: 'center', flexWrap: 'wrap' }}
              >
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {alert.hostname}
                </Typography>
                {linkedAsset && (
                  <>
                    <Chip
                      icon={<DnsIcon />}
                      label={linkedAsset.displayName}
                      size="small"
                      clickable
                      onClick={() => {
                        onClose();
                        navigate(`/assets?selected=${linkedAsset.id}`);
                      }}
                      sx={{
                        color:
                          severityColors[
                            linkedAsset.criticality.toLowerCase() as
                              'critical' | 'high' | 'medium' | 'low'
                          ],
                        fontWeight: 600,
                      }}
                      variant="outlined"
                    />
                    {linkedAsset.exposure === 'INTERNET_FACING' && (
                      <ExposureChip exposure={linkedAsset.exposure} />
                    )}
                  </>
                )}
              </Stack>
            </DetailField>
          )}
          {alert.ruleId && (
            <DetailField label="Règle de détection">
              <MutedText>{alert.ruleId}</MutedText>
            </DetailField>
          )}
          {alert.mitreTechniques.length > 0 && (
            <DetailField label="Techniques MITRE ATT&CK" color={theme.palette.primary.main}>
              <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                {(
                  mitreTechniques ??
                  alert.mitreTechniques.map((raw): ResolvedTechnique => ({
                    rawId: raw,
                    known: false,
                    technique: null,
                  }))
                ).map((resolved) =>
                  resolved.known && resolved.technique ? (
                    <Chip
                      key={resolved.rawId}
                      label={`${resolved.technique.attackId} · ${resolved.technique.name}`}
                      size="small"
                      color="primary"
                      variant="outlined"
                      clickable
                      onClick={() =>
                        navigate(
                          `/mitre?selected=${encodeURIComponent(resolved.technique!.attackId)}`,
                        )
                      }
                      title={resolved.technique.tactics.join(', ')}
                    />
                  ) : (
                    <Chip
                      key={resolved.rawId}
                      label={resolved.rawId}
                      size="small"
                      variant="outlined"
                      component="a"
                      clickable
                      href={`https://attack.mitre.org/techniques/${resolved.rawId.replace('.', '/')}/`}
                      target="_blank"
                      rel="noreferrer"
                      title="Non cataloguée localement"
                    />
                  ),
                )}
              </Stack>
            </DetailField>
          )}
          {threatIntel && threatIntel.observables.length > 0 && (
            <DetailField label="Renseignement CTI" color={severityColors.critical}>
              {threatIntel.matches.length > 0 && (
                <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', mb: 0.75 }}>
                  <GppMaybeIcon fontSize="small" sx={{ color: severityColors.critical }} />
                  <Typography
                    variant="body2"
                    sx={{ color: severityColors.critical, fontWeight: 600 }}
                  >
                    {threatIntel.matches.length} observable(s) correspond(ent) à un indicateur connu
                  </Typography>
                </Stack>
              )}
              <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
                {threatIntel.observables.map((obs) => {
                  // La valeur des deux côtés est déjà normalisée par le
                  // serveur (même règle) : l'égalité stricte du couple
                  // (type, valeur) suffit à savoir si l'observable est un
                  // IOC connu.
                  const match = threatIntel.matches.find(
                    (ioc) => ioc.type === obs.type && ioc.value === obs.value,
                  );
                  return match ? (
                    <Chip
                      key={`${obs.type}:${obs.value}`}
                      label={obs.value}
                      size="small"
                      color="error"
                      clickable
                      onClick={() => {
                        onClose();
                        navigate(`/intelligence?selected=${match.id}`);
                      }}
                      sx={{ fontFamily: 'monospace' }}
                    />
                  ) : (
                    <Chip
                      key={`${obs.type}:${obs.value}`}
                      label={obs.value}
                      size="small"
                      variant="outlined"
                      sx={{ fontFamily: 'monospace' }}
                    />
                  );
                })}
              </Stack>
            </DetailField>
          )}

          <DetailField label="Score IA (classifieur TP/FP externe)" color={aiScoreColor(alert.aiScore)}>
            {alert.aiScore == null ? (
              <MutedText>Non évalué — service IA non connecté</MutedText>
            ) : (
              <Typography
                variant="body2"
                sx={{ fontWeight: 700, color: aiScoreColor(alert.aiScore) }}
              >
                {(alert.aiScore * 100).toFixed(1)} % · {alert.aiVerdict}
              </Typography>
            )}
          </DetailField>

          {(alert.aiZone != null || alert.aiJustifications.length > 0) && (
            <DetailField label="Zone recommandée (enrichissement complémentaire)" color={severityColors.medium}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
                {alert.aiZone != null ? (
                  <AiZoneChip zone={alert.aiZone} />
                ) : (
                  <MutedText>Non évaluée</MutedText>
                )}
                {alert.aiHardOverride && (
                  <Chip label="Dérogation forcée" size="small" color="error" variant="outlined" />
                )}
              </Stack>
              {alert.aiJustifications.length > 0 && (
                <Box
                  component="ul"
                  sx={{
                    m: 0,
                    pl: 2.5,
                    py: 1,
                    pr: 1.5,
                    bgcolor: 'background.default',
                    border: '1px solid',
                    borderColor: 'divider',
                    borderRadius: 1,
                    maxHeight: 200,
                    overflow: 'auto',
                  }}
                >
                  {alert.aiJustifications
                    .filter((line) => !/^=+$/.test(line.trim()))
                    .map((line, index) => (
                      <Typography
                        key={index}
                        component="li"
                        variant="caption"
                        sx={{ fontFamily: 'monospace', display: 'list-item' }}
                      >
                        {line}
                      </Typography>
                    ))}
                </Box>
              )}
            </DetailField>
          )}

          {rawPayloadPretty && (
            <DetailField label="Événement brut (évidence)">
              <Box
                component="pre"
                sx={{
                  m: 0,
                  p: 1.5,
                  bgcolor: 'background.default',
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: 1,
                  fontSize: 12,
                  overflow: 'auto',
                  maxHeight: 320,
                }}
              >
                {rawPayloadPretty}
              </Box>
            </DetailField>
          )}
        </Box>
      )}
    </Drawer>
  );
}

export default AlertDetailDrawer;
