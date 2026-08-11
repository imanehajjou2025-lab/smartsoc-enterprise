import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { alpha, keyframes, useTheme } from '@mui/material/styles';
import BugReportIcon from '@mui/icons-material/BugReport';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import DashboardIcon from '@mui/icons-material/Dashboard';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import DnsIcon from '@mui/icons-material/Dns';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined';
import GppGoodIcon from '@mui/icons-material/GppGood';
import GppMaybeIcon from '@mui/icons-material/GppMaybe';
import HubIcon from '@mui/icons-material/Hub';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import PublicIcon from '@mui/icons-material/Public';
import ShieldIcon from '@mui/icons-material/Shield';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';
import { useAppSelector } from '../../app/hooks';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import EChart from '../../shared/components/EChart';
import { softChipSx } from '../../shared/components/chipStyles';
import { SeverityChip } from '../alerts/chips';
import { useAlertsRealtime } from '../alerts/useAlertsRealtime';
import { ConfidenceBar, IocTypeChip } from '../intelligence/iocChips';
import { IncidentStatusChip, INCIDENT_STATUS_LABELS } from '../incidents/incidentChips';
import type { IncidentStatus } from '../incidents/incidentsApi';
import { AGENT_CONNECTION_STATUS_LABELS, ASSET_TYPE_LABELS } from '../assets/assetChips';
import type { AgentConnectionStatus, Asset, AssetCriticality, AssetType } from '../assets/assetsApi';
import type { AlertStats } from './dashboardApi';
import { useDashboardData } from './useDashboardData';

const pulse = keyframes`
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.35; transform: scale(1.5); }
`;

