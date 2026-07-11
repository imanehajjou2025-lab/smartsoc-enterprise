import { useMemo } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import type { EChartsOption } from 'echarts';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import EChart from '../../shared/components/EChart';
import { useAlertsRealtime } from '../alerts/useAlertsRealtime';
import { getAlertStats, type AlertStats } from './dashboardApi';

const SEVERITY_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'] as const;

function StatCard({ label, value, accent }: { label: string; value: number; accent?: string }) {
  return (
    <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 180 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="h4" sx={{ fontWeight: 600, color: accent }}>
        {value}
      </Typography>
    </Paper>
  );
}

function severityDonutOption(stats: AlertStats): EChartsOption {
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#8b949e' } },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        label: { show: false },
        data: SEVERITY_ORDER.filter((s) => stats.bySeverity[s]).map((s) => ({
          name: s,
          value: stats.bySeverity[s],
          itemStyle: { color: severityColors[s.toLowerCase() as keyof typeof severityColors] },
        })),
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

/** Vue globale SOC : KPIs et activité, rafraîchis en temps réel. */
function DashboardPage() {
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['alerts', 'stats'],
    queryFn: getAlertStats,
  });
  const { connected } = useAlertsRealtime();

  const donut = useMemo(() => (data ? severityDonutOption(data) : null), [data]);
  const timeline = useMemo(() => (data ? timelineOption(data) : null), [data]);
  const sources = useMemo(() => (data ? sourcesBarOption(data) : null), [data]);

  return (
    <Box>
      <Stack direction="row" spacing={1.5} sx={{ mb: 2, alignItems: 'center' }}>
        <Typography variant="h5" component="h2">
          Dashboard
        </Typography>
        <Chip
          size="small"
          label={connected ? 'Temps réel' : 'Hors ligne'}
          color={connected ? 'success' : 'default'}
          variant="outlined"
        />
      </Stack>

      {isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}
      {isError && (
        <Alert severity="error">
          {problemDetail(error, 'Impossible de charger les statistiques.')}
        </Alert>
      )}

      {data && (
        <>
          <Stack direction="row" spacing={2} useFlexGap sx={{ mb: 3, flexWrap: 'wrap' }}>
            <StatCard label="Alertes totales" value={data.total} />
            <StatCard
              label="Nouvelles (à trier)"
              value={data.byStatus.NEW ?? 0}
              accent={severityColors.info}
            />
            <StatCard
              label="Critiques"
              value={data.bySeverity.CRITICAL ?? 0}
              accent={severityColors.critical}
            />
            <StatCard
              label="Faux positifs"
              value={data.byStatus.FALSE_POSITIVE ?? 0}
              accent={severityColors.medium}
            />
          </Stack>

          <Box
            sx={{
              display: 'grid',
              gap: 2,
              gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' },
            }}
          >
            <Paper variant="outlined" sx={{ p: 2, gridColumn: { lg: '1 / -1' } }}>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                Activité — 7 derniers jours
              </Typography>
              {timeline && <EChart option={timeline} height={260} />}
            </Paper>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                Répartition par sévérité
              </Typography>
              {donut && <EChart option={donut} height={280} />}
            </Paper>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                Top sources
              </Typography>
              {sources && <EChart option={sources} height={280} />}
            </Paper>
          </Box>
        </>
      )}
    </Box>
  );
}

export default DashboardPage;
