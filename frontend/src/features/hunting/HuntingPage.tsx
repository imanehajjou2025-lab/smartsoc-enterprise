import { useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditIcon from '@mui/icons-material/Edit';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
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
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import type { Alert as SocAlert } from '../alerts/alertsApi';
import AlertDetailDrawer from '../alerts/AlertDetailDrawer';
import { SeverityChip, StatusChip } from '../alerts/chips';
import {
  deleteHunt,
  executeAdHoc,
  executeHunt,
  flatAndGroup,
  listHuntableFields,
  listHunts,
  updateHunt,
  type HuntCondition,
  type HuntExecutionResult,
  type HuntField,
  type HuntOperator,
  type HuntQuery,
} from './huntingApi';
import SaveHuntDialog from './SaveHuntDialog';

const RESULTS_SIZE = 10;

const SEVERITY_VALUES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const STATUS_VALUES = ['NEW', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'FALSE_POSITIVE'];

const FIELD_LABELS: Record<HuntField, string> = {
  SEVERITY: 'Sévérité',
  STATUS: 'Statut',
  SOURCE: 'Source',
  HOSTNAME: 'Hôte',
  RULE_ID: 'Règle',
  DETECTED_AT: 'Détectée',
  MITRE_TECHNIQUE: 'Technique ATT&CK',
  RAW_PAYLOAD_TEXT: 'Texte de l’événement brut',
};

const OPERATOR_LABELS: Record<HuntOperator, string> = {
  EQUALS: '=',
  CONTAINS: 'contient',
  GREATER_THAN: 'après',
  LESS_THAN: 'avant',
};

function newCondition(): HuntCondition {
  return { kind: 'CONDITION', field: 'SEVERITY', operator: 'EQUALS', value: '' };
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Chasse proactive : requêtes structurées sur les alertes déjà ingérées (ADR-011). */
function HuntingPage() {
  const queryClient = useQueryClient();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canWrite = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';

  const [conditions, setConditions] = useState<HuntCondition[]>([newCondition()]);
  const [loadedHunt, setLoadedHunt] = useState<HuntQuery | null>(null);
  const [savedSearch, setSavedSearch] = useState('');
  const [saveDialogOpen, setSaveDialogOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<HuntQuery | null>(null);
  const [resultsPage, setResultsPage] = useState(0);
  const [lastRunHuntId, setLastRunHuntId] = useState<string | null>(null);
  const [executionResult, setExecutionResult] = useState<HuntExecutionResult | null>(null);
  const [selectedAlert, setSelectedAlert] = useState<SocAlert | null>(null);

  const { data: fields } = useQuery({
    queryKey: ['hunt-fields'],
    queryFn: listHuntableFields,
    staleTime: Infinity,
  });

  const { data: savedHunts, isPending: savedPending } = useQuery({
    queryKey: ['hunts', savedSearch],
    queryFn: () => listHunts(savedSearch, 0, 50),
    placeholderData: keepPreviousData,
  });

  const runMutation = useMutation({
    mutationFn: ({ huntId, page }: { huntId: string | null; page: number }) =>
      huntId
        ? executeHunt(huntId, page, RESULTS_SIZE)
        : executeAdHoc(flatAndGroup(conditions), page, RESULTS_SIZE),
    onSuccess: (result, variables) => {
      setExecutionResult(result);
      setResultsPage(variables.page);
      setLastRunHuntId(variables.huntId);
      if (variables.huntId) {
        void queryClient.invalidateQueries({ queryKey: ['hunts'] });
      }
    },
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      updateHunt(loadedHunt!.id, { name: loadedHunt!.name, criteria: flatAndGroup(conditions) }),
    onSuccess: (updated) => {
      setLoadedHunt(updated);
      void queryClient.invalidateQueries({ queryKey: ['hunts'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteHunt(id),
    onSuccess: () => {
      setDeleteTarget(null);
      if (loadedHunt?.id === deleteTarget?.id) {
        setLoadedHunt(null);
      }
      void queryClient.invalidateQueries({ queryKey: ['hunts'] });
    },
  });

  const updateCondition = (index: number, patch: Partial<HuntCondition>) => {
    setConditions((prev) => prev.map((c, i) => (i === index ? { ...c, ...patch } : c)));
  };

  const changeField = (index: number, field: HuntField) => {
    const allowed = fields?.find((f) => f.field === field)?.allowedOperators ?? ['EQUALS'];
    updateCondition(index, { field, operator: allowed[0], value: '' });
  };

  const removeCondition = (index: number) => {
    setConditions((prev) => (prev.length > 1 ? prev.filter((_, i) => i !== index) : prev));
  };

  const loadHunt = (hunt: HuntQuery) => {
    setLoadedHunt(hunt);
    const flatConditions = hunt.criteria.children.filter(
      (n): n is HuntCondition => n.kind === 'CONDITION',
    );
    setConditions(flatConditions.length > 0 ? flatConditions : [newCondition()]);
    setExecutionResult(null);
  };

  const startNewHunt = () => {
    setLoadedHunt(null);
    setConditions([newCondition()]);
    setExecutionResult(null);
  };

  const run = (huntId: string | null) => runMutation.mutate({ huntId, page: 0 });

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Box>
          <Typography variant="h5" component="h2">
            Threat Hunting
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Requêtes structurées sur les alertes déjà ingérées
          </Typography>
        </Box>
        <Button variant="outlined" startIcon={<AddIcon />} onClick={startNewHunt}>
          Nouvelle chasse
        </Button>
      </Box>

      <Stack direction="row" spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Paper variant="outlined" sx={{ width: 300, flexShrink: 0, p: 2 }}>
          <TextField
            label="Rechercher une chasse"
            size="small"
            fullWidth
            value={savedSearch}
            onChange={(e) => setSavedSearch(e.target.value)}
            sx={{ mb: 1.5 }}
          />
          {savedPending && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
              <CircularProgress size={24} />
            </Box>
          )}
          <List dense disablePadding>
            {savedHunts?.items.map((hunt) => (
              <ListItemButton
                key={hunt.id}
                selected={loadedHunt?.id === hunt.id}
                onClick={() => loadHunt(hunt)}
                sx={{ borderRadius: 1, mb: 0.5 }}
              >
                <ListItemText
                  primary={hunt.name}
                  secondary={
                    hunt.lastExecutedAt
                      ? `Exécutée ${formatDate(hunt.lastExecutedAt)}`
                      : 'Jamais exécutée'
                  }
                  slotProps={{
                    primary: { noWrap: true },
                    secondary: { variant: 'caption' },
                  }}
                />
                <IconButton
                  size="small"
                  onClick={(e) => {
                    e.stopPropagation();
                    run(hunt.id);
                  }}
                  title="Exécuter"
                >
                  <PlayArrowIcon fontSize="small" />
                </IconButton>
                {canWrite && (
                  <IconButton
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation();
                      setDeleteTarget(hunt);
                    }}
                    title="Supprimer"
                  >
                    <DeleteOutlineIcon fontSize="small" />
                  </IconButton>
                )}
              </ListItemButton>
            ))}
            {savedHunts && savedHunts.items.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ px: 1 }}>
                Aucune chasse sauvegardée.
              </Typography>
            )}
          </List>
        </Paper>

        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
            {loadedHunt && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                <EditIcon fontSize="small" color="action" />
                <Typography variant="subtitle2">Modification de « {loadedHunt.name} »</Typography>
              </Box>
            )}
            <Stack spacing={1.5}>
              {conditions.map((condition, index) => {
                const allowedOperators =
                  fields?.find((f) => f.field === condition.field)?.allowedOperators ?? [];
                return (
                  <Stack key={index} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <TextField
                      select
                      size="small"
                      label="Champ"
                      value={condition.field}
                      onChange={(e) => changeField(index, e.target.value as HuntField)}
                      sx={{ minWidth: 180 }}
                    >
                      {Object.entries(FIELD_LABELS).map(([value, label]) => (
                        <MenuItem key={value} value={value}>
                          {label}
                        </MenuItem>
                      ))}
                    </TextField>
                    <TextField
                      select
                      size="small"
                      label="Opérateur"
                      value={condition.operator}
                      onChange={(e) =>
                        updateCondition(index, { operator: e.target.value as HuntOperator })
                      }
                      sx={{ minWidth: 130 }}
                    >
                      {allowedOperators.map((op) => (
                        <MenuItem key={op} value={op}>
                          {OPERATOR_LABELS[op]}
                        </MenuItem>
                      ))}
                    </TextField>
                    {(condition.field === 'SEVERITY' || condition.field === 'STATUS') && (
                      <TextField
                        select
                        size="small"
                        label="Valeur"
                        value={condition.value}
                        onChange={(e) => updateCondition(index, { value: e.target.value })}
                        sx={{ minWidth: 180 }}
                      >
                        {(condition.field === 'SEVERITY' ? SEVERITY_VALUES : STATUS_VALUES).map(
                          (value) => (
                            <MenuItem key={value} value={value}>
                              {value}
                            </MenuItem>
                          ),
                        )}
                      </TextField>
                    )}
                    {condition.field === 'DETECTED_AT' && (
                      <TextField
                        type="datetime-local"
                        size="small"
                        label="Valeur"
                        slotProps={{ inputLabel: { shrink: true } }}
                        onChange={(e) =>
                          updateCondition(index, {
                            value: e.target.value ? new Date(e.target.value).toISOString() : '',
                          })
                        }
                        sx={{ minWidth: 200 }}
                      />
                    )}
                    {!['SEVERITY', 'STATUS', 'DETECTED_AT'].includes(condition.field) && (
                      <TextField
                        size="small"
                        label="Valeur"
                        value={condition.value}
                        onChange={(e) => updateCondition(index, { value: e.target.value })}
                        placeholder={condition.field === 'MITRE_TECHNIQUE' ? 'T1059' : undefined}
                        sx={{ minWidth: 220, flexGrow: 1 }}
                      />
                    )}
                    <IconButton
                      size="small"
                      onClick={() => removeCondition(index)}
                      disabled={conditions.length === 1}
                    >
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                );
              })}
            </Stack>
            <Button
              size="small"
              startIcon={<AddIcon />}
              onClick={() => setConditions((prev) => [...prev, newCondition()])}
              sx={{ mt: 1 }}
            >
              Ajouter une condition
            </Button>

            <Divider sx={{ my: 2 }} />

            {runMutation.isError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {problemDetail(runMutation.error, 'Exécution impossible.')}
              </Alert>
            )}

            <Stack direction="row" spacing={1}>
              <Button
                variant="contained"
                startIcon={<PlayArrowIcon />}
                disabled={runMutation.isPending || conditions.some((c) => !c.value)}
                onClick={() => run(null)}
              >
                Exécuter
              </Button>
              {canWrite && loadedHunt && (
                <Button
                  variant="outlined"
                  disabled={updateMutation.isPending}
                  onClick={() => updateMutation.mutate()}
                >
                  Mettre à jour « {loadedHunt.name} »
                </Button>
              )}
              {canWrite && (
                <Button variant="outlined" onClick={() => setSaveDialogOpen(true)}>
                  Enregistrer sous…
                </Button>
              )}
            </Stack>
          </Paper>

          {executionResult && (
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {executionResult.summary.matchedCount} correspondance
                {executionResult.summary.matchedCount > 1 ? 's' : ''} —{' '}
                {executionResult.summary.tookMillis} ms
                {executionResult.summary.truncated ? ' (résultat tronqué)' : ''}
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Sévérité</TableCell>
                      <TableCell>Titre</TableCell>
                      <TableCell>Source</TableCell>
                      <TableCell>Hôte</TableCell>
                      <TableCell>Statut</TableCell>
                      <TableCell>Détection</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {executionResult.matches.items.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={6}>
                          <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                            Aucune alerte ne correspond à ces critères.
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                    {executionResult.matches.items.map((alert) => (
                      <TableRow
                        key={alert.id}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => setSelectedAlert(alert)}
                      >
                        <TableCell>
                          <SeverityChip severity={alert.severity} />
                        </TableCell>
                        <TableCell
                          title={alert.title}
                          sx={{
                            maxWidth: 280,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {alert.title}
                        </TableCell>
                        <TableCell>{alert.source}</TableCell>
                        <TableCell>{alert.hostname ?? '—'}</TableCell>
                        <TableCell>
                          <StatusChip status={alert.status} />
                        </TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>
                          <Typography variant="caption" color="text.secondary">
                            {formatDate(alert.detectedAt)}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
                <TablePagination
                  component="div"
                  count={executionResult.matches.totalElements}
                  page={resultsPage}
                  onPageChange={(_, newPage) =>
                    runMutation.mutate({ huntId: lastRunHuntId, page: newPage })
                  }
                  rowsPerPage={RESULTS_SIZE}
                  rowsPerPageOptions={[RESULTS_SIZE]}
                  labelRowsPerPage=""
                />
              </TableContainer>
            </Paper>
          )}
        </Box>
      </Stack>

      <SaveHuntDialog
        open={saveDialogOpen}
        onClose={() => setSaveDialogOpen(false)}
        criteria={flatAndGroup(conditions)}
        onSaved={(hunt) => {
          setLoadedHunt(hunt);
          void queryClient.invalidateQueries({ queryKey: ['hunts'] });
        }}
      />

      <Dialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>Supprimer la chasse ?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            « {deleteTarget?.name} » sera supprimée définitivement — ce n'est pas une pièce
            d'évidence SOC, juste un gabarit de recherche.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Annuler</Button>
          <Button
            color="error"
            variant="contained"
            disabled={deleteMutation.isPending}
            onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
          >
            Supprimer
          </Button>
        </DialogActions>
      </Dialog>

      <AlertDetailDrawer
        alert={selectedAlert}
        onClose={() => setSelectedAlert(null)}
        onUpdated={setSelectedAlert}
      />
    </Box>
  );
}

export default HuntingPage;
