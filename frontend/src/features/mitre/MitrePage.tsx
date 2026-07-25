import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import FormControlLabel from '@mui/material/FormControlLabel';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import { coverageColor } from './mitreChips';
import { getCoverage, listTactics, listTechniques, type MitreTechnique } from './mitreApi';

const MATRIX_PAGE_SIZE = 200;

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

/**
 * La matrice MITRE ATT&CK — heatmap de couverture. Les 14 tactiques en
 * colonnes, les techniques colorées par le nombre d'alertes qui les citent
 * (jointure `/techniques` × `/coverage`, calculée à la lecture côté
 * serveur). Les sous-techniques n'encombrent pas la matrice : elles se
 * consultent dans le tiroir d'une technique.
 */
function MitrePage() {
  const [search, setSearch] = useState('');
  const [includeDeprecated, setIncludeDeprecated] = useState(false);
  const [, setSearchParams] = useSearchParams();

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

  const coverageByTechnique = new Map(
    (data?.coverage ?? []).map((c) => [c.attackId, c.alertCount]),
  );
  const maxCount = Math.max(0, ...(data?.coverage ?? []).map((c) => c.alertCount));

  return (
    <Box>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          mb: 2,
          gap: 2,
          flexWrap: 'wrap',
        }}
      >
        <Typography variant="h5" component="h2">
          MITRE ATT&CK
        </Typography>
        <Typography variant="caption" color="text.secondary">
          Couleur = nombre d&apos;alertes citant la technique
        </Typography>
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
        <FormControlLabel
          control={
            <Switch
              checked={includeDeprecated}
              onChange={(e) => setIncludeDeprecated(e.target.checked)}
            />
          }
          label="Inclure les dépréciées"
        />
      </Stack>

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
        <Box sx={{ overflowX: 'auto', pb: 1 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', minWidth: 'min-content' }}>
            {data.tactics.map((tactic) => {
              const techniques = data.techniques
                .filter((t) => !t.subTechnique && t.tactics.includes(tactic.shortName))
                .sort((a, b) => a.attackId.localeCompare(b.attackId));
              return (
                <Box key={tactic.attackId} sx={{ width: 190, flexShrink: 0 }}>
                  <Box sx={{ px: 1, py: 0.75, mb: 0.5 }}>
                    <Typography variant="subtitle2" sx={{ lineHeight: 1.2 }}>
                      {tactic.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {techniques.length} technique{techniques.length > 1 ? 's' : ''}
                    </Typography>
                  </Box>
                  <Stack spacing={0.5}>
                    {techniques.map((technique) => {
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
                            {count > 0 && (
                              <Typography variant="caption" sx={{ fontWeight: 700 }}>
                                {count}
                              </Typography>
                            )}
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
      )}
    </Box>
  );
}

export default MitrePage;
