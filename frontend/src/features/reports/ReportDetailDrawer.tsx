import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined';
import { useQuery } from '@tanstack/react-query';
import type { EChartsOption } from 'echarts';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import EChart from '../../shared/components/EChart';
import { StatusChip } from '../alerts/chips';
import type { AlertSeverity, AlertStatus } from '../alerts/alertsApi';
import { downloadReportCsv, downloadReportPdf, getReport, type ReportMetrics } from './reportsApi';

const SEVERITY_ORDER: AlertSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const STATUS_ORDER: AlertStatus[] = [
  'NEW',
  'ACKNOWLEDGED',
  'IN_PROGRESS',
  'RESOLVED',
  'FALSE_POSITIVE',
];

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

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
  caveat,
  children,
}: {
  title: string;
  caveat?: string;
  children: React.ReactNode;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle2" sx={{ mb: caveat ? 0.25 : 1.5 }}>
        {title}
      </Typography>
      {caveat && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
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
    legend: { bottom: 0, textStyle: { color: '#8b949e' } },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        label: { show: false },
        data: entries.map((s) => ({
          name: s,
          value: alerts.bySeverity[s],
          itemStyle: { color: severityColors[s.toLowerCase() as keyof typeof severityColors] },
        })),
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
  const navigate = useNavigate();
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['report', reportId],
    queryFn: () => getReport(reportId!),
    enabled: Boolean(reportId),
  });

  const donut = useMemo(() => (data ? severityDonutOption(data.metrics.alerts) : null), [data]);

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
            <Typography variant="h6" sx={{ mb: 0.5 }}>
              {data.title}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
              Période : {formatDate(data.periodStart)} → {formatDate(data.periodEnd)}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
              Généré le {formatDate(data.generatedAt)} par {data.generatedBy}
            </Typography>

            <Stack direction="row" spacing={1} sx={{ mb: 3 }}>
              <Button
                size="small"
                variant="outlined"
                startIcon={<DescriptionOutlinedIcon />}
                onClick={() => void downloadReportCsv(data)}
              >
                Export CSV
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<PictureAsPdfOutlinedIcon />}
                onClick={() => void downloadReportPdf(data)}
              >
                Export PDF
              </Button>
            </Stack>

            <Stack spacing={2.5}>
              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  Alertes
                </Typography>
                <Stack direction="row" spacing={1.5} useFlexGap sx={{ flexWrap: 'wrap', mb: 1.5 }}>
                  <KpiCard
                    label="Total"
                    value={String(data.metrics.alerts.total)}
                    color={severityColors.info}
                  />
                </Stack>
                <Box
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
                    gap: 1.5,
                  }}
                >
                  <Paper variant="outlined" sx={{ p: 1.5 }}>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ display: 'block', mb: 1 }}
                    >
                      Par sévérité
                    </Typography>
                    {donut && <EChart option={donut} height={160} />}
                  </Paper>
                  <Paper variant="outlined" sx={{ p: 1.5 }}>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ display: 'block', mb: 1 }}
                    >
                      Par statut
                    </Typography>
                    <Stack spacing={0.75}>
                      {STATUS_ORDER.filter((s) => data.metrics.alerts.byStatus[s]).map((s) => (
                        <Stack key={s} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                          <StatusChip status={s} />
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                            {data.metrics.alerts.byStatus[s]}
                          </Typography>
                        </Stack>
                      ))}
                      {Object.keys(data.metrics.alerts.byStatus).length === 0 && (
                        <Typography variant="caption" color="text.secondary">
                          Aucune alerte sur la période.
                        </Typography>
                      )}
                    </Stack>
                  </Paper>
                </Box>
              </Box>

              <Divider />

              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  Incidents
                </Typography>
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
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  Playbooks SOAR
                </Typography>
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
                caveat="Instantané cumulatif au moment de la génération — pas borné à la période (la couverture est un état, pas un flux)."
              >
                <Stack direction="row" spacing={1.5} useFlexGap sx={{ flexWrap: 'wrap', mb: 1.5 }}>
                  <KpiCard
                    label="Techniques couvertes"
                    value={String(data.metrics.mitreDistinctTechniquesCovered)}
                    color={severityColors.critical}
                  />
                </Stack>
                {data.metrics.mitreTopTechniques.length === 0 ? (
                  <Typography variant="caption" color="text.secondary">
                    Aucune technique observée.
                  </Typography>
                ) : (
                  <Stack spacing={0.75}>
                    {data.metrics.mitreTopTechniques.map((t) => (
                      <Stack
                        key={t.attackId}
                        direction="row"
                        spacing={1}
                        sx={{ alignItems: 'center', cursor: 'pointer' }}
                        onClick={() => navigate(`/mitre?selected=${t.attackId}`)}
                      >
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
                    ))}
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
