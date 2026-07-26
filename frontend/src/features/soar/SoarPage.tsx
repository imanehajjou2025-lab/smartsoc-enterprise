import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import AddIcon from '@mui/icons-material/Add';
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import EditIcon from '@mui/icons-material/Edit';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
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
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import PlaybookDialog from './PlaybookDialog';
import PlaybookExecutionDrawer from './PlaybookExecutionDrawer';
import { archivePlaybook, listPlaybooks, type Playbook } from './soarApi';

const PAGE_SIZE = 25;

/** Playbooks — procédures de réponse documentées et suivi guidé (ADR-012). Pas d'automatisation : voir Shuffle. */
function SoarPage() {
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
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Box>
          <Typography variant="h5" component="h2">
            Playbooks SOAR
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Procédures de réponse documentées, suivies pas à pas contre un incident
          </Typography>
        </Box>
        {canWrite && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
            Déclarer un playbook
          </Button>
        )}
      </Box>

      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
        <TextField
          label="Rechercher"
          size="small"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          sx={{ minWidth: 220 }}
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
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Nom</TableCell>
                <TableCell>Version</TableCell>
                <TableCell>Étapes</TableCell>
                <TableCell>Statut</TableCell>
                {canWrite && <TableCell align="right">Actions</TableCell>}
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
                <TableRow key={playbook.id} hover>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {playbook.name}
                    </Typography>
                    {playbook.description && (
                      <Typography variant="caption" color="text.secondary">
                        {playbook.description}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>v{playbook.version}</TableCell>
                  <TableCell>{playbook.steps.length}</TableCell>
                  <TableCell>
                    {playbook.archived ? (
                      <Chip label="Archivé" size="small" variant="outlined" />
                    ) : (
                      <Chip label="Actif" size="small" color="success" variant="outlined" />
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
