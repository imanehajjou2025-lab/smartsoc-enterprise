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
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import DnsIcon from '@mui/icons-material/Dns';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined';
import GppMaybeIcon from '@mui/icons-material/GppMaybe';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import PublicIcon from '@mui/icons-material/Public';
import ShieldIcon from '@mui/icons-material/Shield';
import type { EChartsOption } from 'echarts';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import EChart from '../../shared/components/EChart';
import { resolveChipColor, softChipSx } from '../../shared/components/chipStyles';
import { SeverityChip } from '../alerts/chips';
import { useAlertsRealtime } from '../alerts/useAlertsRealtime';
import { IncidentStatusChip, INCIDENT_STATUS_LABELS } from '../incidents/incidentChips';
import type { IncidentStatus } from '../incidents/incidentsApi';
import type { Asset, AssetCriticality } from '../assets/assetsApi';
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

/** Anneaux purement décoratifs (dégradé cyan → violet, dans l'esprit de la marque ISIX) — aucune donnée n'y est encodée. */
const ATTACK_SURFACE_RING_COLORS = ['#39c5cf', '#2f81f7', '#6e7bfa', '#8957e5', '#a371f7'];

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
        radius: ['62%', '86%'],
        label: { show: false },
        silent: true,
        data: [
          { name: 'Observées', value: observed, itemStyle: { color: severityColors.low } },
          {
            name: 'Non observées',
            value: Math.max(0, total - observed),
            itemStyle: { color: 'rgba(139,148,158,0.3)' },
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
  const entries = Object.entries(stats.bySource).slice(0, 5).reverse();
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

  const timeline = useMemo(() => (data ? timelineOption(data.alertStats) : null), [data]);
  const sources = useMemo(() => (data ? sourcesBarOption(data.alertStats) : null), [data]);

  const derived = useMemo(() => {
    if (!data) return null;
    const { alertStats, incidents, assets, techniques, coverage, hunts } = data;

    const incidentsByStatus = countBy(incidents, (i) => i.status);
    const incidentsOpen = incidents.filter((i) => i.status !== 'CLOSED').length;

    const assetsByCriticality = countBy(assets, (a) => a.criticality);
    const criticalExposed = assets.filter(isExposedCritical);
    const internetFacingCount = assets.filter((a) => a.exposure === 'INTERNET_FACING').length;

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
      assetsByCriticality,
      criticalExposed,
      internetFacingCount,
      fpRate,
      coveragePct,
      observedCount: observed.length,
      topTechniques,
      huntsExecutedRecently,
      activity,
    };
  }, [data, navigate]);

  const mitreDonut = useMemo(() => {
    if (!data || !derived) return null;
    return mitreCoverageOption(derived.observedCount, data.techniques.length);
  }, [data, derived]);

  return (
    <Box>
      <Stack direction="row" spacing={1.5} sx={{ mb: 2, alignItems: 'center' }}>
        <Typography variant="h5" component="h2">
          Dashboard
        </Typography>
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.75,
            px: 1.25,
            py: 0.4,
            borderRadius: 999,
            border: '1px solid',
            borderColor: alpha(connected ? severityColors.low : theme.palette.text.disabled, 0.4),
            bgcolor: alpha(connected ? severityColors.low : theme.palette.text.disabled, 0.1),
          }}
        >
          <Box
            sx={{
              width: 7,
              height: 7,
              borderRadius: '50%',
              bgcolor: connected ? severityColors.low : theme.palette.text.disabled,
              boxShadow: connected ? `0 0 6px 2px ${alpha(severityColors.low, 0.7)}` : 'none',
              animation: connected ? `${pulse} 1.6s ease-in-out infinite` : 'none',
            }}
          />
          <Typography
            variant="caption"
            sx={{
              fontWeight: 700,
              color: connected ? severityColors.low : 'text.secondary',
            }}
          >
            {connected ? 'En temps réel' : 'Hors ligne'}
          </Typography>
        </Box>
        <Chip
          size="small"
          icon={
            isError ? <ErrorOutlineIcon fontSize="small" /> : <CheckCircleIcon fontSize="small" />
          }
          label={isError ? 'Source(s) indisponible(s)' : 'Plateforme opérationnelle'}
          sx={softChipSx(resolveChipColor(theme, isError ? 'error' : 'success'))}
        />
        {data && (
          <Button
            size="small"
            variant="outlined"
            startIcon={<DescriptionOutlinedIcon fontSize="small" />}
            component="a"
            href="/reports"
            onClick={(e) => {
              e.preventDefault();
              navigate('/reports');
            }}
          >
            Générer un rapport
          </Button>
        )}
      </Stack>

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

          {/* Zone B — Activité */}
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' },
              gap: 2,
              mb: 3,
            }}
          >
            <Panel
              title="Activité — 7 derniers jours"
              action={{ label: 'Voir les alertes', onClick: () => navigate('/alerts') }}
            >
              {timeline && <EChart option={timeline} height={240} />}
            </Panel>
            <Panel
              title="Alertes récentes"
              action={{ label: 'Tout voir', onClick: () => navigate('/alerts') }}
            >
              <Stack spacing={1} sx={{ maxHeight: 260, overflowY: 'auto' }}>
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
                    <Typography variant="body2" noWrap sx={{ flexGrow: 1, fontWeight: 600 }}>
                      {a.title}
                    </Typography>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      noWrap
                      sx={{ minWidth: 64 }}
                    >
                      {a.source}
                    </Typography>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ whiteSpace: 'nowrap' }}
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
            </Panel>
          </Box>

          {/* Zone C — Détection & Réponse */}
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', md: '1fr 1fr', lg: 'repeat(3, 1fr)' },
              gap: 2,
              mb: 3,
            }}
          >
            <Panel
              title="MITRE ATT&CK"
              action={{ label: 'Explorer', onClick: () => navigate('/mitre') }}
            >
              {mitreDonut && (
                <Box sx={{ display: 'flex', justifyContent: 'center' }}>
                  <Box sx={{ position: 'relative', width: 180, height: 160 }}>
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
              <Stack spacing={0.75} sx={{ mt: 1 }}>
                {derived.topTechniques.length === 0 && (
                  <Typography variant="caption" color="text.secondary">
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
            </Panel>

            <Panel
              title="Surface d'attaque"
              action={{ label: 'Voir les actifs', onClick: () => navigate('/assets') }}
            >
              <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1.5 }}>
                <Box sx={{ position: 'relative', width: 92, height: 92, flexShrink: 0 }}>
                  {ATTACK_SURFACE_RING_COLORS.map((c, i) => (
                    <Box
                      key={c}
                      sx={{
                        position: 'absolute',
                        inset: i * 9,
                        borderRadius: '50%',
                        background: `conic-gradient(from ${180 + i * 18}deg, ${c}, transparent 55%)`,
                        WebkitMask:
                          'radial-gradient(farthest-side, transparent calc(100% - 3px), #000 calc(100% - 3px))',
                        mask: 'radial-gradient(farthest-side, transparent calc(100% - 3px), #000 calc(100% - 3px))',
                      }}
                    />
                  ))}
                  <Box
                    sx={{
                      position: 'absolute',
                      inset: 27,
                      borderRadius: '50%',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      background: 'radial-gradient(circle at 35% 30%, #1f3b73, #0b1626 75%)',
                      boxShadow: '0 0 14px 2px rgba(57,197,207,0.35)',
                    }}
                  >
                    <ShieldIcon sx={{ color: '#8ecfff', fontSize: 22 }} />
                  </Box>
                </Box>
                <Stack spacing={0.75} sx={{ flexGrow: 1, minWidth: 0 }}>
                  <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                    <Typography variant="caption" color="text.secondary">
                      Critiques exposés Internet
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                      {derived.criticalExposed.length}
                    </Typography>
                  </Stack>
                  <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                    <Typography variant="caption" color="text.secondary">
                      Actifs exposés Internet
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                      {derived.internetFacingCount}
                    </Typography>
                  </Stack>
                  <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                    <Typography variant="caption" color="text.secondary">
                      Actifs au total
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                      {data.assetsTotal}
                    </Typography>
                  </Stack>
                </Stack>
              </Stack>
              <Stack spacing={0.75}>
                {derived.criticalExposed.length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    Aucun actif critique exposé.
                  </Typography>
                )}
                {derived.criticalExposed.slice(0, 4).map((asset) => (
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
            </Panel>

            <Panel
              title="Sources SOC"
              action={{ label: 'Voir les alertes', onClick: () => navigate('/alerts') }}
            >
              {sources && <EChart option={sources} height={200} />}
            </Panel>

            <Panel
              title="Incidents & Réponse"
              action={{ label: 'Voir', onClick: () => navigate('/incidents') }}
            >
              <Stack spacing={0.75} sx={{ mb: 1.5 }}>
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
                sx={{ display: 'block', cursor: 'pointer' }}
                onClick={() => navigate('/soar')}
              >
                {data.playbooksActiveTotal} playbook{data.playbooksActiveTotal > 1 ? 's' : ''} actif
                {data.playbooksActiveTotal > 1 ? 's' : ''} · {derived.huntsExecutedRecently} requête
                {derived.huntsExecutedRecently > 1 ? 's' : ''} de chasse (7j, approx.)
              </Typography>
            </Panel>

            <Panel
              title="Posture des actifs"
              action={{ label: 'Voir les actifs', onClick: () => navigate('/assets') }}
            >
              <Stack spacing={0.75}>
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
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                {data.assetsTotal} actif{data.assetsTotal > 1 ? 's' : ''} actif
                {data.assetsTotal > 1 ? 's' : ''} au total
              </Typography>
            </Panel>

            <Panel
              title="Threat Intelligence"
              action={{ label: 'Explorer', onClick: () => navigate('/intelligence') }}
            >
              <Typography variant="h4" sx={{ fontWeight: 800, mb: 0.5 }}>
                {data.iocsActiveTotal}
              </Typography>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ display: 'block', mb: 1.5 }}
              >
                indicateurs actifs
              </Typography>
              <Stack spacing={0.75}>
                {data.iocs.slice(0, 4).map((ioc) => (
                  <Typography
                    key={ioc.id}
                    variant="caption"
                    noWrap
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate('/intelligence')}
                  >
                    <b>{ioc.type}</b> {ioc.value}
                  </Typography>
                ))}
              </Stack>
            </Panel>
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
