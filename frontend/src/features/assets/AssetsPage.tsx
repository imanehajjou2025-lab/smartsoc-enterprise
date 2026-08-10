import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
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
import {
  AgentConnectionStatusChip,
  AssetStatusChip,
  CriticalityChip,
  ExposureChip,
  ASSET_STATUS_LABELS,
  ASSET_TYPE_LABELS,
  EXPOSURE_LABELS,
} from './assetChips';
import AssetDetailDrawer from './AssetDetailDrawer';
import RegisterAssetDialog from './RegisterAssetDialog';
import {
  listAssets,
  type AssetCriticality,
  type AssetExposure,
  type AssetStatus,
  type AssetType,
} from './assetsApi';

const TYPES: AssetType[] = [
  'SERVER',
  'WORKSTATION',
  'NETWORK_DEVICE',
  'DATABASE',
  'APPLICATION',
  'CLOUD_RESOURCE',
  'OTHER',
];
const CRITICALITIES: AssetCriticality[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const EXPOSURES: AssetExposure[] = ['INTERNET_FACING', 'INTERNAL', 'ISOLATED'];
const STATUSES: AssetStatus[] = ['ACTIVE', 'DECOMMISSIONED'];

/** Inventaire des actifs supervisés — le contexte métier du SOC. */
function AssetsPage() {
  const [type, setType] = useState<AssetType | ''>('');
  const [criticality, setCriticality] = useState<AssetCriticality | ''>('');
  const [exposure, setExposure] = useState<AssetExposure | ''>('');
  const [status, setStatus] = useState<AssetStatus | ''>('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [registerOpen, setRegisterOpen] = useState(false);
  // Lien profond /assets?selected={id} (ex. depuis le tiroir d'une
  // alerte, ou URL partagée) : le tiroir s'ouvre dès le premier rendu.
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedId, setSelectedId] = useState<string | null>(searchParams.get('selected'));

  const closeDrawer = () => {
    setSelectedId(null);
    if (searchParams.has('selected')) {
      setSearchParams({}, { replace: true });
    }
  };

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['assets', { type, criticality, exposure, status, search, page, size }],
    queryFn: () => listAssets({ type, criticality, exposure, status, search, page, size }),
    placeholderData: keepPreviousData,
  });

  const resetPage = () => setPage(0);

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h5" component="h2">
          Actifs
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setRegisterOpen(true)}>
          Enregistrer un actif
        </Button>
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
          placeholder="hostname ou nom"
          sx={{ minWidth: 200 }}
        />
        <TextField
          select
          label="Type"
          size="small"
          value={type}
          onChange={(e) => {
            setType(e.target.value as AssetType | '');
            resetPage();
          }}
          sx={{ minWidth: 170 }}
        >
          <MenuItem value="">Tous</MenuItem>
          {TYPES.map((value) => (
            <MenuItem key={value} value={value}>
              {ASSET_TYPE_LABELS[value]}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Criticité"
          size="small"
          value={criticality}
          onChange={(e) => {
            setCriticality(e.target.value as AssetCriticality | '');
            resetPage();
          }}
          sx={{ minWidth: 140 }}
        >
          <MenuItem value="">Toutes</MenuItem>
          {CRITICALITIES.map((value) => (
            <MenuItem key={value} value={value}>
              {value}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Exposition"
          size="small"
          value={exposure}
          onChange={(e) => {
            setExposure(e.target.value as AssetExposure | '');
            resetPage();
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">Toutes</MenuItem>
          {EXPOSURES.map((value) => (
            <MenuItem key={value} value={value}>
              {EXPOSURE_LABELS[value]}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Statut"
          size="small"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as AssetStatus | '');
            resetPage();
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">Tous</MenuItem>
          {STATUSES.map((value) => (
            <MenuItem key={value} value={value}>
              {ASSET_STATUS_LABELS[value]}
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
        <Alert severity="error">{problemDetail(error, 'Impossible de charger les actifs.')}</Alert>
      )}

      {data && (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label="Inventaire des actifs">
            <TableHead>
              <TableRow>
                <TableCell>Hostname</TableCell>
                <TableCell>Nom</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Criticité</TableCell>
                <TableCell>Exposition</TableCell>
                <TableCell>Statut</TableCell>
                <TableCell>Connexion</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                      Aucun actif inventorié — enregistrez le premier.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {data.items.map((asset) => (
                <TableRow
                  key={asset.id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => setSelectedId(asset.id)}
                >
                  <TableCell sx={{ whiteSpace: 'nowrap', fontFamily: 'monospace' }}>
                    {asset.hostname}
                  </TableCell>
                  <TableCell sx={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {asset.displayName}
                  </TableCell>
                  <TableCell>{ASSET_TYPE_LABELS[asset.type]}</TableCell>
                  <TableCell>
                    <CriticalityChip criticality={asset.criticality} />
                  </TableCell>
                  <TableCell>
                    <ExposureChip exposure={asset.exposure} />
                  </TableCell>
                  <TableCell>
                    <AssetStatusChip status={asset.status} />
                  </TableCell>
                  <TableCell>
                    <AgentConnectionStatusChip status={asset.agentConnectionStatus} />
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

      <RegisterAssetDialog open={registerOpen} onClose={() => setRegisterOpen(false)} />
      <AssetDetailDrawer assetId={selectedId} onClose={closeDrawer} />
    </Box>
  );
}

export default AssetsPage;
