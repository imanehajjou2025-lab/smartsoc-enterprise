import { useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import BookmarkBorderOutlinedIcon from '@mui/icons-material/BookmarkBorderOutlined';
import CheckCircleOutlineOutlinedIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditIcon from '@mui/icons-material/Edit';
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';
import ManageSearchOutlinedIcon from '@mui/icons-material/ManageSearchOutlined';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import QueryStatsOutlinedIcon from '@mui/icons-material/QueryStatsOutlined';
import RadarOutlinedIcon from '@mui/icons-material/RadarOutlined';
import SavedSearchOutlinedIcon from '@mui/icons-material/SavedSearchOutlined';
import SearchOutlinedIcon from '@mui/icons-material/SearchOutlined';
import SensorsOutlinedIcon from '@mui/icons-material/SensorsOutlined';
import SpeedOutlinedIcon from '@mui/icons-material/SpeedOutlined';
import TrackChangesOutlinedIcon from '@mui/icons-material/TrackChangesOutlined';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import LinearProgress from '@mui/material/LinearProgress';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { alpha, keyframes, useTheme } from '@mui/material/styles';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAppSelector } from '../../app/hooks';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import KpiTile from '../../shared/components/KpiTile';
import PageHeaderBanner from '../../shared/components/PageHeaderBanner';
import type { Alert as SocAlert } from '../alerts/alertsApi';
import AlertDetailDrawer from '../alerts/AlertDetailDrawer';
import { SeverityChip, StatusChip } from '../alerts/chips';
import { useAlertsRealtime } from '../alerts/useAlertsRealtime';
import {
  deleteHunt,
  executeAdHoc,
  executeHunt,
  flatAndGroup,
  listHuntableFields,
  listHunts,
  updateHunt,
  type HuntCondition,
  type HuntExecutionResult,
  type HuntField,
  type HuntOperator,
  type HuntQuery,
} from './huntingApi';
import SaveHuntDialog from './SaveHuntDialog';

const RESULTS_SIZE = 10;

const SEVERITY_VALUES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const STATUS_VALUES = ['NEW', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'FALSE_POSITIVE'];

const FIELD_LABELS: Record<HuntField, string> = {
  SEVERITY: 'Sévérité',
  STATUS: 'Statut',
  SOURCE: 'Source',
  HOSTNAME: 'Hôte',
  RULE_ID: 'Règle',
  DETECTED_AT: 'Détectée',
  MITRE_TECHNIQUE: 'Technique ATT&CK',
  RAW_PAYLOAD_TEXT: 'Texte de l’événement brut',
};

const OPERATOR_LABELS: Record<HuntOperator, string> = {
  EQUALS: '=',
  CONTAINS: 'contient',
  GREATER_THAN: 'après',
  LESS_THAN: 'avant',
};

