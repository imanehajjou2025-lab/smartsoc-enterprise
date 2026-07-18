import { useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
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
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import type { AlertSeverity } from '../alerts/alertsApi';
import { SeverityChip } from '../alerts/chips';
import { CaseStatusChip, CASE_STATUS_LABELS } from './caseChips';
import CaseDetailDrawer from './CaseDetailDrawer';
import CreateCaseDialog from './CreateCaseDialog';
import { listCases, type CaseStatus } from './investigationsApi';

const PRIORITIES: AlertSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const STATUSES: CaseStatus[] = ['OPEN', 'IN_PROGRESS', 'CLOSED'];

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Module Investigations : les cas d'enquête au-dessus des incidents. */
function InvestigationsPage() {
  const [status, setStatus] = useState<CaseStatus | ''>('');
  const [priority, setPriority] = useState<AlertSeverity | ''>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['investigations', { status, priority, page, size }],
    queryFn: () => listCases({ status, priority, page, size }),
    placeholderData: keepPreviousData,
  });

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h5" component="h2">
          Investigations
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
          Nouveau cas
        </Button>
      </Box>

      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          label="Statut"
          size="small"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as CaseStatus | '');
            setPage(0);
          }}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">Tous</MenuItem>
          {STATUSES.map((value) => (
            <MenuItem key={value} value={value}>
              {CASE_STATUS_LABELS[value]}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Priorité"
          size="small"
          value={priority}
          onChange={(e) => {
            setPriority(e.target.value as AlertSeverity | '');
            setPage(0);
          }}
          sx={{ minWidth: 150 }}
        >
          <MenuItem value="">Toutes</MenuItem>
          {PRIORITIES.map((value) => (
            <MenuItem key={value} value={value}>
              {value}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}
      {isError && (
        <Alert severity="error">
          {problemDetail(error, 'Impossible de charger les cas d’investigation.')}
        </Alert>
      )}

      {data && (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label="Liste des cas d'investigation">
            <TableHead>
              <TableRow>
                <TableCell>Référence</TableCell>
                <TableCell>Priorité</TableCell>
                <TableCell>Titre</TableCell>
                <TableCell>Statut</TableCell>
                <TableCell>Assigné</TableCell>
                <TableCell>Ouvert le</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6}>
                    <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                      Aucun cas — créez-en un ou ouvrez un cas depuis un incident.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {data.items.map((investigation) => (
                <TableRow
                  key={investigation.id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => setSelectedId(investigation.id)}
                >
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{investigation.reference}</TableCell>
                  <TableCell>
                    <SeverityChip severity={investigation.priority} />
                  </TableCell>
                  <TableCell sx={{ maxWidth: 360, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {investigation.title}
                  </TableCell>
                  <TableCell>
                    <CaseStatusChip status={investigation.status} />
                  </TableCell>
                  <TableCell>{investigation.assigneeUsername ?? '—'}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {formatDate(investigation.openedAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <TablePagination
            component="div"
            count={data.totalElements}
            page={page}
            onPageChange={(_, newPage) => setPage(newPage)}
            rowsPerPage={size}
            onRowsPerPageChange={(e) => {
              setSize(parseInt(e.target.value, 10));
              setPage(0);
            }}
            rowsPerPageOptions={[10, 25, 50, 100]}
            labelRowsPerPage="Lignes par page"
          />
        </TableContainer>
      )}

      <CreateCaseDialog open={createOpen} onClose={() => setCreateOpen(false)} />
      <CaseDetailDrawer caseId={selectedId} onClose={() => setSelectedId(null)} />
    </Box>
  );
}

export default InvestigationsPage;
