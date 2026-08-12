import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import InputAdornment from '@mui/material/InputAdornment';
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
import { alpha, useTheme } from '@mui/material/styles';
import BugReportIcon from '@mui/icons-material/BugReport';
import CalendarTodayOutlinedIcon from '@mui/icons-material/CalendarTodayOutlined';
import CloseIcon from '@mui/icons-material/Close';
import DnsOutlinedIcon from '@mui/icons-material/DnsOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined';
import FilterListIcon from '@mui/icons-material/FilterList';
import GppGoodIcon from '@mui/icons-material/GppGood';
import GppMaybeIcon from '@mui/icons-material/GppMaybe';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import PlaylistAddCheckOutlinedIcon from '@mui/icons-material/PlaylistAddCheckOutlined';
import ReportProblemIcon from '@mui/icons-material/ReportProblem';
import SensorsOutlinedIcon from '@mui/icons-material/SensorsOutlined';
import ShieldIcon from '@mui/icons-material/Shield';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import KpiTile from '../../shared/components/KpiTile';
import PageHeaderBanner from '../../shared/components/PageHeaderBanner';
import { getAlertStats } from '../dashboard/dashboardApi';
import AlertDetailDrawer from './AlertDetailDrawer';
import {
  listAlerts,
  type Alert as SocAlert,
  type AlertSeverity,
  type AlertStatus,
  type AnalystTier,
} from './alertsApi';
import { ANALYST_TIER_LABELS, AnalystTierChip, SeverityChip, StatusChip, STATUS_LABELS } from './chips';

const ANALYST_TIERS: AnalystTier[] = ['N1', 'N2', 'N3'];
import { useAlertsRealtime } from './useAlertsRealtime';

/** Score IA : couleur sémantique par tranche de confiance TP, même palette que les sévérités. */
function aiScoreColor(score: number | null): string {
  if (score == null) return severityColors.info;
  if (score >= 0.7) return severityColors.critical;
  if (score >= 0.4) return severityColors.high;
  return severityColors.low;
}

const SEVERITIES: AlertSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const STATUSES: AlertStatus[] = [
  'NEW',
  'ACKNOWLEDGED',
  'IN_PROGRESS',
  'RESOLVED',
  'FALSE_POSITIVE',
];

const SEVERITY_TILE_ICON: Record<AlertSeverity, React.ReactNode> = {
  CRITICAL: <GppMaybeIcon />,
  HIGH: <LocalFireDepartmentIcon />,
  MEDIUM: <ReportProblemIcon />,
  LOW: <ShieldIcon />,
  INFO: <InfoOutlinedIcon />,
};

