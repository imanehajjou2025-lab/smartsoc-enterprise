import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import BugReportIcon from '@mui/icons-material/BugReport';
import GpsFixedIcon from '@mui/icons-material/GpsFixed';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import ShieldIcon from '@mui/icons-material/Shield';
import ViewWeekIcon from '@mui/icons-material/ViewWeek';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { EChartsOption } from 'echarts';
import { problemDetail } from '../../shared/api/client';
import { severityColors } from '../../app/theme';
import EChart from '../../shared/components/EChart';
import { coverageColor } from './mitreChips';
import { getCoverage, listTactics, listTechniques, type MitreTechnique } from './mitreApi';
import TechniqueDetailDrawer from './TechniqueDetailDrawer';

const MATRIX_PAGE_SIZE = 200;
const TACTIC_COUNT = 14;
const NAVIGATOR_URL = 'https://mitre-attack.github.io/attack-navigator/';
const MUTED = 'rgba(139,148,158,0.35)';

/**
 * Récupère TOUTES les techniques : le catalogue peut dépasser une page
 * (taille bornée à 200 côté serveur), et la matrice doit être complète.
 */
async function fetchAllTechniques(
  search: string,
  includeDeprecated: boolean,
): Promise<MitreTechnique[]> {
  const all: MitreTechnique[] = [];
  let page = 0;
  for (;;) {
    const res = await listTechniques({ search, includeDeprecated, page, size: MATRIX_PAGE_SIZE });
    all.push(...res.items);
    if (res.items.length === 0 || all.length >= res.totalElements) break;
    page += 1;
  }
  return all;
}

/** Pastille : gris si non observé, sinon orange → rouge selon l'intensité. */
function dotColor(count: number, max: number): string {
  if (count <= 0) return MUTED;
  const intensity = max ? count / max : 1;
  if (intensity >= 0.66) return severityColors.critical;
  if (intensity >= 0.33) return severityColors.high;
  return severityColors.medium;
}

/** Carte KPI iconée et accentuée, style console. */
function KpiCard({
  label,
  value,
  hint,
  color,
  icon,
}: {
  label: string;
  value: string;
  hint: string;
  color: string;
  icon: React.ReactNode;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2, display: 'flex', gap: 2, alignItems: 'center', flex: 1 }}>
      <Box
        sx={{
          width: 46,
          height: 46,
          borderRadius: '12px',
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color,
          backgroundColor: `${color}22`,
        }}
      >
        {icon}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ textTransform: 'uppercase', letterSpacing: 0.4 }}
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

function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: color, flexShrink: 0 }} />
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
    </Stack>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 260 }}>
      <Typography variant="subtitle2" sx={{ mb: 1.5 }}>
        {title}
      </Typography>
      {children}
    </Paper>
  );
}

/**
 * La matrice MITRE ATT&CK — tableau de bord de couverture. Cartes KPI,
 * heatmap des 14 tactiques (techniques colorées par le nombre d'alertes qui
 * les citent), techniques les plus vues et anneau de couverture. Tout vient
 * du catalogue et de la corrélation calculée à la lecture côté serveur ; les
 * sous-techniques se consultent dans le tiroir d'une technique.
 */