function newCondition(): HuntCondition {
  return { kind: 'CONDITION', field: 'SEVERITY', operator: 'EQUALS', value: '' };
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

const radarSpin = keyframes`
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
`;

const blipPulse = keyframes`
  0%, 100% { transform: scale(0.55); opacity: 0.25; }
  50% { transform: scale(1.15); opacity: 1; }
`;

const ringPulse = keyframes`
  0% { transform: scale(0.85); opacity: 0.65; }
  100% { transform: scale(1.25); opacity: 0; }
`;

const BLIPS = [
  { top: '20%', left: '68%', color: severityColors.info, delay: '0s' },
  { top: '58%', left: '80%', color: severityColors.low, delay: '0.7s' },
  { top: '74%', left: '32%', color: severityColors.high, delay: '1.4s' },
  { top: '28%', left: '26%', color: severityColors.info, delay: '2.1s' },
];

/**
 * Illustration décorative — sonar/radar animé en CSS pur (aucune donnée,
 * aucune requête réseau) pour évoquer la recherche active côté SOC.
 * Volontairement discrète : elle habille la zone d'activité, jamais au
 * premier plan devant les vraies informations.
 */
function RadarPulse() {
  const accent = severityColors.info;
  return (
    <Box sx={{ position: 'relative', width: 176, height: 176, flexShrink: 0, mx: 'auto' }}>
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          borderRadius: '50%',
          boxShadow: `0 0 70px 14px ${alpha(accent, 0.14)}`,
        }}
      />
      {[1, 0.68, 0.36].map((scale, index) => (
        <Box
          key={index}
          sx={{
            position: 'absolute',
            inset: 0,
            margin: 'auto',
            width: `${scale * 100}%`,
            height: `${scale * 100}%`,
            borderRadius: '50%',
            border: '1px solid',
            borderColor: alpha(accent, 0.3),
          }}
        />
      ))}
      <Box
        sx={{
          position: 'absolute',
          top: 0,
          bottom: 0,
          left: '50%',
          width: '1px',
          bgcolor: alpha(accent, 0.18),
        }}
      />
      <Box
        sx={{
          position: 'absolute',
          left: 0,
          right: 0,
          top: '50%',
          height: '1px',
          bgcolor: alpha(accent, 0.18),
        }}
      />
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          borderRadius: '50%',
          overflow: 'hidden',
          background: `conic-gradient(from 0deg, transparent 0deg, ${alpha(accent, 0.55)} 28deg, transparent 78deg)`,
          animation: `${radarSpin} 3.4s linear infinite`,
        }}
      />
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          margin: 'auto',
          width: '38%',
          height: '38%',
          borderRadius: '50%',
          border: '2px solid',
          borderColor: alpha(accent, 0.55),
          animation: `${ringPulse} 2.6s ease-out infinite`,
        }}
      />
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          margin: 'auto',
          width: 14,
          height: 14,
          borderRadius: '50%',
          bgcolor: accent,
          boxShadow: `0 0 16px 4px ${alpha(accent, 0.75)}`,
        }}
      />
      {BLIPS.map((blip, index) => (
        <Box
          key={index}
          sx={{
            position: 'absolute',
            top: blip.top,
            left: blip.left,
            width: 8,
            height: 8,
            borderRadius: '50%',
            bgcolor: blip.color,
            boxShadow: `0 0 8px 2px ${alpha(blip.color, 0.75)}`,
            animation: `${blipPulse} 2.6s ease-in-out infinite`,
            animationDelay: blip.delay,
          }}
        />
      ))}
    </Box>
  );
}

function MiniStat({
  icon,
  label,
  value,
  color,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  color: string;
}) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.25,
        p: 1.5,
        borderRadius: 2,
        border: '1px solid',
        borderColor: alpha(color, 0.25),
        bgcolor: (t) => alpha(color, t.palette.mode === 'dark' ? 0.1 : 0.06),
      }}
    >
      <Box
        sx={{
          width: 34,
          height: 34,
          borderRadius: '50%',
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color,
          bgcolor: alpha(color, 0.16),
        }}
      >
        {icon}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ textTransform: 'uppercase', letterSpacing: 0.4, fontWeight: 700, display: 'block' }}
        >
          {label}
        </Typography>
        <Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.2 }} noWrap>
          {value}
        </Typography>
      </Box>
    </Box>
  );
}

const HOW_IT_WORKS = [
  {
    title: 'Configurez vos conditions',
    description: 'Choisissez un champ, un opérateur et une valeur pour cibler les alertes recherchées.',
  },
  {
    title: 'Exécutez la recherche',
    description: "Lancez la requête sur les alertes déjà ingérées par la plateforme — aucune donnée n'est modifiée.",
  },
  {
    title: 'Analysez et sauvegardez',
    description: "Ouvrez le détail d'une alerte correspondante, puis enregistrez la chasse pour la rejouer plus tard.",
  },
];

function HowItWorksStep({ index, title, description }: { index: number; title: string; description: string }) {
  const accent = severityColors.info;
  return (
    <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start' }}>
      <Box
        sx={{
          width: 26,
          height: 26,
          flexShrink: 0,
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontWeight: 800,
          fontSize: 13,
          color: accent,
          bgcolor: alpha(accent, 0.16),
          border: '1px solid',
          borderColor: alpha(accent, 0.4),
        }}
      >
        {index}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="body2" sx={{ fontWeight: 700 }}>
          {title}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {description}
        </Typography>
      </Box>
    </Stack>
  );
}

