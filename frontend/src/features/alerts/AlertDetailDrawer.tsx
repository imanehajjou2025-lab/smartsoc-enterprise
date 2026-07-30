import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import ArrowOutwardIcon from '@mui/icons-material/ArrowOutward';
import DnsIcon from '@mui/icons-material/Dns';
import GppMaybeIcon from '@mui/icons-material/GppMaybe';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import { severityColors } from '../../app/theme';
import { getAssetByHostname } from '../assets/assetsApi';
import { ExposureChip } from '../assets/assetChips';
import { setAssistantContext, summarizeAlertForAssistant } from '../assistant/assistantContext';
import { getAlertThreatIntel } from '../intelligence/intelligenceApi';
import { getAlertMitre, type ResolvedTechnique } from '../mitre/mitreApi';
import { escalateFromAlert } from '../incidents/incidentsApi';
import {
  ALLOWED_TRANSITIONS,
  updateAlertStatus,
  type Alert as SocAlert,
  type AlertStatus,
} from './alertsApi';
import { AiZoneChip, SeverityChip, StatusChip, STATUS_LABELS } from './chips';

interface Props {
  alert: SocAlert | null;
  onClose: () => void;
  onUpdated: (alert: SocAlert) => void;
}

const TRANSITION_BUTTON_COLORS: Record<string, 'primary' | 'success' | 'warning'> = {
  ACKNOWLEDGED: 'primary',
  IN_PROGRESS: 'primary',
  RESOLVED: 'success',
  FALSE_POSITIVE: 'warning',
};

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

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
}

/** Détail d'une alerte : contexte SOC, payload brut (évidence) et triage. */
function AlertDetailDrawer({ alert, onClose, onUpdated }: Props) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canTriage = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';

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
          <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
            <SeverityChip severity={alert.severity} />
            <StatusChip status={alert.status} />
          </Stack>
          <Typography variant="h6" sx={{ mb: 2 }}>
            {alert.title}
          </Typography>

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

          <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap' }} useFlexGap>
            {canTriage && (
              <Button
                size="small"
                variant="contained"
                startIcon={<ArrowOutwardIcon />}
                disabled={escalateMutation.isPending}
                onClick={() => escalateMutation.mutate(alert.id)}
              >
                {escalateMutation.isPending ? 'Escalade…' : 'Escalader en incident'}
              </Button>
            )}
            <Button
              size="small"
              variant="outlined"
              startIcon={<SmartToyOutlinedIcon />}
              onClick={() => {
                setAssistantContext({
                  alertId: alert.id,
                  summary: summarizeAlertForAssistant(alert),
                });
                onClose();
                navigate('/assistant');
              }}
            >
              Demander à l'assistant
            </Button>
          </Stack>

          {canTriage && transitions.length > 0 && (
            <>
              <Stack direction="row" spacing={1} useFlexGap sx={{ mb: 2, flexWrap: 'wrap' }}>
                {transitions.map((target) => (
                  <Button
                    key={target}
                    size="small"
                    variant="outlined"
                    color={TRANSITION_BUTTON_COLORS[target]}
                    disabled={mutation.isPending}
                    onClick={() => mutation.mutate({ id: alert.id, status: target })}
                  >
                    {STATUS_LABELS[target]}
                  </Button>
                ))}
              </Stack>
              <Divider sx={{ mb: 2 }} />
            </>
          )}

          {alert.description && (
            <Field label="Description">
              <Typography variant="body2">{alert.description}</Typography>
            </Field>
          )}
          <Field label="Source / Identifiant externe">
            <Typography variant="body2">
              {alert.source} · {alert.externalId}
            </Typography>
          </Field>
          <Field label="Détection / Réception">
            <Typography variant="body2">
              {formatDate(alert.detectedAt)} · reçue {formatDate(alert.receivedAt)}
            </Typography>
          </Field>
          {alert.hostname && (
            <Field label="Actif concerné">
              <Stack
                direction="row"
                spacing={1}
                useFlexGap
                sx={{ alignItems: 'center', flexWrap: 'wrap' }}
              >
                <Typography variant="body2">{alert.hostname}</Typography>
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
            </Field>
          )}
          {alert.ruleId && (
            <Field label="Règle de détection">
              <Typography variant="body2">{alert.ruleId}</Typography>
            </Field>
          )}
          {alert.mitreTechniques.length > 0 && (
            <Field label="Techniques MITRE ATT&CK">
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
            </Field>
          )}
          {threatIntel && threatIntel.observables.length > 0 && (
            <Field label="Renseignement CTI">
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
            </Field>
          )}

          <Field label="Score IA (classifieur TP/FP externe)">
            <Typography
              variant="body2"
              color={alert.aiScore == null ? 'text.secondary' : undefined}
            >
              {alert.aiScore == null
                ? 'Non évalué — service IA non connecté'
                : `${(alert.aiScore * 100).toFixed(1)} % · ${alert.aiVerdict}`}
            </Typography>
          </Field>

          {(alert.aiZone != null || alert.aiJustifications.length > 0) && (
            <Field label="Zone recommandée (enrichissement complémentaire)">
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
                {alert.aiZone != null ? (
                  <AiZoneChip zone={alert.aiZone} />
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    Non évaluée
                  </Typography>
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
            </Field>
          )}

          {rawPayloadPretty && (
            <Field label="Événement brut (évidence)">
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
            </Field>
          )}
        </Box>
      )}
    </Drawer>
  );
}

export default AlertDetailDrawer;