const SEVERITY_TILE_LABEL: Record<AlertSeverity, string> = {
  CRITICAL: 'Critiques',
  HIGH: 'Élevées',
  MEDIUM: 'Moyennes',
  LOW: 'Faibles',
  INFO: 'Informatives',
};

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/** File de triage des alertes — le cœur opérationnel de la console. */
function AlertsPage() {
  const theme = useTheme();
  // Lien profond /alerts?status=NEW (notifications du header) : sert
  // uniquement de valeur initiale, l'analyste reste libre de changer le filtre.
  const [searchParams] = useSearchParams();
  const initialStatus = searchParams.get('status');
  const [status, setStatus] = useState<AlertStatus | ''>(
    STATUSES.includes(initialStatus as AlertStatus) ? (initialStatus as AlertStatus) : '',
  );
  const [severity, setSeverity] = useState<AlertSeverity | ''>('');
  const [source, setSource] = useState('');
  const [hostname, setHostname] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [assignedTier, setAssignedTier] = useState<AnalystTier | ''>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [selected, setSelected] = useState<SocAlert | null>(null);

  // `to` est exclu côté backend (borne [from, to)) : on vise donc le
  // lendemain minuit pour que la date sélectionnée soit incluse en entier.
  const from = dateFrom ? new Date(`${dateFrom}T00:00:00.000Z`).toISOString() : undefined;
  const to = dateTo
    ? new Date(new Date(`${dateTo}T00:00:00.000Z`).getTime() + 86_400_000).toISOString()
    : undefined;
  const hasActiveFilters = Boolean(
    status || severity || source || hostname || dateFrom || dateTo || assignedTier,
  );
  const resetFilters = () => {
    setStatus('');
    setSeverity('');
    setSource('');
    setHostname('');
    setDateFrom('');
    setDateTo('');
    setAssignedTier('');
    setPage(0);
  };

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['alerts', { status, severity, source, hostname, from, to, assignedTier, page, size }],
    queryFn: () =>
      listAlerts({
        status,
        severity,
        source: source || undefined,
        hostname: hostname || undefined,
        from,
        to,
        assignedTier: assignedTier || undefined,
        page,
        size,
      }),
    placeholderData: keepPreviousData,
  });
  const { data: stats } = useQuery({
    queryKey: ['alerts-stats-header'],
    queryFn: getAlertStats,
  });
  const { connected } = useAlertsRealtime();

  return (
    <Box>
      <PageHeaderBanner
        icon={<BugReportIcon sx={{ color: theme.palette.primary.main, fontSize: 28 }} />}
        title="Alertes"
        subtitle="Surveillez, analysez et répondez aux alertes détectées par la plateforme."
        badges={[
          {
            icon: <MonitorHeartIcon />,
            label: connected ? 'En temps réel' : 'Hors ligne',
            active: connected,
            activeColor: severityColors.low,
            pulseDot: true,
          },
          {
            icon: isError ? <ErrorOutlineIcon /> : <GppGoodIcon />,
            label: isError ? 'Flux indisponible' : 'Flux opérationnel',
            active: !isError,
            activeColor: theme.palette.primary.main,
            inactiveColor: severityColors.critical,
          },
        ]}
      />

      {stats && (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(3, 1fr)', lg: 'repeat(6, 1fr)' },
            gap: 2,
            mb: 3,
          }}
        >
          <KpiTile
            label="Total"
            value={String(stats.total)}
            hint="tous statuts confondus"
            color={severityColors.info}
            icon={<BugReportIcon />}
          />
          {SEVERITIES.map((sev) => (
            <KpiTile
              key={sev}
              label={SEVERITY_TILE_LABEL[sev]}
              value={String(stats.bySeverity[sev] ?? 0)}
              hint={
                stats.total > 0
                  ? `${Math.round(((stats.bySeverity[sev] ?? 0) / stats.total) * 100)}% du total`
                  : 'du total'
              }
              color={severityColors[sev.toLowerCase() as keyof typeof severityColors]}
              icon={SEVERITY_TILE_ICON[sev]}
              onClick={() => {
                setSeverity(sev);
                setPage(0);
              }}
            />
          ))}
        </Box>
      )}

      <Paper
        elevation={0}
        sx={{
          p: 2.5,
          mb: 3,
          borderRadius: 4,
          border: '1px solid',
          borderColor: alpha(theme.palette.primary.main, 0.2),
          background: `linear-gradient(160deg, ${alpha(theme.palette.primary.main, theme.palette.mode === 'dark' ? 0.16 : 0.08)} 0%, ${theme.palette.background.paper} 55%)`,
        }}
      >
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 2.25 }}>
          <Box
            sx={{
              width: 36,
              height: 36,
              borderRadius: '50%',
              flexShrink: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: theme.palette.primary.main,
              bgcolor: alpha(theme.palette.primary.main, 0.16),
              boxShadow: `0 0 14px 2px ${alpha(theme.palette.primary.main, 0.4)}`,
            }}
          >
            <FilterListIcon fontSize="small" />
          </Box>
          <Box sx={{ minWidth: 0, flexGrow: 1 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
              Filtres
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ opacity: 0.75 }}>
              {data
                ? `${data.totalElements} alerte${data.totalElements > 1 ? 's' : ''} correspondante${data.totalElements > 1 ? 's' : ''}`
                : 'Affinez la file de triage'}
            </Typography>
          </Box>
          {hasActiveFilters && (
            <Button
              size="small"
              onClick={resetFilters}
              startIcon={<CloseIcon fontSize="small" />}
              sx={{ fontWeight: 700, borderRadius: 2, flexShrink: 0 }}
            >
              Réinitialiser
            </Button>
          )}
        </Stack>

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(3, 1fr)', lg: 'repeat(6, 1fr)' },
            gap: 1.5,
            '& .MuiOutlinedInput-root': { borderRadius: 2.5, bgcolor: 'background.paper' },
          }}
        >
          <TextField
            select
            label="Statut"
            size="small"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as AlertStatus | '');
              setPage(0);
            }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <PlaylistAddCheckOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
          >
            <MenuItem value="">Tous</MenuItem>
            {STATUSES.map((value) => (
              <MenuItem key={value} value={value}>
                {STATUS_LABELS[value]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Sévérité"
            size="small"
            value={severity}
            onChange={(e) => {
              setSeverity(e.target.value as AlertSeverity | '');
              setPage(0);
            }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <ReportProblemIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
          >
            <MenuItem value="">Toutes</MenuItem>
            {SEVERITIES.map((value) => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Source"
            placeholder="wazuh, suricata…"
            size="small"
            value={source}
            onChange={(e) => {
              setSource(e.target.value);
              setPage(0);
            }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SensorsOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
          />
          <TextField
            label="Actif"
            placeholder="win10-client…"
            size="small"
            value={hostname}
            onChange={(e) => {
              setHostname(e.target.value);
              setPage(0);
            }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <DnsOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
          />
          <TextField
            select
            label="Niveau affecté"
            size="small"
            value={assignedTier}
            onChange={(e) => {
              setAssignedTier(e.target.value as AnalystTier | '');
              setPage(0);
            }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <LayersOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
          >
            <MenuItem value="">Tous</MenuItem>
            {ANALYST_TIERS.map((t) => (
              <MenuItem key={t} value={t}>
                {ANALYST_TIER_LABELS[t]}
              </MenuItem>
            ))}
          </TextField>
          <Stack direction="row" spacing={1}>
            <TextField
              label="Du"
              type="date"
              size="small"
              value={dateFrom}
              onChange={(e) => {
                setDateFrom(e.target.value);
                setPage(0);
              }}
              slotProps={{
                inputLabel: { shrink: true },
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <CalendarTodayOutlinedIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                    </InputAdornment>
                  ),
                },
              }}
              sx={{ flex: 1 }}
            />
            <TextField
              label="Au"
              type="date"
              size="small"
              value={dateTo}
              onChange={(e) => {
                setDateTo(e.target.value);
                setPage(0);
              }}
              slotProps={{ inputLabel: { shrink: true } }}
              sx={{ flex: 1 }}
            />
          </Stack>
        </Box>

        {hasActiveFilters && (
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mt: 2 }}>
            {status && (
              <Chip
                size="small"
                label={`Statut : ${STATUS_LABELS[status]}`}
                onDelete={() => {
                  setStatus('');
                  setPage(0);
                }}
              />
            )}
            {severity && (
              <Chip
                size="small"
                label={`Sévérité : ${severity}`}
                onDelete={() => {
                  setSeverity('');
                  setPage(0);
                }}
              />
            )}
            {source && (
              <Chip
                size="small"
                label={`Source : ${source}`}
                onDelete={() => {
                  setSource('');
                  setPage(0);
                }}
              />
            )}
            {hostname && (
              <Chip
                size="small"
                label={`Actif : ${hostname}`}
                onDelete={() => {
                  setHostname('');
                  setPage(0);
                }}
              />
            )}
            {assignedTier && (
              <Chip
                size="small"
                label={`Niveau : ${assignedTier}`}
                onDelete={() => {
                  setAssignedTier('');
                  setPage(0);
                }}
              />
            )}
            {dateFrom && (
              <Chip
                size="small"
                label={`Du : ${dateFrom}`}
                onDelete={() => {
                  setDateFrom('');
                  setPage(0);
                }}
              />
            )}
            {dateTo && (
              <Chip
                size="small"
                label={`Au : ${dateTo}`}
                onDelete={() => {
                  setDateTo('');
                  setPage(0);
                }}
              />
            )}
          </Stack>
        )}
      </Paper>

      {isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}
      {isError && (
        <Alert severity="error">{problemDetail(error, 'Impossible de charger les alertes.')}</Alert>
      )}

      {data && (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 3 }}>
          <Table size="small" aria-label="File des alertes">
            <TableHead>
              <TableRow sx={{ '& th': { bgcolor: 'action.hover' } }}>
                <TableCell sx={{ fontWeight: 700 }}>Détection</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Sévérité</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Titre</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Source</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Actif</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Statut</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Affecté</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>
                  Score IA
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8}>
                    <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                      Aucune alerte — en attente d'ingestion depuis les outils SOC (ou lancez
                      scripts/simulate-alerts).
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {data.items.map((alert) => {
                const scoreColor = aiScoreColor(alert.aiScore);
                return (
                  <TableRow
                    key={alert.id}
                    hover
                    sx={{
                      cursor: 'pointer',
                      borderLeft: '3px solid',
                      borderLeftColor: alpha(
                        severityColors[alert.severity.toLowerCase() as keyof typeof severityColors],
                        0.6,
                      ),
                    }}
                    onClick={() => setSelected(alert)}
                  >
                    <TableCell sx={{ whiteSpace: 'nowrap', color: 'text.secondary' }}>
                      {formatDate(alert.detectedAt)}
                    </TableCell>
                    <TableCell>
                      <SeverityChip severity={alert.severity} />
                    </TableCell>
                    <TableCell
                      sx={{
                        maxWidth: 420,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        fontWeight: 500,
                      }}
                    >
                      {alert.title}
                    </TableCell>
                    <TableCell>
                      <Chip
                        icon={<SensorsOutlinedIcon sx={{ fontSize: 15 }} />}
                        label={alert.source}
                        size="small"
                        variant="outlined"
                        sx={{ textTransform: 'capitalize', fontWeight: 600 }}
                      />
                    </TableCell>
                    <TableCell>
                      {alert.hostname ? (
                        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                          <DnsOutlinedIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                          <Typography variant="body2">{alert.hostname}</Typography>
                        </Stack>
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          —
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <StatusChip status={alert.status} />
                    </TableCell>
                    <TableCell>
                      {alert.assignedTier ? (
                        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                          <AnalystTierChip tier={alert.assignedTier} />
                          {alert.assignedToUsername && (
                            <Typography variant="caption" color="text.secondary" noWrap>
                              {alert.assignedToUsername}
                            </Typography>
                          )}
                        </Stack>
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          —
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="right">
                      {alert.aiScore == null ? (
                        <Typography variant="body2" color="text.secondary">
                          —
                        </Typography>
                      ) : (
                        <Chip
                          label={`${(alert.aiScore * 100).toFixed(0)} %`}
                          size="small"
                          sx={{
                            bgcolor: alpha(scoreColor, 0.14),
                            color: scoreColor,
                            border: '1px solid',
                            borderColor: alpha(scoreColor, 0.5),
                            fontWeight: 700,
                            minWidth: 56,
                          }}
                        />
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
          <TablePagination
            component="div"
            count={data.totalElements}
            page={page}
            onPageChange={(_, newPage) => setPage(newPage)}
            rowsPerPage={size}
            onRowsPerPageChange={(e) => {
              setSize(parseInt(e.target.value, 10));
              setPage(0);
            }}
            rowsPerPageOptions={[10, 25, 50, 100]}
            labelRowsPerPage="Lignes par page"
          />
        </TableContainer>
      )}

      <AlertDetailDrawer
        alert={selected}
        onClose={() => setSelected(null)}
        onUpdated={setSelected}
      />
    </Box>
  );
}

export default AlertsPage;
