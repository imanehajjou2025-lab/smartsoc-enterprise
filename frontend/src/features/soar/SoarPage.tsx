import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import AddIcon from '@mui/icons-material/Add';
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import EditIcon from '@mui/icons-material/Edit';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { alpha, useTheme } from '@mui/material/styles';
import CheckCircleOutlineOutlinedIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import PlayCircleOutlineOutlinedIcon from '@mui/icons-material/PlayCircleOutlineOutlined';
import SearchIcon from '@mui/icons-material/Search';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { severityColors } from '../../app/theme';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import KpiTile from '../../shared/components/KpiTile';
import PageHeaderBanner from '../../shared/components/PageHeaderBanner';
import PlaybookDialog from './PlaybookDialog';
import PlaybookExecutionDrawer from './PlaybookExecutionDrawer';
import { archivePlaybook, listPlaybooks, type Playbook } from './soarApi';

const PAGE_SIZE = 25;

/** Nombre de playbooks pour un filtre donné — dérivé d'une pagination réelle (size=1), pas d'endpoint dédié. */
function usePlaybookCount(includeArchived: boolean) {
  return useQuery({
    queryKey: ['playbooks-count', includeArchived],
    queryFn: () => listPlaybooks('', includeArchived, 0, 1),
    select: (d) => d.totalElements,
    staleTime: 30_000,
  });
}

/** Playbooks — procédures de réponse documentées et suivi guidé (ADR-012). Pas d'automatisation : voir Shuffle. */
function SoarPage() {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canWrite = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';

  const [search, setSearch] = useState('');
  const [includeArchived, setIncludeArchived] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingPlaybook, setEditingPlaybook] = useState<Playbook | null>(null);

  // Lien profond /soar?execution={id} : le tiroir d'exécution s'ouvre dès
  // le premier rendu (depuis « Exécuter un playbook » sur un incident).
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedExecution = searchParams.get('execution');
  const closeExecutionDrawer = () => setSearchParams({}, { replace: true });

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['playbooks', search, includeArchived],
    queryFn: () => listPlaybooks(search, includeArchived, 0, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });

  const totalCount = usePlaybookCount(true);
  const activeCount = usePlaybookCount(false);
  const archivedCount =
    totalCount.data != null && activeCount.data != null ? totalCount.data - activeCount.data : null;

  const archiveMutation = useMutation({
    mutationFn: (id: string) => archivePlaybook(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['playbooks'] }),
  });

  const openCreate = () => {
    setEditingPlaybook(null);
    setDialogOpen(true);
  };
  const openEdit = (playbook: Playbook) => {
    setEditingPlaybook(playbook);
    setDialogOpen(true);
  };

  return (
    <Box>
      <PageHeaderBanner
        icon={<PlayCircleOutlineOutlinedIcon sx={{ color: theme.palette.primary.main, fontSize: 28 }} />}
        title="Playbooks SOAR"
        subtitle="Procédures de réponse documentées, suivies pas à pas contre un incident."
        action={
          canWrite
            ? { label: 'Déclarer un playbook', icon: <AddIcon />, onClick: openCreate }
            : undefined
        }
      />

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(3, 1fr)' },
          gap: 2,
          mb: 3,
        }}
      >
        <KpiTile
          label="Playbooks"
          value={totalCount.data == null ? '…' : String(totalCount.data)}
          hint="au total"
          color={severityColors.info}
          icon={<PlayCircleOutlineOutlinedIcon />}
        />
        <KpiTile
          label="Playbooks actifs"
          value={activeCount.data == null ? '…' : String(activeCount.data)}
          hint="disponibles à l'exécution"
          color={severityColors.low}
          icon={<CheckCircleOutlineOutlinedIcon />}
        />
        <KpiTile
          label="Archivés"
          value={archivedCount == null ? '…' : String(archivedCount)}
          hint="retirés du catalogue actif"
          color={severityColors.medium}
          icon={<ArchiveOutlinedIcon />}
          onClick={() => setIncludeArchived(true)}
        />
      </Box>

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
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap' }} useFlexGap>
          <TextField
            label="Rechercher"
            size="small"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="nom ou description…"
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
            sx={{ minWidth: 240, '& .MuiOutlinedInput-root': { borderRadius: 2.5, bgcolor: 'background.paper' } }}
          />
          <FormControlLabel
            control={
              <Switch
                checked={includeArchived}
                onChange={(e) => setIncludeArchived(e.target.checked)}
              />
            }
            label="Inclure les archivés"
          />
        </Stack>
      </Paper>

      {isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}
      {isError && (
        <Alert severity="error">
          {problemDetail(error, 'Impossible de charger les playbooks.')}
        </Alert>
      )}

      {data && (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 3 }}>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ '& th': { bgcolor: 'action.hover' } }}>
                <TableCell sx={{ fontWeight: 700 }}>Nom</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Version</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Étapes</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Statut</TableCell>
                {canWrite && (
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    Actions
                  </TableCell>
                )}
              </TableRow>
            </TableHead>
            <TableBody>
              {data.items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5}>
                    <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                      Aucun playbook — déclarez la première procédure de réponse.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {data.items.map((playbook) => (
                <TableRow
                  key={playbook.id}
                  hover
                  sx={{
                    borderLeft: '3px solid',
                    borderLeftColor: alpha(
                      playbook.archived ? theme.palette.text.secondary : severityColors.low,
                      0.5,
                    ),
                  }}
                >
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                      {playbook.name}
                    </Typography>
                    {playbook.description && (
                      <Typography variant="caption" color="text.secondary">
                        {playbook.description}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip label={`v${playbook.version}`} size="small" variant="outlined" />
                  </TableCell>
                  <TableCell>{playbook.steps.length}</TableCell>
                  <TableCell>
                    {playbook.archived ? (
                      <Chip label="Archivé" size="small" variant="outlined" />
                    ) : (
                      <Chip
                        label="Actif"
                        size="small"
                        sx={{
                          bgcolor: alpha(severityColors.low, 0.14),
                          color: severityColors.low,
                          border: '1px solid',
                          borderColor: alpha(severityColors.low, 0.55),
                          fontWeight: 700,
                        }}
                      />
                    )}
                  </TableCell>
                  {canWrite && (
                    <TableCell align="right">
                      <IconButton size="small" onClick={() => openEdit(playbook)} title="Modifier">
                        <EditIcon fontSize="small" />
                      </IconButton>
                      {!playbook.archived && (
                        <IconButton
                          size="small"
                          onClick={() => archiveMutation.mutate(playbook.id)}
                          disabled={archiveMutation.isPending}
                          title="Archiver"
                        >
                          <ArchiveOutlinedIcon fontSize="small" />
                        </IconButton>
                      )}
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <PlaybookDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        playbook={editingPlaybook}
      />
      <PlaybookExecutionDrawer executionId={selectedExecution} onClose={closeExecutionDrawer} />
    </Box>
  );
}

export default SoarPage;
