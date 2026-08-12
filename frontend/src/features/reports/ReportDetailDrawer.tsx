import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import GppMaybeIcon from '@mui/icons-material/GppMaybe';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined';
import SummarizeOutlinedIcon from '@mui/icons-material/SummarizeOutlined';
import { alpha, useTheme } from '@mui/material/styles';
import { useQuery } from '@tanstack/react-query';
import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import ActionCard from '../../shared/components/ActionCard';
import DetailDrawerHeader from '../../shared/components/DetailDrawerHeader';
import EChart from '../../shared/components/EChart';
import SectionLabel from '../../shared/components/SectionLabel';
import { StatusChip } from '../alerts/chips';
import type { AlertSeverity, AlertStatus } from '../alerts/alertsApi';
import { listTechniques } from '../mitre/mitreApi';
import { downloadReportCsv, downloadReportPdf, getReport, type ReportMetrics } from './reportsApi';

const SEVERITY_ORDER: AlertSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const STATUS_ORDER: AlertStatus[] = [
  'NEW',
  'ACKNOWLEDGED',
  'IN_PROGRESS',
  'RESOLVED',
  'FALSE_POSITIVE',
];

function KpiCard({
  label,
  value,
  hint,
  color,
}: {
  label: string;
  value: string;
  hint?: string;
  color: string;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 1.5, flex: 1, minWidth: 130 }}>
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{ textTransform: 'uppercase', letterSpacing: 0.3, display: 'block' }}
      >
        {label}
      </Typography>
      <Typography variant="h5" sx={{ fontWeight: 800, color, lineHeight: 1.2 }}>
        {value}
      </Typography>
      {hint && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
          {hint}
        </Typography>
      )}
    </Paper>
  );
}

function Panel({
  title,
  color,
  caveat,
  children,
}: {
  title: string;
  color: string;
  caveat?: string;
  children: React.ReactNode;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Box sx={{ mb: caveat ? 0.25 : 1.5 }}>
        <SectionLabel color={color}>{title}</SectionLabel>
      </Box>
      {caveat && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5, opacity: 0.75 }}>
          {caveat}
        </Typography>
      )}
      {children}
    </Paper>
  );
}

function severityDonutOption(alerts: ReportMetrics['alerts']): EChartsOption {
  const entries = SEVERITY_ORDER.filter((s) => alerts.bySeverity[s]);
  return {
    tooltip: { trigger: 'item' },
    legend: { show: false },
    series: [
      {
        type: 'pie',
        center: ['50%', '50%'],
        radius: ['52%', '82%'],
        label: { show: false },
        itemStyle: { borderRadius: 6, borderColor: 'transparent', borderWidth: 2 },
        data: entries.map((s) => {
          const color = severityColors[s.toLowerCase() as keyof typeof severityColors];
          return {
            name: s,
            value: alerts.bySeverity[s],
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
                { offset: 0, color },
                { offset: 1, color: alpha(color, 0.55) },
              ]),
              shadowColor: alpha(color, 0.5),
              shadowBlur: 10,
            },
          };
        }),
      },
    ],
  };
}

/** Anneau de couverture — même dégradé/lueur que le Dashboard, pour un rendu cohérent. */
function coverageGaugeOption(covered: number, total: number): EChartsOption {
  const pct = total > 0 ? Math.round((covered / total) * 100) : 0;
  return {
    series: [
      {
        type: 'pie',
        radius: ['72%', '92%'],
        startAngle: 90,
        silent: true,
        label: { show: false },
        emphasis: { scale: false },
        data: [
          {
            value: pct,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
                { offset: 0, color: severityColors.critical },
                { offset: 1, color: severityColors.high },
              ]),
              shadowColor: alpha(severityColors.critical, 0.55),
              shadowBlur: 12,
            },
          },
          { value: 100 - pct, itemStyle: { color: 'transparent' } },
        ],
      },
    ],
  };
}

interface Props {
  reportId: string | null;
  onClose: () => void;
}

