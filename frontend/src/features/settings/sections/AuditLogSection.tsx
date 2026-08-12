import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
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
import { problemDetail } from '../../../shared/api/client';
import { AuditActionChip } from '../settingsChips';
import { listAuditLogs, type AuditAction, type AuditLogEntry } from '../settingsApi';

const ACTIONS: AuditAction[] = [
  'LOGIN_SUCCEEDED',
  'LOGIN_FAILED',
  'USER_CREATED',
  'USER_UPDATED',
  'USER_ROLE_CHANGED',
  'USER_ENABLED',
  'USER_DISABLED',
  'USER_DELETED',
  'BACKUP_EXPORTED',
  'WAZUH_AGENT_RESTART_REQUESTED',
  'WAZUH_AGENT_FIREWALL_DROP_REQUESTED',
  'SHUFFLE_WORKFLOW_TRIGGER_REQUESTED',
  'ALERT_ASSIGNED',
  'ALERT_UNASSIGNED',
];

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
}

function describeTarget(entry: AuditLogEntry): string {
  if (!entry.targetType) return '—';
  return entry.targetId ? `${entry.targetType} · ${entry.targetId.slice(0, 8)}` : entry.targetType;
}

function AuditLogSection() {
  const [action, setAction] = useState<AuditAction | ''>('');
  const [actorUsername, setActorUsername] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['settings-audit-logs', action, actorUsername, page, size],
    queryFn: () => listAuditLogs({ action, actorUsername, page, size }),
    placeholderData: keepPreviousData,
  });

  return (
    <Box>
      <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
        Journal d'audit
      </Typography>

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', gap: 2 }}>
        <TextField
          select
          label="Action"
          size="small"
          value={action}
          onChange={(e) => {
            setAction(e.target.value as AuditAction | '');
            setPage(0);
          }}
          sx={{ minWidth: 220 }}
        >
          <MenuItem value="">Toutes</MenuItem>
          {ACTIONS.map((value) => (
            <MenuItem key={value} value={value}>
              {value}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          label="Utilisateur"
          size="small"
          value={actorUsername}
          onChange={(e) => {
            setActorUsername(e.target.value);
            setPage(0);
          }}
          placeholder="admin"
          sx={{ minWidth: 200 }}
        />
      </Stack>

      {isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}
      {isError && (
        <Alert severity="error">{problemDetail(error, "Journal d'audit indisponible.")}</Alert>
      )}

      {data && (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 3 }}>
          <Table size="small" aria-label="Journal d'audit">
            <TableHead>
              <TableRow>
                <TableCell>Horodatage</TableCell>
                <TableCell>Action</TableCell>
                <TableCell>Acteur</TableCell>
                <TableCell>Cible</TableCell>
                <TableCell>Détails</TableCell>
                <TableCell>Adresse IP</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6}>
                    <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                      Aucune entrée pour ces filtres.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {data.items.map((entry) => (
                <TableRow key={entry.id} hover>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {formatDate(entry.occurredAt)}
                  </TableCell>
                  <TableCell>
                    <AuditActionChip action={entry.action} />
                  </TableCell>
                  <TableCell>{entry.actorUsername}</TableCell>
                  <TableCell>{describeTarget(entry)}</TableCell>
                  <TableCell sx={{ maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {entry.details ?? '—'}
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace' }}>{entry.ipAddress ?? '—'}</TableCell>
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
              setSize(Number(e.target.value));
              setPage(0);
            }}
            rowsPerPageOptions={[10, 25, 50, 100]}
            labelRowsPerPage="Lignes par page"
            labelDisplayedRows={({ from, to, count }) => `${from}–${to} sur ${count}`}
          />
        </TableContainer>
      )}
    </Box>
  );
}

export default AuditLogSection;