function MitrePage() {
  const [search, setSearch] = useState('');
  const [includeDeprecated, setIncludeDeprecated] = useState(false);
  const [onlyObserved, setOnlyObserved] = useState(false);
  // Lien profond /mitre?selected={attackId} : le tiroir s'ouvre dès le
  // premier rendu (clic sur une case, ou URL partagée depuis une alerte).
  const [searchParams, setSearchParams] = useSearchParams();
  const selected = searchParams.get('selected');
  const closeDrawer = () => setSearchParams({}, { replace: true });

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['mitre-matrix', { search, includeDeprecated }],
    queryFn: async () => {
      const [tactics, techniques, coverage] = await Promise.all([
        listTactics(),
        fetchAllTechniques(search, includeDeprecated),
        getCoverage(),
      ]);
      return { tactics, techniques, coverage };
    },
    placeholderData: keepPreviousData,
  });

  const tactics = data?.tactics ?? [];
  const techniques = data?.techniques ?? [];
  const coverage = data?.coverage ?? [];
  const catalogById = new Map(techniques.map((t) => [t.attackId, t]));
  const coverageByTechnique = new Map(coverage.map((c) => [c.attackId, c.alertCount]));
  const maxCount = Math.max(0, ...coverage.map((c) => c.alertCount));

  // KPI, sur les seules techniques du catalogue réellement observées.
  const observedCatalog = coverage.filter((c) => catalogById.has(c.attackId));
  const observed = observedCatalog.length;
  const totalAlerts = coverage.reduce((sum, c) => sum + c.alertCount, 0);
  const coveragePct = techniques.length ? Math.round((observed / techniques.length) * 100) : 0;
  const touchedTactics = new Set<string>();
  observedCatalog.forEach((c) =>
    catalogById.get(c.attackId)?.tactics.forEach((t) => touchedTactics.add(t)),
  );
  const topTechniques = [...observedCatalog]
    .sort((a, b) => b.alertCount - a.alertCount)
    .slice(0, 5)
    .map((c) => ({ ...c, name: catalogById.get(c.attackId)?.name ?? c.attackId }));

  // Alertes par tactique : sert la couleur d'accent des en-têtes de colonne.
  const tacticAlerts = new Map(
    tactics.map((ta) => [
      ta.shortName,
      techniques
        .filter((t) => !t.subTechnique && t.tactics.includes(ta.shortName))
        .reduce((s, t) => s + (coverageByTechnique.get(t.attackId) ?? 0), 0),
    ]),
  );
  const maxTacticAlerts = Math.max(0, ...tacticAlerts.values());

  const coverageOption = useMemo<EChartsOption>(
    () => ({
      tooltip: { trigger: 'item' },
      title: {
        text: `${coveragePct}%`,
        subtext: `${observed}/${techniques.length}`,
        left: 'center',
        top: 'center',
        textAlign: 'center',
        textStyle: { color: '#e6edf3', fontSize: 24, fontWeight: 700 },
        subtextStyle: { color: '#8b949e', fontSize: 12 },
      },
      series: [
        {
          type: 'pie',
          radius: ['58%', '82%'],
          label: { show: false },
          data: [
            { name: 'Observées', value: observed, itemStyle: { color: severityColors.low } },
            {
              name: 'Non observées',
              value: Math.max(0, techniques.length - observed),
              itemStyle: { color: 'rgba(139,148,158,0.35)' },
            },
          ],
        },
      ],
    }),
    [observed, techniques.length, coveragePct],
  );

  return (
    <Box>
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          gap: 2,
          mb: 2,
          flexWrap: 'wrap',
        }}
      >
        <Box>
          <Typography variant="h5" component="h2">
            MITRE ATT&CK
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Explorer les techniques, tactiques et procédures adverses
          </Typography>
        </Box>
        <Button
          component="a"
          href={NAVIGATOR_URL}
          target="_blank"
          rel="noopener noreferrer"
          variant="outlined"
          startIcon={<OpenInNewIcon />}
        >
          Vue ATT&CK Navigator
        </Button>
      </Box>

      {isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}
      {isError && (
        <Alert severity="error">
          {problemDetail(error, 'Impossible de charger la matrice ATT&CK.')}
        </Alert>
      )}

      {data && (
        <>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' },
              gap: 2,
              mb: 3,
            }}
          >
            <KpiCard
              label="Techniques observées"
              value={String(observed)}
              hint={`sur ${techniques.length} au catalogue`}
              color={severityColors.critical}
              icon={<GpsFixedIcon />}
            />
            <KpiCard
              label="Tactiques touchées"
              value={`${touchedTactics.size} / ${TACTIC_COUNT}`}
              hint="colonnes avec activité"
              color={severityColors.medium}
              icon={<ViewWeekIcon />}
            />
            <KpiCard
              label="Alertes corrélées"
              value={String(totalAlerts)}
              hint="citant une technique"
              color={severityColors.info}
              icon={<BugReportIcon />}
            />
            <KpiCard
              label="Couverture"
              value={`${coveragePct}%`}
              hint="du catalogue observé"
              color={severityColors.low}
              icon={<ShieldIcon />}
            />
          </Box>

          <Stack
            direction="row"
            spacing={2}
            useFlexGap
            sx={{ mb: 2, flexWrap: 'wrap', alignItems: 'center' }}
          >
            <TextField
              label="Recherche"
              size="small"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="identifiant ou nom"
              sx={{ minWidth: 220 }}
            />
            <TextField
              select
              label="État"
              size="small"
              value={onlyObserved ? 'observed' : 'all'}
              onChange={(e) => setOnlyObserved(e.target.value === 'observed')}
              sx={{ minWidth: 150 }}
            >
              <MenuItem value="all">Toutes</MenuItem>
              <MenuItem value="observed">Observées</MenuItem>
            </TextField>
            <FormControlLabel
              control={
                <Switch
                  checked={includeDeprecated}
                  onChange={(e) => setIncludeDeprecated(e.target.checked)}
                />
              }
              label="Inclure les dépréciées"
            />
            <Box sx={{ flexGrow: 1 }} />
            <Typography variant="caption" color="text.secondary">
              Couleur = nombre d&apos;alertes citant la technique
            </Typography>
          </Stack>

          <Box sx={{ overflowX: 'auto', pb: 1 }}>
            <Box
              sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', minWidth: 'min-content' }}
            >
              {tactics.map((tactic) => {
                const columnTechniques = techniques
                  .filter((t) => !t.subTechnique && t.tactics.includes(tactic.shortName))
                  .filter((t) => !onlyObserved || (coverageByTechnique.get(t.attackId) ?? 0) > 0)
                  .sort((a, b) => a.attackId.localeCompare(b.attackId));
                const headerColor = dotColor(
                  tacticAlerts.get(tactic.shortName) ?? 0,
                  maxTacticAlerts,
                );
                return (
                  <Box key={tactic.attackId} sx={{ width: 190, flexShrink: 0 }}>
                    <Box
                      sx={{
                        px: 1,
                        py: 0.75,
                        mb: 0.5,
                        borderTop: '3px solid',
                        borderColor: headerColor,
                        borderRadius: '4px 4px 0 0',
                        backgroundColor: 'rgba(139,148,158,0.06)',
                      }}
                    >
                      <Typography variant="subtitle2" sx={{ lineHeight: 1.2 }}>
                        {tactic.name}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {columnTechniques.length} technique{columnTechniques.length > 1 ? 's' : ''}
                      </Typography>
                    </Box>
                    <Stack spacing={0.5}>
                      {columnTechniques.map((technique) => {
                        const count = coverageByTechnique.get(technique.attackId) ?? 0;
                        return (
                          <Paper
                            key={technique.attackId}
                            variant="outlined"
                            onClick={() => setSearchParams({ selected: technique.attackId })}
                            title={technique.name}
                            sx={{
                              p: 0.75,
                              cursor: 'pointer',
                              backgroundColor: coverageColor(count, maxCount),
                              opacity: technique.deprecated ? 0.55 : 1,
                              '&:hover': { borderColor: 'primary.main' },
                            }}
                          >
                            <Box
                              sx={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                gap: 0.5,
                              }}
                            >
                              <Typography
                                variant="caption"
                                sx={{ fontFamily: 'monospace', fontWeight: 600 }}
                              >
                                {technique.attackId}
                              </Typography>
                              <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                                {count > 0 && (
                                  <Typography variant="caption" sx={{ fontWeight: 700 }}>
                                    {count}
                                  </Typography>
                                )}
                                <Box
                                  sx={{
                                    width: 9,
                                    height: 9,
                                    borderRadius: '50%',
                                    bgcolor: dotColor(count, maxCount),
                                  }}
                                />
                              </Stack>
                            </Box>
                            <Typography
                              variant="caption"
                              sx={{
                                display: 'block',
                                lineHeight: 1.15,
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap',
                              }}
                            >
                              {technique.name}
                            </Typography>
                          </Paper>
                        );
                      })}
                    </Stack>
                  </Box>
                );
              })}
            </Box>
          </Box>

          <Stack direction="row" spacing={2} useFlexGap sx={{ mt: 3, flexWrap: 'wrap' }}>
            <Panel title="Techniques les plus citées">
              {topTechniques.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  Aucune alerte ne cite de technique cataloguée pour l&apos;instant.
                </Typography>
              )}
              <Stack spacing={1}>
                {topTechniques.map((t) => (
                  <Box
                    key={t.attackId}
                    onClick={() => setSearchParams({ selected: t.attackId })}
                    sx={{ cursor: 'pointer' }}
                  >
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                      <Typography variant="caption" noWrap>
                        <b style={{ fontFamily: 'monospace' }}>{t.attackId}</b> {t.name}
                      </Typography>
                      <Typography variant="caption" sx={{ fontWeight: 700 }}>
                        {t.alertCount}
                      </Typography>
                    </Box>
                    <Box
                      sx={{
                        height: 6,
                        borderRadius: 3,
                        mt: 0.25,
                        width: `${maxCount ? (t.alertCount / maxCount) * 100 : 0}%`,
                        minWidth: 4,
                        backgroundColor: dotColor(t.alertCount, maxCount),
                      }}
                    />
                  </Box>
                ))}
              </Stack>
            </Panel>

            <Panel title="Couverture MITRE">
              <EChart option={coverageOption} height={180} />
              <Stack spacing={0.5} sx={{ mt: 1 }}>
                <LegendItem color={severityColors.low} label={`Observées (${observed})`} />
                <LegendItem
                  color="rgba(139,148,158,0.4)"
                  label={`Non observées (${Math.max(0, techniques.length - observed)})`}
                />
              </Stack>
            </Panel>

            <Panel title="Légende">
              <Stack spacing={1}>
                <LegendItem
                  color={severityColors.critical}
                  label="Forte activité (nombreuses alertes)"
                />
                <LegendItem color={severityColors.high} label="Activité moyenne" />
                <LegendItem color={severityColors.medium} label="Faible activité" />
                <LegendItem color={MUTED} label="Non observé — aucune alerte" />
                <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5 }}>
                  L&apos;intensité suit le nombre d&apos;alertes, relatif au maximum observé.
                </Typography>
              </Stack>
            </Panel>
          </Stack>
        </>
      )}

      <TechniqueDetailDrawer attackId={selected} onClose={closeDrawer} />
    </Box>
  );
}

export default MitrePage;