/** Instantané figé d'indicateurs sur une période passée (ADR-013) — écran, export CSV et PDF. */
function ReportDetailDrawer({ reportId, onClose }: Props) {
  const theme = useTheme();
  const navigate = useNavigate();
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['report', reportId],
    queryFn: () => getReport(reportId!),
    enabled: Boolean(reportId),
  });

  // Taille réelle du catalogue ATT&CK — dérivée d'une pagination (size=1),
  // pas fabriquée : le pourcentage de couverture n'a de sens que rapporté
  // au nombre RÉEL de techniques du catalogue, jamais à une constante.
  const { data: catalogSize } = useQuery({
    queryKey: ['mitre-catalog-size'],
    queryFn: () => listTechniques({ page: 0, size: 1 }),
    select: (d) => d.totalElements,
    staleTime: 5 * 60_000,
  });

  const donut = useMemo(() => (data ? severityDonutOption(data.metrics.alerts) : null), [data]);

  // Deux taux dérivés des mêmes compteurs déjà chargés (rien de fabriqué) :
  // la part critique/élevée et la part traitée (résolue ou classée faux
  // positif) sur la période — utiles pour juger vite la charge et l'issue.
  const criticalHighPct =
    data && data.metrics.alerts.total > 0
      ? Math.round(
          (((data.metrics.alerts.bySeverity.CRITICAL ?? 0) +
            (data.metrics.alerts.bySeverity.HIGH ?? 0)) /
            data.metrics.alerts.total) *
            100,
        )
      : null;
  const handledPct =
    data && data.metrics.alerts.total > 0
      ? Math.round(
          (((data.metrics.alerts.byStatus.RESOLVED ?? 0) +
            (data.metrics.alerts.byStatus.FALSE_POSITIVE ?? 0)) /
            data.metrics.alerts.total) *
            100,
        )
      : null;

  return (
    <Drawer anchor="right" open={Boolean(reportId)} onClose={onClose}>
      <Box sx={{ width: 620, maxWidth: '94vw', p: 3 }}>
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
              color={severityColors.info}
              icon={<SummarizeOutlinedIcon sx={{ fontSize: 26 }} />}
              title={data.title}
              onClose={onClose}
            />

            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: 1.25,
                mb: 3,
              }}
            >
              <ActionCard
                icon={<DescriptionOutlinedIcon />}
                title="Export CSV"
                description="Télécharger les indicateurs bruts"
                color="#2f81f7"
                onClick={() => void downloadReportCsv(data)}
              />
              <ActionCard
                icon={<PictureAsPdfOutlinedIcon />}
                title="Export PDF"
                description="Télécharger le rapport formaté"
                color={severityColors.critical}
                onClick={() => void downloadReportPdf(data)}
              />
            </Box>

            <Stack spacing={2.5}>
              <Box>
                <SectionLabel color={severityColors.critical}>Alertes</SectionLabel>
                <Stack direction="row" spacing={1.5} useFlexGap sx={{ flexWrap: 'wrap', mb: 1.5 }}>
                  <KpiCard
                    label="Total"
                    value={String(data.metrics.alerts.total)}
                    color={severityColors.info}
                  />
                  <KpiCard
                    label="Critique + élevée"
                    value={criticalHighPct == null ? 'n/a' : `${criticalHighPct}%`}
                    hint="de la période"
                    color={severityColors.critical}
                  />
                  <KpiCard
                    label="Traitées"
                    value={handledPct == null ? 'n/a' : `${handledPct}%`}
                    hint="résolues ou faux positif"
                    color={severityColors.low}
                  />
                </Stack>
                <Box
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: '1fr', sm: '1.5fr 1fr' },
                    gap: 1.5,
                    alignItems: 'stretch',
                  }}
                >
                  <Paper
                    variant="outlined"
                    sx={{
                      p: 2,
                      position: 'relative',
                      overflow: 'hidden',
                      borderColor: alpha(severityColors.critical, 0.25),
                      background: (t) =>
                        `linear-gradient(160deg, ${alpha(severityColors.critical, t.palette.mode === 'dark' ? 0.14 : 0.06)} 0%, ${t.palette.background.paper} 65%)`,
                    }}
                  >
                    <GppMaybeIcon
                      aria-hidden
                      sx={{
                        position: 'absolute',
                        top: 10,
                        right: 10,
                        fontSize: 38,
                        color: alpha(severityColors.critical, 0.13),
                      }}
                    />
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ display: 'block', mb: 1, position: 'relative' }}
                    >
                      Par sévérité
                    </Typography>
                    <Box sx={{ width: 130, height: 130, mx: 'auto', mb: 2 }}>
                      {donut && <EChart option={donut} height={130} />}
                    </Box>
                    <Stack spacing={1.5}>
                      {SEVERITY_ORDER.filter((s) => data.metrics.alerts.bySeverity[s]).map((s) => {
                        const count = data.metrics.alerts.bySeverity[s] ?? 0;
                        const pct =
                          data.metrics.alerts.total > 0
                            ? Math.round((count / data.metrics.alerts.total) * 100)
                            : 0;
                        const color = severityColors[s.toLowerCase() as keyof typeof severityColors];
                        return (
                          <Stack
                            key={s}
                            direction="row"
                            spacing={1}
                            sx={{ alignItems: 'center', justifyContent: 'space-between' }}
                          >
                            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 0 }}>
                              <Box
                                sx={{
                                  width: 10,
                                  height: 10,
                                  borderRadius: '50%',
                                  flexShrink: 0,
                                  bgcolor: color,
                                  boxShadow: `0 0 6px 1px ${alpha(color, 0.55)}`,
                                }}
                              />
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {s}
                              </Typography>
                            </Stack>
                            <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
                              {count} · {pct}%
                            </Typography>
                          </Stack>
                        );
                      })}
                    </Stack>
                  </Paper>
                  <Paper
                    variant="outlined"
                    sx={{
                      p: 2,
                      position: 'relative',
                      overflow: 'hidden',
                      display: 'flex',
                      flexDirection: 'column',
                      borderColor: alpha(theme.palette.primary.main, 0.25),
                      background: (t) =>
                        `linear-gradient(160deg, ${alpha(t.palette.primary.main, t.palette.mode === 'dark' ? 0.14 : 0.06)} 0%, ${t.palette.background.paper} 65%)`,
                    }}
                  >
                    <MonitorHeartIcon
                      aria-hidden
                      sx={{
                        position: 'absolute',
                        top: 10,
                        right: 10,
                        fontSize: 38,
                        color: alpha(theme.palette.primary.main, 0.13),
                      }}
                    />
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ display: 'block', mb: 2, position: 'relative' }}
                    >
                      Par statut
                    </Typography>
                    <Stack spacing={1.75}>
                      {STATUS_ORDER.map((s) => {
                        const count = data.metrics.alerts.byStatus[s] ?? 0;
                        const pct =
                          data.metrics.alerts.total > 0
                            ? Math.round((count / data.metrics.alerts.total) * 100)
                            : 0;
                        return (
                          <Box key={s} sx={{ opacity: count === 0 ? 0.45 : 1 }}>
                            <Stack
                              direction="row"
                              spacing={1}
                              sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1 }}
                            >
                              <StatusChip status={s} />
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {count} · {pct}%
                              </Typography>
                            </Stack>
                            <Box
                              sx={{
                                height: 6,
                                borderRadius: 3,
                                bgcolor: alpha(theme.palette.primary.main, 0.12),
                                overflow: 'hidden',
                              }}
                            >
                              <Box
                                sx={{
                                  height: '100%',
                                  width: `${pct}%`,
                                  borderRadius: 3,
                                  bgcolor: theme.palette.primary.main,
                                }}
                              />
                            </Box>
                          </Box>
                        );
                      })}
                    </Stack>
                  </Paper>
                </Box>
              </Box>

              <Divider />

              <Box>
                <SectionLabel color={severityColors.high}>Incidents</SectionLabel>
                <Stack direction="row" spacing={1.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
                  <KpiCard
                    label="Ouverts"
                    value={String(data.metrics.incidents.opened)}
                    color={severityColors.high}
                  />
                  <KpiCard
                    label="Clôturés"
                    value={String(data.metrics.incidents.closed)}
                    color={severityColors.low}
                  />
                  <KpiCard
                    label="Résolution moyenne"
                    value={
                      data.metrics.incidents.avgResolutionHours === null
                        ? 'n/a'
                        : `${data.metrics.incidents.avgResolutionHours.toFixed(1)} h`
                    }
                    color={severityColors.info}
                    hint={
                      data.metrics.incidents.closed === 0 ? 'Aucun incident clôturé' : undefined
                    }
                  />
                </Stack>
              </Box>

              <Divider />

              <Box>
                <SectionLabel color="#2f81f7">Playbooks SOAR</SectionLabel>
                <Stack direction="row" spacing={1.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
                  <KpiCard
                    label="Démarrées"
                    value={String(data.metrics.soar.started)}
                    color={severityColors.info}
                  />
                  <KpiCard
                    label="Terminées"
                    value={String(data.metrics.soar.completed)}
                    color={severityColors.low}
                  />
                  <KpiCard
                    label="Annulées"
                    value={String(data.metrics.soar.cancelled)}
                    color={severityColors.medium}
                  />
                </Stack>
              </Box>

              <Divider />

              <Panel
                title="Threat Hunting"
                color={severityColors.medium}
                caveat="Requêtes sauvegardées dont la dernière exécution tombe dans la période — pas un compteur d'exécutions (aucun historique n'est persisté, ADR-011)."
              >
                <KpiCard
                  label="Requêtes exécutées"
                  value={String(data.metrics.huntQueriesExecuted)}
                  color={severityColors.info}
                />
              </Panel>

              <Panel
                title="Couverture MITRE ATT&CK"
                color={severityColors.critical}
                caveat="Instantané cumulatif au moment de la génération — pas borné à la période (la couverture est un état, pas un flux)."
              >
                <Box
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: '1fr', sm: '1fr 140px' },
                    gap: 1.5,
                    mb: 1.5,
                    alignItems: 'center',
                  }}
                >
                  <KpiCard
                    label="Techniques couvertes"
                    value={
                      catalogSize
                        ? `${data.metrics.mitreDistinctTechniquesCovered} / ${catalogSize}`
                        : String(data.metrics.mitreDistinctTechniquesCovered)
                    }
                    hint={
                      catalogSize
                        ? `${Math.round((data.metrics.mitreDistinctTechniquesCovered / catalogSize) * 100)}% du catalogue`
                        : undefined
                    }
                    color={severityColors.critical}
                  />
                  {catalogSize != null && catalogSize > 0 && (
                    <Box sx={{ position: 'relative', width: 120, height: 120, mx: 'auto' }}>
                      <EChart
                        option={coverageGaugeOption(data.metrics.mitreDistinctTechniquesCovered, catalogSize)}
                        height={120}
                      />
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
                        <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1 }}>
                          {Math.round((data.metrics.mitreDistinctTechniquesCovered / catalogSize) * 100)}%
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ opacity: 0.7 }}>
                          couverture
                        </Typography>
                      </Box>
                    </Box>
                  )}
                </Box>
                {data.metrics.mitreTopTechniques.length === 0 ? (
                  <Typography variant="caption" color="text.secondary">
                    Aucune technique observée.
                  </Typography>
                ) : (
                  <Stack spacing={1}>
                    {(() => {
                      const maxCount = Math.max(
                        ...data.metrics.mitreTopTechniques.map((t) => t.alertCount),
                      );
                      return data.metrics.mitreTopTechniques.map((t) => (
                        <Box
                          key={t.attackId}
                          sx={{ cursor: 'pointer' }}
                          onClick={() => navigate(`/mitre?selected=${t.attackId}`)}
                        >
                          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.4 }}>
                            <Typography
                              variant="body2"
                              sx={{ fontFamily: 'monospace', fontWeight: 700, flexGrow: 1 }}
                            >
                              {t.attackId}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {t.alertCount} alerte{t.alertCount > 1 ? 's' : ''}
                            </Typography>
                          </Stack>
                          <Box
                            sx={{
                              height: 5,
                              borderRadius: 3,
                              bgcolor: alpha(severityColors.critical, 0.12),
                              overflow: 'hidden',
                            }}
                          >
                            <Box
                              sx={{
                                height: '100%',
                                width: `${(t.alertCount / maxCount) * 100}%`,
                                borderRadius: 3,
                                background: `linear-gradient(90deg, ${severityColors.high}, ${severityColors.critical})`,
                              }}
                            />
                          </Box>
                        </Box>
                      ));
                    })()}
                  </Stack>
                )}
              </Panel>
            </Stack>
          </>
        )}
      </Box>
    </Drawer>
  );
}

export default ReportDetailDrawer;