const INCIDENT_STATUS_ORDER: IncidentStatus[] = [
  'OPEN',
  'INVESTIGATING',
  'CONTAINED',
  'RESOLVED',
  'CLOSED',
];
const CRITICALITY_ORDER: AssetCriticality[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Temps écoulé, arrondi à l'unité la plus lisible — jamais recalculé côté serveur, purement d'affichage. */
function timeAgo(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 1) return "à l'instant";
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h`;
  const days = Math.floor(hours / 24);
  return `${days} j`;
}

const ACTIVITY_KIND_COLOR: Record<'alert' | 'incident' | 'report', string> = {
  alert: severityColors.critical,
  incident: severityColors.high,
  report: severityColors.info,
};

function KpiTile({
  label,
  value,
  hint,
  color,
  icon,
  onClick,
}: {
  label: string;
  value: string;
  hint: string;
  color: string;
  icon: React.ReactNode;
  onClick?: () => void;
}) {
  return (
    <Paper
      variant="outlined"
      onClick={onClick}
      sx={{
        p: 2,
        minHeight: 108,
        display: 'flex',
        gap: 1.75,
        alignItems: 'center',
        flex: 1,
        borderRadius: 3,
        cursor: onClick ? 'pointer' : 'default',
        borderColor: alpha(color, 0.25),
        background: (t) =>
          `linear-gradient(135deg, ${alpha(color, t.palette.mode === 'dark' ? 0.18 : 0.12)} 0%, ${t.palette.background.paper} 70%)`,
        transition: 'transform 150ms ease, border-color 150ms ease',
        '&:hover': onClick ? { borderColor: color, transform: 'translateY(-2px)' } : undefined,
      }}
    >
      <Box
        sx={{
          width: 46,
          height: 46,
          borderRadius: '50%',
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color,
          backgroundColor: alpha(color, 0.16),
          boxShadow: `0 0 16px 3px ${alpha(color, 0.45)}`,
        }}
      >
        {icon}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ textTransform: 'uppercase', letterSpacing: 0.4, fontWeight: 700 }}
        >
          {label}
        </Typography>
        <Typography variant="h4" sx={{ fontWeight: 800, lineHeight: 1.1, color }}>
          {value}
        </Typography>
        <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
          {hint}
        </Typography>
      </Box>
    </Paper>
  );
}

function Panel({
  title,
  action,
  children,
}: {
  title: string;
  action?: { label: string; onClick: () => void };
  children: React.ReactNode;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
      <Stack
        direction="row"
        sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}
      >
        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
          {title}
        </Typography>
        {action && (
          <Button size="small" onClick={action.onClick}>
            {action.label}
          </Button>
        )}
      </Stack>
      {children}
    </Paper>
  );
}

/**
 * Variante accentuée de {@link Panel} : icône + dégradé teinté par
 * couleur (même patron que {@link KpiTile}, donc déjà theme-aware — le
 * dégradé se recalcule seul en clair/sombre via `t.palette.mode`, aucune
 * image bitmap à gérer séparément par thème). Réservée aux panneaux
 * secondaires denses (lignes 4 et 5) pour réduire le vide visuel des
 * cartes les plus courtes tout en gardant une identité par module.
 */
function AccentPanel({
  title,
  icon,
  color,
  action,
  watermark,
  children,
}: {
  title: string;
  icon: React.ReactNode;
  color: string;
  action?: { label: string; onClick: () => void };
  /** Grande icône décorative en fond de carte — jamais une donnée, un pur
   * repère visuel par module (opacité/teinte theme-aware, pas de bitmap). */
  watermark?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2,
        position: 'relative',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        minWidth: 0,
        borderRadius: 3,
        borderColor: alpha(color, 0.22),
        background: (t) =>
          `linear-gradient(160deg, ${alpha(color, t.palette.mode === 'dark' ? 0.14 : 0.07)} 0%, ${t.palette.background.paper} 60%)`,
      }}
    >
      {watermark && (
        <Box
          aria-hidden
          sx={{
            position: 'absolute',
            right: -28,
            bottom: -28,
            width: 148,
            height: 148,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color,
            opacity: (t) => (t.palette.mode === 'dark' ? 0.14 : 0.08),
            transform: 'rotate(-12deg)',
            pointerEvents: 'none',
            '& svg': { fontSize: 148 },
          }}
        >
          {watermark}
        </Box>
      )}
      <Stack
        direction="row"
        sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1.5, position: 'relative' }}
      >
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 0 }}>
          <Box
            sx={{
              width: 30,
              height: 30,
              borderRadius: '50%',
              flexShrink: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color,
              bgcolor: alpha(color, 0.16),
              boxShadow: `0 0 10px 2px ${alpha(color, 0.35)}`,
            }}
          >
            {icon}
          </Box>
          <Typography variant="subtitle2" sx={{ fontWeight: 700 }} noWrap>
            {title}
          </Typography>
        </Stack>
        {action && (
          <Button size="small" onClick={action.onClick} sx={{ flexShrink: 0 }}>
            {action.label}
          </Button>
        )}
      </Stack>
      <Box sx={{ position: 'relative' }}>{children}</Box>
    </Paper>
  );
}

/**
 * Sous-titre de section à l'intérieur d'une {@link AccentPanel} — barre
 * colorée + majuscules teintées par la couleur d'accent de la carte,
 * pour se distinguer sans ambiguïté du contenu (texte gris + gras seul
 * se confondait trop avec les libellés de données).
 */
function SectionLabel({ children, color }: { children: React.ReactNode; color: string }) {
  return (
    <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', mb: 0.75 }}>
      <Box sx={{ width: 3, height: 12, borderRadius: 999, bgcolor: color, flexShrink: 0 }} />
      <Typography
        variant="caption"
        sx={{ fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.6, color }}
      >
        {children}
      </Typography>
    </Stack>
  );
}

/**
 * Anneau de couverture MITRE (observées vs catalogue). Le pourcentage et
 * le total ne sont PAS le titre ECharts (son centrage interne dépend de
 * la largeur du texte et dérive facilement) — ils sont affichés par une
 * superposition CSS/flexbox strictement centrée sur le conteneur, voir
 * le rendu dans `DashboardPage`.
 */
function mitreCoverageOption(observed: number, total: number): EChartsOption {
  return {
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['64%', '88%'],
        label: { show: false },
        silent: true,
        itemStyle: { borderColor: 'transparent', borderWidth: 3, borderRadius: 12 },
        data: [
          {
            name: 'Observées',
            value: observed,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
                { offset: 0, color: '#39c5cf' },
                { offset: 1, color: severityColors.low },
              ]),
              shadowColor: 'rgba(63,185,80,0.5)',
              shadowBlur: 14,
            },
          },
          {
            name: 'Non observées',
            value: Math.max(0, total - observed),
            itemStyle: { color: 'rgba(139,148,158,0.22)' },
          },
        ],
      },
    ],
  };
}

function timelineOption(stats: AlertStats): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 24, bottom: 24 },
    xAxis: {
      type: 'category',
      data: stats.timeline.map((d) =>
        new Date(d.date).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' }),
      ),
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.25 },
        itemStyle: { color: '#2f81f7' },
        data: stats.timeline.map((d) => d.count),
      },
    ],
  };
}

function sourcesBarOption(stats: AlertStats): EChartsOption {
  const entries = Object.entries(stats.bySource)
    .sort(([, a], [, b]) => b - a)
    .slice(0, 8)
    .reverse();
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 80, right: 24, top: 16, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: entries.map(([source]) => source) },
    series: [
      {
        type: 'bar',
        barWidth: 18,
        itemStyle: { color: '#39c5cf', borderRadius: [0, 4, 4, 0] },
        data: entries.map(([, count]) => count),
      },
    ],
  };
}

function countBy<T, K extends string>(items: T[], key: (item: T) => K): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const item of items) {
    const k = key(item);
    counts[k] = (counts[k] ?? 0) + 1;
  }
  return counts;
}

function isExposedCritical(asset: Asset): boolean {
  return asset.criticality === 'CRITICAL' && asset.exposure === 'INTERNET_FACING';
}

interface ActivityRow {
  kind: 'alert' | 'incident' | 'report';
  date: string;
  label: string;
  detail: string;
  onClick: () => void;
}

/**
 * Page d'accueil de la plateforme : chaque KPI et chaque panneau lit
 * directement son module d'origine (alerts/incidents/assets/soar/
 * hunting/mitre/intelligence). Le module Rapports n'alimente RIEN ici —
 * il n'apparaît qu'en activité récente secondaire, jamais un KPI
 * principal : le Dashboard reste utilisable même sans aucun rapport
 * généré.
 */
function DashboardPage() {
  const navigate = useNavigate();
  const { data, isPending, isError, error } = useDashboardData();
  const { connected } = useAlertsRealtime();
  const theme = useTheme();
  const user = useAppSelector((state) => state.auth.user);

  const timeline = useMemo(() => (data ? timelineOption(data.alertStats) : null), [data]);
  const sources = useMemo(() => (data ? sourcesBarOption(data.alertStats) : null), [data]);

  const derived = useMemo(() => {
    if (!data) return null;
    const { alertStats, incidents, assets, techniques, coverage, hunts } = data;

    const incidentsByStatus = countBy(incidents, (i) => i.status);
    const incidentsOpen = incidents.filter((i) => i.status !== 'CLOSED').length;

    const assetsByCriticality = countBy(assets, (a) => a.criticality);
    const assetsByType = countBy(assets, (a) => a.type);
    const criticalExposed = assets.filter(isExposedCritical);
    const internetFacingCount = assets.filter((a) => a.exposure === 'INTERNET_FACING').length;
    const internalCount = assets.filter((a) => a.exposure !== 'INTERNET_FACING').length;
    const exposedPct = assets.length > 0 ? Math.round((internetFacingCount / assets.length) * 100) : 0;

    const exposureByCriticality = CRITICALITY_ORDER.map((criticality) => ({
      criticality,
      internet: assets.filter(
        (a) => a.criticality === criticality && a.exposure === 'INTERNET_FACING',
      ).length,
      internal: assets.filter(
        (a) => a.criticality === criticality && a.exposure !== 'INTERNET_FACING',
      ).length,
    })).filter((row) => row.internet + row.internal > 0);
    const exposureMatrixMax = Math.max(
      1,
      ...exposureByCriticality.flatMap((row) => [row.internet, row.internal]),
    );

    const agentStatusCounts = countBy(
      assets.filter((a) => a.agentConnectionStatus !== null),
      (a) => a.agentConnectionStatus as string,
    );

    const openIncidents = incidents.filter((i) => i.status !== 'CLOSED').slice(0, 4);

    const sourcesTotal = Object.keys(alertStats.bySource).length;

    const fpRate =
      alertStats.total > 0
        ? Math.round(((alertStats.byStatus.FALSE_POSITIVE ?? 0) / alertStats.total) * 100)
        : 0;

    const catalogById = new Map(techniques.map((t) => [t.attackId, t]));
    const observed = coverage.filter((c) => catalogById.has(c.attackId));
    const coveragePct =
      techniques.length > 0 ? Math.round((observed.length / techniques.length) * 100) : 0;
    const topTechniques = [...observed]
      .sort((a, b) => b.alertCount - a.alertCount)
      .slice(0, 5)
      .map((c) => ({ ...c, name: catalogById.get(c.attackId)?.name ?? c.attackId }));

    const huntsExecutedRecently = hunts.filter((h) => {
      if (!h.lastExecutedAt) return false;
      return Date.now() - new Date(h.lastExecutedAt).getTime() <= SEVEN_DAYS_MS;
    }).length;

    const activity: ActivityRow[] = [
      ...data.recentAlerts.map((a) => ({
        kind: 'alert' as const,
        date: a.detectedAt,
        label: a.title,
        detail: `Alerte ${a.severity}`,
        onClick: () => navigate('/alerts'),
      })),
      ...incidents.slice(0, 8).map((i) => ({
        kind: 'incident' as const,
        date: i.openedAt,
        label: i.title,
        detail: `Incident ${i.reference}`,
        onClick: () => navigate(`/incidents?selected=${i.id}`),
      })),
      ...data.recentReports.map((r) => ({
        kind: 'report' as const,
        date: r.generatedAt,
        label: r.title,
        detail: 'Rapport généré',
        onClick: () => navigate(`/reports?selected=${r.id}`),
      })),
    ]
      .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
      .slice(0, 10);

    return {
      incidentsByStatus,
      incidentsOpen,
      openIncidents,
      assetsByCriticality,
      assetsByType,
      criticalExposed,
      internetFacingCount,
      internalCount,
      exposedPct,
      exposureByCriticality,
      exposureMatrixMax,
      agentStatusCounts,
      fpRate,
      coveragePct,
      observedCount: observed.length,
      topTechniques,
      huntsExecutedRecently,
      sourcesTotal,
      activity,
    };
  }, [data, navigate]);

  const mitreDonut = useMemo(() => {
    if (!data || !derived) return null;
    return mitreCoverageOption(derived.observedCount, data.techniques.length);
  }, [data, derived]);

  const displayName = user?.fullName ?? user?.username ?? '—';

  return (
    <Box>
      <Paper
        elevation={0}
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 2.5,
          px: 2.5,
          py: 2,
          mb: 3,
          borderRadius: 4,
          flexWrap: 'wrap',
          border: '1px solid',
          borderColor: alpha(theme.palette.primary.main, 0.18),
          background: `linear-gradient(135deg, ${alpha(theme.palette.primary.main, theme.palette.mode === 'dark' ? 0.22 : 0.1)} 0%, ${theme.palette.background.paper} 80%)`,
        }}
      >
        <Box
          sx={{
            width: 56,
            height: 56,
            borderRadius: 2,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: alpha(theme.palette.primary.main, 0.14),
            border: '1px solid',
            borderColor: alpha(theme.palette.primary.main, 0.35),
          }}
        >
          <DashboardIcon sx={{ color: theme.palette.primary.main, fontSize: 28 }} />
        </Box>

        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h5" component="h2" sx={{ fontWeight: 800 }}>
            Dashboard
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Bienvenue,{' '}
            <Box component="span" sx={{ color: theme.palette.primary.main, fontWeight: 700 }}>
              {displayName}
            </Box>
          </Typography>
        </Box>

        <Box sx={{ width: '1px', alignSelf: 'stretch', bgcolor: 'divider' }} />

        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.75,
            px: 1.5,
            py: 0.6,
            borderRadius: 999,
            border: '1px solid',
            borderColor: alpha(connected ? severityColors.low : theme.palette.text.secondary, 0.4),
            bgcolor: alpha(connected ? severityColors.low : theme.palette.text.secondary, 0.12),
          }}
        >
          <Box
            sx={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              bgcolor: connected ? severityColors.low : theme.palette.text.secondary,
              boxShadow: connected ? `0 0 6px 2px ${alpha(severityColors.low, 0.7)}` : 'none',
              animation: connected ? `${pulse} 1.6s ease-in-out infinite` : 'none',
            }}
          />
          <MonitorHeartIcon
            sx={{
              fontSize: 18,
              color: connected ? severityColors.low : theme.palette.text.secondary,
            }}
          />
          <Typography
            variant="body2"
            sx={{
              fontWeight: 700,
              color: connected ? severityColors.low : theme.palette.text.secondary,
            }}
          >
            {connected ? 'En temps réel' : 'Hors ligne'}
          </Typography>
        </Box>

        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.75,
            px: 1.5,
            py: 0.6,
            borderRadius: 999,
            border: '1px solid',
            borderColor: alpha(isError ? severityColors.critical : theme.palette.primary.main, 0.4),
            bgcolor: alpha(isError ? severityColors.critical : theme.palette.primary.main, 0.12),
          }}
        >
          {isError ? (
            <ErrorOutlineIcon sx={{ fontSize: 18, color: severityColors.critical }} />
          ) : (
            <GppGoodIcon sx={{ fontSize: 18, color: theme.palette.primary.main }} />
          )}
          <Typography
            variant="body2"
            sx={{
              fontWeight: 700,
              color: isError ? severityColors.critical : theme.palette.primary.main,
            }}
          >
            {isError ? 'Source(s) indisponible(s)' : 'Plateforme opérationnelle'}
          </Typography>
        </Box>

        <Box sx={{ flexGrow: 1 }} />

        {data && (
          <Button
            variant="contained"
            disableElevation
            startIcon={<DescriptionOutlinedIcon />}
            endIcon={<ChevronRightIcon />}
            component="a"
            href="/reports"
            onClick={(e) => {
              e.preventDefault();
              navigate('/reports');
            }}
            sx={{
              bgcolor: '#2f81f7',
              borderRadius: 2,
              px: 2.5,
              fontWeight: 700,
              boxShadow: '0 0 20px rgba(47,129,247,0.5)',
              '&:hover': { bgcolor: '#1f6feb', boxShadow: '0 0 24px rgba(47,129,247,0.65)' },
            }}
          >
            Générer un rapport
          </Button>
        )}
      </Paper>

      {isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}
      {isError && (
        <Alert severity="error">
          {problemDetail(error, 'Impossible de charger le tableau de bord.')}
        </Alert>
      )}

      {data && derived && (
        <>
          {/* Zone A — Posture */}
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(3, 1fr)', lg: 'repeat(6, 1fr)' },
              gap: 2,
              mb: 3,
            }}
          >
            <KpiTile
              label="Alertes totales"
              value={String(data.alertStats.total)}
              hint="tous statuts confondus"
              color={severityColors.info}
              icon={<BugReportIcon />}
              onClick={() => navigate('/alerts')}
            />
            <KpiTile
              label="Alertes critiques"
              value={String(data.alertStats.bySeverity.CRITICAL ?? 0)}
              hint="sévérité CRITICAL"
              color={severityColors.critical}
              icon={<GppMaybeIcon />}
              onClick={() => navigate('/alerts')}
            />
            <KpiTile
              label="Incidents ouverts"
              value={String(derived.incidentsOpen)}
              hint={`sur ${data.incidentsTotal} au total`}
              color={severityColors.high}
              icon={<LocalFireDepartmentIcon />}
              onClick={() => navigate('/incidents')}
            />
            <KpiTile
              label="Actifs critiques exposés"
              value={String(derived.criticalExposed.length)}
              hint="critiques + Internet"
              color={severityColors.medium}
              icon={<DnsIcon />}
              onClick={() => navigate('/assets')}
            />
            <KpiTile
              label="Taux de faux positifs"
              value={`${derived.fpRate}%`}
              hint="des alertes triées"
              color={severityColors.low}
              icon={<ShieldIcon />}
              onClick={() => navigate('/alerts')}
            />
            <KpiTile
              label="Couverture MITRE"
              value={`${derived.coveragePct}%`}
              hint={`${derived.observedCount}/${data.techniques.length} techniques`}
              color={severityColors.info}
              icon={<PublicIcon />}
              onClick={() => navigate('/mitre')}
            />
          </Box>

          {/* Zone B — Activité, seule sur sa ligne (élargie sur demande) */}
          <Panel
            title="Activité — 7 derniers jours"
            action={{ label: 'Voir les alertes', onClick: () => navigate('/alerts') }}
          >
            {timeline && <EChart option={timeline} height={300} />}
          </Panel>
          <Box sx={{ mb: 3 }} />

          {/* Zone C1 — Alertes récentes, MITRE ATT&CK, Sources SOC */}
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', md: '1fr 1fr', lg: 'repeat(3, 1fr)' },
              gap: 2,
              mb: 3,
              alignItems: 'stretch',
            }}
          >
            <AccentPanel
              title="Alertes récentes"
              icon={<BugReportIcon sx={{ fontSize: 18 }} />}
              color={severityColors.critical}
              watermark={<BugReportIcon />}
              action={{ label: 'Tout voir', onClick: () => navigate('/alerts') }}
            >
              <Stack spacing={1.25} sx={{ maxHeight: 320, overflowY: 'auto' }}>
                {data.recentAlerts.length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    Aucune alerte récente.
                  </Typography>
                )}
                {data.recentAlerts.map((a) => (
                  <Stack
                    key={a.id}
                    direction="row"
                    spacing={1}
                    sx={{ alignItems: 'center', cursor: 'pointer' }}
                    onClick={() => navigate('/alerts')}
                  >
                    <SeverityChip severity={a.severity} />
                    <Typography variant="body2" noWrap sx={{ flexGrow: 1 }}>
                      {a.title}
                    </Typography>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      noWrap
                      sx={{ minWidth: 64, opacity: 0.65 }}
                    >
                      {a.source}
                    </Typography>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ whiteSpace: 'nowrap', opacity: 0.65 }}
                    >
                      {timeAgo(a.detectedAt)}
                    </Typography>
                    <Box
                      sx={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        flexShrink: 0,
                        bgcolor:
                          severityColors[a.severity.toLowerCase() as keyof typeof severityColors],
                      }}
                    />
                  </Stack>
                ))}
              </Stack>
            </AccentPanel>

            <AccentPanel
              title="MITRE ATT&CK"
              icon={<PublicIcon sx={{ fontSize: 18 }} />}
              color={severityColors.info}
              watermark={<PublicIcon />}
              action={{ label: 'Explorer', onClick: () => navigate('/mitre') }}
            >
              {mitreDonut && (
                <Box sx={{ display: 'flex', justifyContent: 'center' }}>
                  <Box
                    sx={{
                      position: 'relative',
                      width: 180,
                      height: 160,
                      borderRadius: '50%',
                      filter: `drop-shadow(0 0 14px ${alpha(severityColors.low, 0.35)})`,
                    }}
                  >
                    <EChart option={mitreDonut} height={160} />
                    <Box
                      sx={{
                        position: 'absolute',
                        inset: 0,
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        pointerEvents: 'none',
                      }}
                    >
                      <Typography variant="h5" sx={{ fontWeight: 800, lineHeight: 1 }}>
                        {derived.coveragePct}%
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {derived.observedCount}/{data.techniques.length}
                      </Typography>
                    </Box>
                  </Box>
                </Box>
              )}
              <Typography
                variant="caption"
                sx={{
                  display: 'block',
                  textAlign: 'center',
                  fontWeight: 800,
                  textTransform: 'uppercase',
                  letterSpacing: 0.6,
                  color: severityColors.info,
                  mt: 1,
                  mb: 0.75,
                }}
              >
                Techniques les plus citées
              </Typography>
              <Stack spacing={1.25}>
                {derived.topTechniques.length === 0 && (
                  <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
                    Aucune technique observée.
                  </Typography>
                )}
                {derived.topTechniques.map((t) => (
                  <Stack
                    key={t.attackId}
                    direction="row"
                    spacing={1}
                    sx={{ justifyContent: 'space-between', cursor: 'pointer' }}
                    onClick={() => navigate(`/mitre?selected=${t.attackId}`)}
                  >
                    <Typography variant="caption" noWrap>
                      <b style={{ fontFamily: 'monospace' }}>{t.attackId}</b> {t.name}
                    </Typography>
                    <Typography variant="caption" sx={{ fontWeight: 700 }}>
                      {t.alertCount}
                    </Typography>
                  </Stack>
                ))}
              </Stack>
            </AccentPanel>

            <AccentPanel
              title="Sources SOC"
              icon={<HubIcon sx={{ fontSize: 18 }} />}
              color="#39c5cf"
              watermark={<HubIcon />}
              action={{ label: 'Voir les alertes', onClick: () => navigate('/alerts') }}
            >
              {sources && <EChart option={sources} height={280} />}
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ display: 'block', mt: 1, opacity: 0.65 }}
              >
                {derived.sourcesTotal} source{derived.sourcesTotal > 1 ? 's' : ''} active
                {derived.sourcesTotal > 1 ? 's' : ''} · {data.alertStats.total} alerte
                {data.alertStats.total > 1 ? 's' : ''} au total
              </Typography>
            </AccentPanel>
          </Box>

          {/* Zone C2 — Surface d'attaque, Incidents & Réponse, Posture des actifs, Threat Intelligence */}
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' },
              gap: 2,
              mb: 3,
              alignItems: 'stretch',
            }}
          >
            <AccentPanel
              title="Surface d'attaque"
              icon={<ShieldIcon sx={{ fontSize: 18 }} />}
              color="#8ecfff"
              watermark={<PublicIcon />}
              action={{ label: 'Voir les actifs', onClick: () => navigate('/assets') }}
            >
              <Stack spacing={1} sx={{ mb: 2 }}>
                <Box>
                  <Chip
                    label={`${derived.exposedPct}% exposés Internet`}
                    size="small"
                    sx={softChipSx(severityColors.critical)}
                  />
                </Box>
                <Typography variant="caption" color="text.secondary" sx={{ opacity: 0.65 }}>
                  {derived.internetFacingCount} exposé{derived.internetFacingCount > 1 ? 's' : ''} ·{' '}
                  {derived.internalCount} interne{derived.internalCount > 1 ? 's' : ''} ·{' '}
                  {data.assetsTotal} au total
                </Typography>
              </Stack>

              <SectionLabel color="#8ecfff">Exposition par criticité</SectionLabel>
              {derived.exposureByCriticality.length === 0 ? (
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                  Aucun actif enregistré.
                </Typography>
              ) : (
                <Box
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 56px 56px',
                    gap: 0.5,
                    alignItems: 'center',
                    mb: 1.5,
                  }}
                >
                  <Box />
                  <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
                    Internet
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
                    Interne
                  </Typography>
                  {derived.exposureByCriticality.map((row) => (
                    <Box key={row.criticality} sx={{ display: 'contents' }}>
                      <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                        <Box
                          sx={{
                            width: 8,
                            height: 8,
                            borderRadius: '50%',
                            flexShrink: 0,
                            bgcolor:
                              severityColors[
                                row.criticality.toLowerCase() as keyof typeof severityColors
                              ],
                          }}
                        />
                        <Typography variant="body2" noWrap>
                          {row.criticality}
                        </Typography>
                      </Stack>
                      <Box
                        sx={{
                          height: 28,
                          borderRadius: 1.5,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          bgcolor: alpha(
                            severityColors.critical,
                            row.internet === 0 ? 0.06 : (row.internet / derived.exposureMatrixMax) * 0.7 + 0.1,
                          ),
                        }}
                      >
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {row.internet}
                        </Typography>
                      </Box>
                      <Box
                        sx={{
                          height: 28,
                          borderRadius: 1.5,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          bgcolor: alpha(
                            theme.palette.text.secondary,
                            row.internal === 0 ? 0.06 : (row.internal / derived.exposureMatrixMax) * 0.35 + 0.06,
                          ),
                        }}
                      >
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {row.internal}
                        </Typography>
                      </Box>
                    </Box>
                  ))}
                </Box>
              )}

              <SectionLabel color="#8ecfff">Détail des actifs critiques exposés</SectionLabel>
              <Stack spacing={1.25}>
                {derived.criticalExposed.length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    Aucun actif critique exposé.
                  </Typography>
                )}
                {derived.criticalExposed.slice(0, 6).map((asset) => (
                  <Stack
                    key={asset.id}
                    direction="row"
                    spacing={1}
                    sx={{
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      cursor: 'pointer',
                    }}
                    onClick={() => navigate(`/assets?selected=${asset.id}`)}
                  >
                    <Typography variant="caption" noWrap>
                      {asset.hostname}
                    </Typography>
                    <Chip label="Internet" size="small" sx={softChipSx(theme.palette.error.main)} />
                  </Stack>
                ))}
              </Stack>
            </AccentPanel>

            <AccentPanel
              title="Incidents & Réponse"
              icon={<LocalFireDepartmentIcon sx={{ fontSize: 18 }} />}
              color={severityColors.high}
              watermark={<LocalFireDepartmentIcon />}
              action={{ label: 'Voir', onClick: () => navigate('/incidents') }}
            >
              <Stack spacing={1.25} sx={{ mb: 1.5 }}>
                {INCIDENT_STATUS_ORDER.filter((s) => derived.incidentsByStatus[s]).map((s) => (
                  <Stack key={s} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <IncidentStatusChip status={s} />
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                      {derived.incidentsByStatus[s]}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {INCIDENT_STATUS_LABELS[s]}
                    </Typography>
                  </Stack>
                ))}
              </Stack>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ display: 'block', cursor: 'pointer', mb: 1.5, opacity: 0.65 }}
                onClick={() => navigate('/soar')}
              >
                {data.playbooksActiveTotal} playbook{data.playbooksActiveTotal > 1 ? 's' : ''} actif
                {data.playbooksActiveTotal > 1 ? 's' : ''} · {derived.huntsExecutedRecently} requête
                {derived.huntsExecutedRecently > 1 ? 's' : ''} de chasse (7j, approx.)
              </Typography>
              <SectionLabel color={severityColors.high}>À traiter</SectionLabel>
              <Stack spacing={1.25}>
                {derived.openIncidents.length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    Aucun incident ouvert.
                  </Typography>
                )}
                {derived.openIncidents.map((incident) => (
                  <Stack
                    key={incident.id}
                    direction="row"
                    spacing={1}
                    sx={{ alignItems: 'center', cursor: 'pointer' }}
                    onClick={() => navigate(`/incidents?selected=${incident.id}`)}
                  >
                    <Typography variant="caption" noWrap sx={{ flexGrow: 1 }}>
                      <b style={{ fontFamily: 'monospace' }}>{incident.reference}</b>{' '}
                      {incident.title}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" noWrap sx={{ opacity: 0.65 }}>
                      {incident.assigneeUsername ?? 'Non assigné'}
                    </Typography>
                  </Stack>
                ))}
              </Stack>
            </AccentPanel>

            <AccentPanel
              title="Posture des actifs"
              icon={<DnsIcon sx={{ fontSize: 18 }} />}
              color={severityColors.medium}
              watermark={<DnsIcon />}
              action={{ label: 'Voir les actifs', onClick: () => navigate('/assets') }}
            >
              <Stack
                direction="row"
                sx={{ height: 10, borderRadius: 999, overflow: 'hidden', mb: 1 }}
              >
                {CRITICALITY_ORDER.filter((c) => derived.assetsByCriticality[c]).map((c) => (
                  <Box
                    key={c}
                    sx={{
                      flex: derived.assetsByCriticality[c],
                      bgcolor: severityColors[c.toLowerCase() as keyof typeof severityColors],
                    }}
                  />
                ))}
              </Stack>
              <Stack spacing={1.25}>
                {CRITICALITY_ORDER.filter((c) => derived.assetsByCriticality[c]).map((c) => (
                  <Stack key={c} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Box
                      sx={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        bgcolor: severityColors[c.toLowerCase() as keyof typeof severityColors],
                      }}
                    />
                    <Typography variant="body2" sx={{ flexGrow: 1 }}>
                      {c}
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                      {derived.assetsByCriticality[c]}
                    </Typography>
                  </Stack>
                ))}
              </Stack>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ display: 'block', mt: 1, mb: 1.5, opacity: 0.65 }}
              >
                {data.assetsTotal} actif{data.assetsTotal > 1 ? 's' : ''} au total
              </Typography>
              <SectionLabel color={severityColors.medium}>Par type</SectionLabel>
              <Stack spacing={1.25} sx={{ mb: 1.5 }}>
                {(Object.entries(derived.assetsByType) as [AssetType, number][])
                  .sort(([, a], [, b]) => b - a)
                  .map(([type, count]) => (
                    <Stack key={type} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <Typography variant="body2" sx={{ flexGrow: 1 }}>
                        {ASSET_TYPE_LABELS[type]}
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        {count}
                      </Typography>
                    </Stack>
                  ))}
              </Stack>
              <SectionLabel color={severityColors.medium}>Connexion agent</SectionLabel>
              <Stack spacing={1.25}>
                {Object.keys(derived.agentStatusCounts).length === 0 && (
                  <Typography variant="caption" color="text.secondary">
                    Aucun actif rapporté par un connecteur d'agents.
                  </Typography>
                )}
                {(Object.entries(derived.agentStatusCounts) as [AgentConnectionStatus, number][])
                  .sort(([, a], [, b]) => b - a)
                  .map(([status, count]) => (
                    <Stack key={status} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <Box
                        sx={{
                          width: 8,
                          height: 8,
                          borderRadius: '50%',
                          bgcolor:
                            status === 'ACTIVE'
                              ? severityColors.low
                              : status === 'DISCONNECTED'
                                ? severityColors.critical
                                : theme.palette.text.secondary,
                        }}
                      />
                      <Typography variant="body2" sx={{ flexGrow: 1 }}>
                        {AGENT_CONNECTION_STATUS_LABELS[status]}
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        {count}
                      </Typography>
                    </Stack>
                  ))}
              </Stack>
            </AccentPanel>

            <AccentPanel
              title="Threat Intelligence"
              icon={<TravelExploreIcon sx={{ fontSize: 18 }} />}
              color={severityColors.low}
              watermark={<TravelExploreIcon />}
              action={{ label: 'Explorer', onClick: () => navigate('/intelligence') }}
            >
              <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1.5 }}>
                <Typography variant="h4" sx={{ fontWeight: 800, lineHeight: 1 }}>
                  {data.iocsActiveTotal}
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ opacity: 0.65 }}>
                  indicateur{data.iocsActiveTotal > 1 ? 's' : ''} actif
                  {data.iocsActiveTotal > 1 ? 's' : ''}
                </Typography>
              </Stack>
              <Stack spacing={1.5}>
                {data.iocs.length === 0 && (
                  <Typography variant="caption" color="text.secondary">
                    Aucun indicateur actif.
                  </Typography>
                )}
                {data.iocs.slice(0, 4).map((ioc) => (
                  <Stack
                    key={ioc.id}
                    spacing={0.5}
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate('/intelligence')}
                  >
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <IocTypeChip type={ioc.type} />
                      <Typography variant="caption" noWrap sx={{ flexGrow: 1, fontFamily: 'monospace' }}>
                        {ioc.value}
                      </Typography>
                    </Stack>
                    <ConfidenceBar confidence={ioc.confidence} />
                  </Stack>
                ))}
              </Stack>
            </AccentPanel>
          </Box>

          {/* Zone D — Activité récente */}
          <Panel title="Activité récente">
            <Stack spacing={0}>
              {derived.activity.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  Aucune activité récente.
                </Typography>
              )}
              {derived.activity.map((row, index) => (
                <Stack
                  key={`${row.kind}-${index}`}
                  direction="row"
                  spacing={1.5}
                  sx={{ cursor: 'pointer' }}
                  onClick={row.onClick}
                >
                  <Box
                    sx={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      width: 12,
                    }}
                  >
                    <Box
                      sx={{
                        width: 9,
                        height: 9,
                        borderRadius: '50%',
                        flexShrink: 0,
                        mt: 0.6,
                        bgcolor: ACTIVITY_KIND_COLOR[row.kind],
                      }}
                    />
                    {index < derived.activity.length - 1 && (
                      <Box sx={{ width: '2px', flexGrow: 1, bgcolor: 'divider', mt: 0.5 }} />
                    )}
                  </Box>
                  <Stack
                    direction="row"
                    spacing={1.5}
                    sx={{ alignItems: 'center', flexGrow: 1, minWidth: 0, pb: 1.25 }}
                  >
                    <Chip size="small" label={row.detail} sx={{ minWidth: 140 }} />
                    <Typography variant="body2" noWrap sx={{ flexGrow: 1 }}>
                      {row.label}
                    </Typography>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ whiteSpace: 'nowrap' }}
                    >
                      {formatDate(row.date)}
                    </Typography>
                  </Stack>
                </Stack>
              ))}
            </Stack>
          </Panel>
        </>
      )}
    </Box>
  );
}

export default DashboardPage;
