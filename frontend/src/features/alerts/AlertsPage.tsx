import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
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
import { useSearchParams } from 'react-router-dom';
import { problemDetail } from '../../shared/api/client';
import AlertDetailDrawer from './AlertDetailDrawer';
import {
  listAlerts,
  type Alert as SocAlert,
  type AlertSeverity,
  type AlertStatus,
} from './alertsApi';
import { SeverityChip, StatusChip, STATUS_LABELS } from './chips';
import { useAlertsRealtime } from './useAlertsRealtime';

const SEVERITIES: AlertSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const STATUSES: AlertStatus[] = [
  'NEW',
  'ACKNOWLEDGED',
  'IN_PROGRESS',
  'RESOLVED',
  'FALSE_POSITIVE',
];

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/** File de triage des alertes — le cœur opérationnel de la console. */
function AlertsPage() {
  // Lien profond /alerts?status=NEW (notifications du header) : sert
  // uniquement de valeur initiale, l'analyste reste libre de changer le filtre.
  const [searchParams] = useSearchParams();
  const initialStatus = searchParams.get('status');
  const [status, setStatus] = useState<AlertStatus | ''>(
    STATUSES.includes(initialStatus as AlertStatus) ? (initialStatus as AlertStatus) : '',
  );
  const [severity, setSeverity] = useState<AlertSeverity | ''>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [selected, setSelected] = useState<SocAlert | null>(null);

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['alerts', { status, severity, page, size }],
    queryFn: () => listAlerts({ status, severity, page, size }),
    placeholderData: keepPreviousData,
  });
  const { connected } = useAlertsRealtime();

  return (
    <Box>
      <Stack direction="row" spacing={1.5} sx={{ mb: 2, alignItems: 'center' }}>
        <Typography variant="h5" component="h2">
          Alertes
        </Typography>
        <Chip
          size="small"
          label={connected ? 'Temps réel' : 'Hors ligne'}
          color={connected ? 'success' : 'default'}
          variant="outlined"
        />
      </Stack>

      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          label="Statut"
          size="small"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as AlertStatus | '');
            setPage(0);
          }}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">Tous</MenuItem>
          {STATUSES.map((value) => (
            <MenuItem key={value} value={value}>
              {STATUS_LABELS[value]}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Sévérité"
          size="small"
          value={severity}
          onChange={(e) => {
            setSeverity(e.target.value as AlertSeverity | '');
            setPage(0);
          }}
          sx={{ minWidth: 150 }}
        >
          <MenuItem value="">Toutes</MenuItem>
          {SEVERITIES.map((value) => (
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
        <Alert severity="error">{problemDetail(error, 'Impossible de charger les alertes.')}</Alert>
      )}

      {data && (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label="File des alertes">
            <TableHead>
              <TableRow>
                <TableCell>Détection</TableCell>
                <TableCell>Sévérité</TableCell>
                <TableCell>Titre</TableCell>
                <TableCell>Source</TableCell>
                <TableCell>Actif</TableCell>
                <TableCell>Statut</TableCell>
                <TableCell align="right">Score IA</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                      Aucune alerte — en attente d'ingestion depuis les outils SOC (ou lancez
                      scripts/simulate-alerts).
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {data.items.map((alert) => (
                <TableRow
                  key={alert.id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => setSelected(alert)}
                >
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {formatDate(alert.detectedAt)}
                  </TableCell>
                  <TableCell>
                    <SeverityChip severity={alert.severity} />
                  </TableCell>
                  <TableCell sx={{ maxWidth: 420, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {alert.title}
                  </TableCell>
                  <TableCell>{alert.source}</TableCell>
                  <TableCell>{alert.hostname ?? '—'}</TableCell>
                  <TableCell>
                    <StatusChip status={alert.status} />
                  </TableCell>
                  <TableCell align="right">
                    {alert.aiScore == null ? '—' : `${(alert.aiScore * 100).toFixed(0)} %`}
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

      <AlertDetailDrawer
        alert={selected}
        onClose={() => setSelected(null)}
        onUpdated={setSelected}
      />
    </Box>
  );
}

export default AlertsPage;
