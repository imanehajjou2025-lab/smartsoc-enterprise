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
import { problemDetail } from '../../shared/api/client';
import {
  ConfidenceBar,
  IocStatusChip,
  IocTypeChip,
  TlpChip,
  IOC_STATUS_LABELS,
  IOC_TYPE_LABELS,
} from './iocChips';
import { listIocs, type IndicatorStatus, type IndicatorType } from './intelligenceApi';

const TYPES: IndicatorType[] = ['IPV4', 'IPV6', 'DOMAIN', 'URL', 'MD5', 'SHA1', 'SHA256', 'EMAIL'];
const STATUSES: IndicatorStatus[] = ['ACTIVE', 'EXPIRED', 'REVOKED'];

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Référentiel des indicateurs de compromission (IOC) — le renseignement du SOC. */
function IntelligencePage() {
  const [type, setType] = useState<IndicatorType | ''>('');
  const [status, setStatus] = useState<IndicatorStatus | ''>('');
  const [feedSource, setFeedSource] = useState('');
  const [tag, setTag] = useState('');
  const [minConfidence, setMinConfidence] = useState<number | ''>('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['iocs', { type, status, feedSource, tag, minConfidence, search, page, size }],
    queryFn: () => listIocs({ type, status, feedSource, tag, minConfidence, search, page, size }),
    placeholderData: keepPreviousData,
  });

  const resetPage = () => setPage(0);

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h5" component="h2">
          Threat Intelligence
        </Typography>
      </Box>

      <Stack direction="row" spacing={2} useFlexGap sx={{ mb: 2, flexWrap: 'wrap' }}>
        <TextField
          label="Recherche"
          size="small"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            resetPage();
          }}
          placeholder="valeur ou description"
          sx={{ minWidth: 200 }}
        />
        <TextField
          select
          label="Type"
          size="small"
          value={type}
          onChange={(e) => {
            setType(e.target.value as IndicatorType | '');
            resetPage();
          }}
          sx={{ minWidth: 140 }}
        >
          <MenuItem value="">Tous</MenuItem>
          {TYPES.map((value) => (
            <MenuItem key={value} value={value}>
              {IOC_TYPE_LABELS[value]}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Statut"
          size="small"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as IndicatorStatus | '');
            resetPage();
          }}
          sx={{ minWidth: 140 }}
        >
          <MenuItem value="">Tous</MenuItem>
          {STATUSES.map((value) => (
            <MenuItem key={value} value={value}>
              {IOC_STATUS_LABELS[value]}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          label="Source"
          size="small"
          value={feedSource}
          onChange={(e) => {
            setFeedSource(e.target.value);
            resetPage();
          }}
          placeholder="misp, otx…"
          sx={{ minWidth: 140 }}
        />
        <TextField
          label="Tag"
          size="small"
          value={tag}
          onChange={(e) => {
            setTag(e.target.value);
            resetPage();
          }}
          placeholder="c2, ransomware…"
          sx={{ minWidth: 140 }}
        />
        <TextField
          label="Confiance min."
          size="small"
          type="number"
          value={minConfidence}
          onChange={(e) => {
            const raw = e.target.value;
            setMinConfidence(raw === '' ? '' : Math.max(0, Math.min(100, Number(raw))));
            resetPage();
          }}
          slotProps={{ htmlInput: { min: 0, max: 100 } }}
          sx={{ minWidth: 120 }}
        />
      </Stack>

      {isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}
      {isError && (
        <Alert severity="error">
          {problemDetail(error, 'Impossible de charger les indicateurs.')}
        </Alert>
      )}

      {data && (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label="Référentiel des indicateurs">
            <TableHead>
              <TableRow>
                <TableCell>Type</TableCell>
                <TableCell>Valeur</TableCell>
                <TableCell>Statut</TableCell>
                <TableCell sx={{ minWidth: 110 }}>Confiance</TableCell>
                <TableCell>TLP</TableCell>
                <TableCell>Source</TableCell>
                <TableCell>Dernière obs.</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                      Aucun indicateur — les flux CTI alimentent le référentiel, ou déclarez-en un.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {data.items.map((ioc) => (
                <TableRow key={ioc.id} hover>
                  <TableCell>
                    <IocTypeChip type={ioc.type} />
                  </TableCell>
                  <TableCell
                    sx={{
                      maxWidth: 320,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      fontFamily: 'monospace',
                    }}
                    title={ioc.value}
                  >
                    {ioc.value}
                  </TableCell>
                  <TableCell>
                    <IocStatusChip status={ioc.status} />
                  </TableCell>
                  <TableCell>
                    <ConfidenceBar confidence={ioc.confidence} />
                  </TableCell>
                  <TableCell>
                    <TlpChip tlp={ioc.tlp} />
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{ioc.feedSource}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    <Typography variant="caption" color="text.secondary">
                      {formatDate(ioc.lastSeen)}
                    </Typography>
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
              resetPage();
            }}
            rowsPerPageOptions={[10, 25, 50, 100]}
            labelRowsPerPage="Lignes par page"
          />
        </TableContainer>
      )}
    </Box>
  );
}

export default IntelligencePage;