/** Chasse proactive : requêtes structurées sur les alertes déjà ingérées (ADR-011). */
function HuntingPage() {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const { connected } = useAlertsRealtime();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canWrite = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';

  const [conditions, setConditions] = useState<HuntCondition[]>([newCondition()]);
  const [loadedHunt, setLoadedHunt] = useState<HuntQuery | null>(null);
  const [savedSearch, setSavedSearch] = useState('');
  const [saveDialogOpen, setSaveDialogOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<HuntQuery | null>(null);
  const [resultsPage, setResultsPage] = useState(0);
  const [lastRunHuntId, setLastRunHuntId] = useState<string | null>(null);
  const [executionResult, setExecutionResult] = useState<HuntExecutionResult | null>(null);
  const [selectedAlert, setSelectedAlert] = useState<SocAlert | null>(null);

  const { data: fields } = useQuery({
    queryKey: ['hunt-fields'],
    queryFn: listHuntableFields,
    staleTime: Infinity,
  });

  const { data: savedHunts, isPending: savedPending } = useQuery({
    queryKey: ['hunts', savedSearch],
    queryFn: () => listHunts(savedSearch, 0, 50),
    placeholderData: keepPreviousData,
  });

  const runMutation = useMutation({
    mutationFn: ({ huntId, page }: { huntId: string | null; page: number }) =>
      huntId
        ? executeHunt(huntId, page, RESULTS_SIZE)
        : executeAdHoc(flatAndGroup(conditions), page, RESULTS_SIZE),
    onSuccess: (result, variables) => {
      setExecutionResult(result);
      setResultsPage(variables.page);
      setLastRunHuntId(variables.huntId);
      if (variables.huntId) {
        void queryClient.invalidateQueries({ queryKey: ['hunts'] });
      }
    },
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      updateHunt(loadedHunt!.id, { name: loadedHunt!.name, criteria: flatAndGroup(conditions) }),
    onSuccess: (updated) => {
      setLoadedHunt(updated);
      void queryClient.invalidateQueries({ queryKey: ['hunts'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteHunt(id),
    onSuccess: () => {
      setDeleteTarget(null);
      if (loadedHunt?.id === deleteTarget?.id) {
        setLoadedHunt(null);
      }
      void queryClient.invalidateQueries({ queryKey: ['hunts'] });
    },
  });

  const updateCondition = (index: number, patch: Partial<HuntCondition>) => {
    setConditions((prev) => prev.map((c, i) => (i === index ? { ...c, ...patch } : c)));
  };

  const changeField = (index: number, field: HuntField) => {
    const allowed = fields?.find((f) => f.field === field)?.allowedOperators ?? ['EQUALS'];
    updateCondition(index, { field, operator: allowed[0], value: '' });
  };

  const removeCondition = (index: number) => {
    setConditions((prev) => (prev.length > 1 ? prev.filter((_, i) => i !== index) : prev));
  };

  const loadHunt = (hunt: HuntQuery) => {
    setLoadedHunt(hunt);
    const flatConditions = hunt.criteria.children.filter(
      (n): n is HuntCondition => n.kind === 'CONDITION',
    );
    setConditions(flatConditions.length > 0 ? flatConditions : [newCondition()]);
    setExecutionResult(null);
  };

  const startNewHunt = () => {
    setLoadedHunt(null);
    setConditions([newCondition()]);
    setExecutionResult(null);
  };

  const run = (huntId: string | null) => runMutation.mutate({ huntId, page: 0 });

  const executedCount = savedHunts?.items.filter((h) => h.lastExecutedAt).length ?? 0;
  const statusColor = runMutation.isPending
    ? severityColors.medium
    : executionResult
      ? executionResult.summary.matchedCount > 0
        ? severityColors.low
        : severityColors.info
      : severityColors.info;
  const statusLabel = runMutation.isPending
    ? 'Recherche en cours…'
    : executionResult
      ? `${executionResult.summary.matchedCount} résultat${executionResult.summary.matchedCount > 1 ? 's' : ''} — recherche terminée`
      : 'Prête à exécuter — configurez vos conditions puis lancez la recherche.';

  return (
    <Box>
      <PageHeaderBanner
        icon={<RadarOutlinedIcon sx={{ color: theme.palette.primary.main, fontSize: 28 }} />}
        title="Threat Hunting"
        subtitle="Requêtes structurées sur les alertes déjà ingérées — aucune ingestion, uniquement de la recherche."
        badges={[
          {
            icon: <SensorsOutlinedIcon />,
            label: connected ? 'Flux temps réel actif' : 'Flux hors ligne',
            active: connected,
            activeColor: severityColors.low,
            pulseDot: true,
          },
        ]}
        action={{ label: 'Nouvelle chasse', icon: <AddIcon />, onClick: startNewHunt }}
      />

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' },
          gap: 2,
          mb: 3,
        }}
      >
        <KpiTile
          label="Chasses sauvegardées"
          value={String(savedHunts?.totalElements ?? 0)}
          hint="gabarits réutilisables"
          color={severityColors.info}
          icon={<SavedSearchOutlinedIcon />}
        />
        <KpiTile
          label="Déjà exécutées"
          value={String(executedCount)}
          hint="parmi les chasses chargées"
          color={severityColors.low}
          icon={<CheckCircleOutlineOutlinedIcon />}
        />
        <KpiTile
          label="Dernière exécution"
          value={executionResult ? String(executionResult.summary.matchedCount) : '—'}
          hint={executionResult ? 'correspondances trouvées' : 'aucune requête lancée'}
          color={severityColors.high}
          icon={<QueryStatsOutlinedIcon />}
        />
        <KpiTile
          label="Conditions actives"
          value={String(conditions.length)}
          hint="dans le constructeur"
          color={severityColors.medium}
          icon={<TrackChangesOutlinedIcon />}
        />
      </Box>

      <Stack direction="row" spacing={2.5} sx={{ alignItems: 'flex-start' }}>
        <Paper
          variant="outlined"
          sx={{ width: 300, flexShrink: 0, borderRadius: 3, overflow: 'hidden' }}
        >
          <Box
            sx={{
              px: 2,
              pt: 2,
              pb: 1.5,
              background: (t) =>
                `linear-gradient(160deg, ${alpha(severityColors.info, t.palette.mode === 'dark' ? 0.14 : 0.07)} 0%, ${t.palette.background.paper} 70%)`,
            }}
          >
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
              <BookmarkBorderOutlinedIcon fontSize="small" sx={{ color: severityColors.info }} />
              <Typography
                variant="caption"
                sx={{ fontWeight: 800, letterSpacing: 0.5, textTransform: 'uppercase' }}
              >
                Chasses sauvegardées
              </Typography>
              <Chip
                label={savedHunts?.totalElements ?? 0}
                size="small"
                sx={{
                  ml: 'auto',
                  height: 20,
                  fontWeight: 700,
                  color: severityColors.info,
                  bgcolor: alpha(severityColors.info, 0.14),
                }}
              />
            </Stack>
            <TextField
              placeholder="Rechercher une chasse"
              size="small"
              fullWidth
              value={savedSearch}
              onChange={(e) => setSavedSearch(e.target.value)}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchOutlinedIcon fontSize="small" color="disabled" />
                    </InputAdornment>
                  ),
                },
              }}
            />
          </Box>
          <Box sx={{ px: 1, pb: 1.5 }}>
            {savedPending && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
                <CircularProgress size={24} />
              </Box>
            )}
            <List dense disablePadding>
              {savedHunts?.items.map((hunt) => (
                <ListItemButton
                  key={hunt.id}
                  selected={loadedHunt?.id === hunt.id}
                  onClick={() => loadHunt(hunt)}
                  sx={{
                    borderRadius: 2,
                    mb: 0.5,
                    borderLeft: '3px solid',
                    borderLeftColor:
                      loadedHunt?.id === hunt.id ? severityColors.info : 'transparent',
                  }}
                >
                  <ListItemText
                    primary={hunt.name}
                    secondary={
                      hunt.lastExecutedAt
                        ? `Exécutée ${formatDate(hunt.lastExecutedAt)}`
                        : 'Jamais exécutée'
                    }
                    slotProps={{
                      primary: { noWrap: true },
                      secondary: { variant: 'caption' },
                    }}
                  />
                  <IconButton
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation();
                      run(hunt.id);
                    }}
                    title="Exécuter"
                  >
                    <PlayArrowIcon fontSize="small" />
                  </IconButton>
                  {canWrite && (
                    <IconButton
                      size="small"
                      onClick={(e) => {
                        e.stopPropagation();
                        setDeleteTarget(hunt);
                      }}
                      title="Supprimer"
                    >
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  )}
                </ListItemButton>
              ))}
              {savedHunts && savedHunts.items.length === 0 && (
                <Typography variant="body2" color="text.secondary" sx={{ px: 1, py: 1 }}>
                  Aucune chasse sauvegardée. Créez et enregistrez vos requêtes pour les rejouer
                  facilement.
                </Typography>
              )}
            </List>
          </Box>
        </Paper>

        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Paper variant="outlined" sx={{ p: 2.5, mb: 2.5, borderRadius: 3 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
              <ManageSearchOutlinedIcon sx={{ color: severityColors.info }} />
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                Constructeur de requête
              </Typography>
              {loadedHunt && (
                <Chip
                  icon={<EditIcon fontSize="small" />}
                  label={`Modification de « ${loadedHunt.name} »`}
                  size="small"
                  variant="outlined"
                  sx={{ ml: 1 }}
                />
              )}
            </Stack>
            <Stack spacing={1.5}>
              {conditions.map((condition, index) => {
                const allowedOperators =
                  fields?.find((f) => f.field === condition.field)?.allowedOperators ?? [];
                return (
                  <Stack
                    key={index}
                    direction="row"
                    spacing={1}
                    sx={{
                      alignItems: 'center',
                      p: 1,
                      borderRadius: 2,
                      border: '1px solid',
                      borderColor: 'divider',
                    }}
                  >
                    <TextField
                      select
                      size="small"
                      label="Champ"
                      value={condition.field}
                      onChange={(e) => changeField(index, e.target.value as HuntField)}
                      sx={{ minWidth: 180 }}
                    >
                      {Object.entries(FIELD_LABELS).map(([value, label]) => (
                        <MenuItem key={value} value={value}>
                          {label}
                        </MenuItem>
                      ))}
                    </TextField>
                    <TextField
                      select
                      size="small"
                      label="Opérateur"
                      value={condition.operator}
                      onChange={(e) =>
                        updateCondition(index, { operator: e.target.value as HuntOperator })
                      }
                      sx={{ minWidth: 130 }}
                    >
                      {allowedOperators.map((op) => (
                        <MenuItem key={op} value={op}>
                          {OPERATOR_LABELS[op]}
                        </MenuItem>
                      ))}
                    </TextField>
                    {(condition.field === 'SEVERITY' || condition.field === 'STATUS') && (
                      <TextField
                        select
                        size="small"
                        label="Valeur"
                        value={condition.value}
                        onChange={(e) => updateCondition(index, { value: e.target.value })}
                        sx={{ minWidth: 180 }}
                      >
                        {(condition.field === 'SEVERITY' ? SEVERITY_VALUES : STATUS_VALUES).map(
                          (value) => (
                            <MenuItem key={value} value={value}>
                              {value}
                            </MenuItem>
                          ),
                        )}
                      </TextField>
                    )}
                    {condition.field === 'DETECTED_AT' && (
                      <TextField
                        type="datetime-local"
                        size="small"
                        label="Valeur"
                        slotProps={{ inputLabel: { shrink: true } }}
                        onChange={(e) =>
                          updateCondition(index, {
                            value: e.target.value ? new Date(e.target.value).toISOString() : '',
                          })
                        }
                        sx={{ minWidth: 200 }}
                      />
                    )}
                    {!['SEVERITY', 'STATUS', 'DETECTED_AT'].includes(condition.field) && (
                      <TextField
                        size="small"
                        label="Valeur"
                        value={condition.value}
                        onChange={(e) => updateCondition(index, { value: e.target.value })}
                        placeholder={condition.field === 'MITRE_TECHNIQUE' ? 'T1059' : undefined}
                        sx={{ minWidth: 220, flexGrow: 1 }}
                      />
                    )}
                    <IconButton
                      size="small"
                      onClick={() => removeCondition(index)}
                      disabled={conditions.length === 1}
                    >
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                );
              })}
            </Stack>
            <Button
              size="small"
              startIcon={<AddIcon />}
              onClick={() => setConditions((prev) => [...prev, newCondition()])}
              sx={{ mt: 1 }}
            >
              Ajouter une condition
            </Button>

            <Divider sx={{ my: 2 }} />

            {runMutation.isError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {problemDetail(runMutation.error, 'Exécution impossible.')}
              </Alert>
            )}

            <Stack direction="row" spacing={1}>
              <Button
                variant="contained"
                disableElevation
                startIcon={<PlayArrowIcon />}
                disabled={runMutation.isPending || conditions.some((c) => !c.value)}
                onClick={() => run(null)}
                sx={{
                  bgcolor: '#2f81f7',
                  fontWeight: 700,
                  boxShadow: '0 0 16px rgba(47,129,247,0.4)',
                  '&:hover': { bgcolor: '#1f6feb', boxShadow: '0 0 20px rgba(47,129,247,0.55)' },
                }}
              >
                Exécuter
              </Button>
              {canWrite && loadedHunt && (
                <Button
                  variant="outlined"
                  disabled={updateMutation.isPending}
                  onClick={() => updateMutation.mutate()}
                >
                  Mettre à jour « {loadedHunt.name} »
                </Button>
              )}
              {canWrite && (
                <Button variant="outlined" onClick={() => setSaveDialogOpen(true)}>
                  Enregistrer sous…
                </Button>
              )}
            </Stack>
          </Paper>

          <Paper
            variant="outlined"
            sx={{
              p: 2.5,
              mb: 2.5,
              borderRadius: 3,
              position: 'relative',
              overflow: 'hidden',
              background: (t) =>
                `linear-gradient(160deg, ${alpha(severityColors.info, t.palette.mode === 'dark' ? 0.08 : 0.04)} 0%, ${t.palette.background.paper} 55%)`,
            }}
          >
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
              <InsightsOutlinedIcon sx={{ color: severityColors.info }} />
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                Activité de la chasse en cours
              </Typography>
              <Box sx={{ flexGrow: 1 }} />
              <Chip
                label={
                  runMutation.isPending
                    ? 'En cours'
                    : executionResult
                      ? 'Terminée'
                      : 'Prête à exécuter'
                }
                size="small"
                sx={{
                  fontWeight: 700,
                  color: statusColor,
                  bgcolor: alpha(statusColor, 0.14),
                }}
              />
            </Stack>

            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', md: '190px 1fr' },
                gap: 3,
                alignItems: 'center',
              }}
            >
              <RadarPulse />

              <Box sx={{ minWidth: 0 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
                  <Box
                    sx={{
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      bgcolor: statusColor,
                      boxShadow: `0 0 6px 2px ${alpha(statusColor, 0.7)}`,
                    }}
                  />
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                    {statusLabel}
                  </Typography>
                </Stack>
                {runMutation.isPending && (
                  <LinearProgress
                    sx={{
                      mb: 2,
                      height: 5,
                      borderRadius: 999,
                      bgcolor: alpha(severityColors.medium, 0.15),
                      '& .MuiLinearProgress-bar': { bgcolor: severityColors.medium },
                    }}
                  />
                )}
                <Box
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
                    gap: 1.25,
                    mt: runMutation.isPending ? 0 : 2,
                  }}
                >
                  <MiniStat
                    icon={<QueryStatsOutlinedIcon fontSize="small" />}
                    label="Résultats"
                    value={executionResult ? String(executionResult.summary.matchedCount) : '—'}
                    color={severityColors.high}
                  />
                  <MiniStat
                    icon={<SpeedOutlinedIcon fontSize="small" />}
                    label="Durée d'exécution"
                    value={executionResult ? `${executionResult.summary.tookMillis} ms` : '—'}
                    color={severityColors.medium}
                  />
                  <MiniStat
                    icon={<TrackChangesOutlinedIcon fontSize="small" />}
                    label="Conditions actives"
                    value={String(conditions.length)}
                    color={severityColors.info}
                  />
                  <MiniStat
                    icon={<ManageSearchOutlinedIcon fontSize="small" />}
                    label="Champs disponibles"
                    value={String(fields?.length ?? 0)}
                    color={severityColors.low}
                  />
                </Box>
              </Box>
            </Box>

            <Divider sx={{ my: 2.5 }} />

            <Typography
              variant="caption"
              sx={{ fontWeight: 800, letterSpacing: 0.5, textTransform: 'uppercase', mb: 1.5, display: 'block' }}
              color="text.secondary"
            >
              Comment ça fonctionne ?
            </Typography>
            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' },
                gap: 2.5,
              }}
            >
              {HOW_IT_WORKS.map((step, index) => (
                <HowItWorksStep key={step.title} index={index + 1} {...step} />
              ))}
            </Box>
          </Paper>

          {executionResult && (
            <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3 }}>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {executionResult.summary.matchedCount} correspondance
                {executionResult.summary.matchedCount > 1 ? 's' : ''} —{' '}
                {executionResult.summary.tookMillis} ms
                {executionResult.summary.truncated ? ' (résultat tronqué)' : ''}
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Sévérité</TableCell>
                      <TableCell>Titre</TableCell>
                      <TableCell>Source</TableCell>
                      <TableCell>Hôte</TableCell>
                      <TableCell>Statut</TableCell>
                      <TableCell>Détection</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {executionResult.matches.items.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={6}>
                          <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                            Aucune alerte ne correspond à ces critères.
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                    {executionResult.matches.items.map((alert) => (
                      <TableRow
                        key={alert.id}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => setSelectedAlert(alert)}
                      >
                        <TableCell>
                          <SeverityChip severity={alert.severity} />
                        </TableCell>
                        <TableCell
                          title={alert.title}
                          sx={{
                            maxWidth: 280,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {alert.title}
                        </TableCell>
                        <TableCell>{alert.source}</TableCell>
                        <TableCell>{alert.hostname ?? '—'}</TableCell>
                        <TableCell>
                          <StatusChip status={alert.status} />
                        </TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>
                          <Typography variant="caption" color="text.secondary">
                            {formatDate(alert.detectedAt)}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
                <TablePagination
                  component="div"
                  count={executionResult.matches.totalElements}
                  page={resultsPage}
                  onPageChange={(_, newPage) =>
                    runMutation.mutate({ huntId: lastRunHuntId, page: newPage })
                  }
                  rowsPerPage={RESULTS_SIZE}
                  rowsPerPageOptions={[RESULTS_SIZE]}
                  labelRowsPerPage=""
                />
              </TableContainer>
            </Paper>
          )}
        </Box>
      </Stack>

      <SaveHuntDialog
        open={saveDialogOpen}
        onClose={() => setSaveDialogOpen(false)}
        criteria={flatAndGroup(conditions)}
        onSaved={(hunt) => {
          setLoadedHunt(hunt);
          void queryClient.invalidateQueries({ queryKey: ['hunts'] });
        }}
      />

      <Dialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>Supprimer la chasse ?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            « {deleteTarget?.name} » sera supprimée définitivement — ce n'est pas une pièce
            d'évidence SOC, juste un gabarit de recherche.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Annuler</Button>
          <Button
            color="error"
            variant="contained"
            disabled={deleteMutation.isPending}
            onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
          >
            Supprimer
          </Button>
        </DialogActions>
      </Dialog>

      <AlertDetailDrawer
        alert={selectedAlert}
        onClose={() => setSelectedAlert(null)}
        onUpdated={setSelectedAlert}
      />
    </Box>
  );
}

export default HuntingPage;
