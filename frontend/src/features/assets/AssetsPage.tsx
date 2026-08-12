import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import AddIcon from '@mui/icons-material/Add';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import InputAdornment from '@mui/material/InputAdornment';
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
import { alpha, useTheme } from '@mui/material/styles';
import CloseIcon from '@mui/icons-material/Close';
import DevicesOutlinedIcon from '@mui/icons-material/DevicesOutlined';
import DnsOutlinedIcon from '@mui/icons-material/DnsOutlined';
import FilterListIcon from '@mui/icons-material/FilterList';
import PowerOffOutlinedIcon from '@mui/icons-material/PowerOffOutlined';
import PublicIcon from '@mui/icons-material/Public';
import SearchIcon from '@mui/icons-material/Search';
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import KpiTile from '../../shared/components/KpiTile';
import PageHeaderBanner from '../../shared/components/PageHeaderBanner';
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

/** Nombre d'actifs pour un filtre donné — dérivé d'une pagination réelle (size=1), pas d'endpoint dédié. */
function useAssetCount(filters: { criticality?: AssetCriticality; exposure?: AssetExposure; status?: AssetStatus }) {
  return useQuery({
    queryKey: ['assets-count', filters],
    queryFn: () =>
      listAssets({
        criticality: filters.criticality ?? '',
        exposure: filters.exposure ?? '',
        status: filters.status ?? '',
        page: 0,
        size: 1,
      }),
    select: (d) => d.totalElements,
    staleTime: 30_000,
  });
}

/** Inventaire des actifs supervisés — le contexte métier du SOC. */
function AssetsPage() {
  const theme = useTheme();
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

  const hasActiveFilters = Boolean(type || criticality || exposure || status || search);
  const resetFilters = () => {
    setType('');
    setCriticality('');
    setExposure('');
    setStatus('');
    setSearch('');
    setPage(0);
  };

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['assets', { type, criticality, exposure, status, search, page, size }],
    queryFn: () => listAssets({ type, criticality, exposure, status, search, page, size }),
    placeholderData: keepPreviousData,
  });

  const totalCount = useAssetCount({});
  const criticalCount = useAssetCount({ criticality: 'CRITICAL' });
  const exposedCount = useAssetCount({ exposure: 'INTERNET_FACING' });
  const decommissionedCount = useAssetCount({ status: 'DECOMMISSIONED' });

  const resetPage = () => setPage(0);

  return (
    <Box>
      <PageHeaderBanner
        icon={<DnsOutlinedIcon sx={{ color: theme.palette.primary.main, fontSize: 28 }} />}
        title="Actifs"
        subtitle="Inventaire des actifs supervisés — le contexte métier du SOC."
        action={{ label: 'Enregistrer un actif', icon: <AddIcon />, onClick: () => setRegisterOpen(true) }}
      />

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' },
          gap: 2,
          mb: 3,
        }}
      >
        <KpiTile
          label="Actifs"
          value={totalCount.data == null ? '…' : String(totalCount.data)}
          hint="au total"
          color={severityColors.info}
          icon={<DnsOutlinedIcon />}
        />
        <KpiTile
          label="Critiques"
          value={criticalCount.data == null ? '…' : String(criticalCount.data)}
          hint="criticité critique"
          color={severityColors.critical}
          icon={<ShieldOutlinedIcon />}
          onClick={() => {
            setCriticality('CRITICAL');
            resetPage();
          }}
        />
        <KpiTile
          label="Exposés Internet"
          value={exposedCount.data == null ? '…' : String(exposedCount.data)}
          hint="surface d'attaque"
          color={severityColors.high}
          icon={<PublicIcon />}
          onClick={() => {
            setExposure('INTERNET_FACING');
            resetPage();
          }}
        />
        <KpiTile
          label="Décommissionnés"
          value={decommissionedCount.data == null ? '…' : String(decommissionedCount.data)}
          hint="hors service"
          color={severityColors.medium}
          icon={<PowerOffOutlinedIcon />}
          onClick={() => {
            setStatus('DECOMMISSIONED');
            resetPage();
          }}
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
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 2.25 }}>
          <Box
            sx={{
              width: 36,
              height: 36,
              borderRadius: '50%',
              flexShrink: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: theme.palette.primary.main,
              bgcolor: alpha(theme.palette.primary.main, 0.16),
              boxShadow: `0 0 14px 2px ${alpha(theme.palette.primary.main, 0.4)}`,
            }}
          >
            <FilterListIcon fontSize="small" />
          </Box>
          <Box sx={{ minWidth: 0, flexGrow: 1 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
              Filtres
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ opacity: 0.75 }}>
              {data
                ? `${data.totalElements} actif${data.totalElements > 1 ? 's' : ''} correspondant${data.totalElements > 1 ? 's' : ''}`
                : "Affinez l'inventaire"}
            </Typography>
          </Box>
          {hasActiveFilters && (
            <Button
              size="small"
              onClick={resetFilters}
              startIcon={<CloseIcon fontSize="small" />}
              sx={{ fontWeight: 700, borderRadius: 2, flexShrink: 0 }}
            >
              Réinitialiser
            </Button>
          )}
        </Stack>

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(3, 1fr)', lg: 'repeat(5, 1fr)' },
            gap: 1.5,
            '& .MuiOutlinedInput-root': { borderRadius: 2.5, bgcolor: 'background.paper' },
          }}
        >
          <TextField
            label="Recherche"
            size="small"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              resetPage();
            }}
            placeholder="hostname ou nom"
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
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
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <DevicesOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
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
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <ShieldOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
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
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <PublicIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
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
          >
            <MenuItem value="">Tous</MenuItem>
            {STATUSES.map((value) => (
              <MenuItem key={value} value={value}>
                {ASSET_STATUS_LABELS[value]}
              </MenuItem>
            ))}
          </TextField>
        </Box>

        {hasActiveFilters && (
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mt: 2 }}>
            {search && (
              <Chip
                size="small"
                label={`Recherche : ${search}`}
                onDelete={() => {
                  setSearch('');
                  resetPage();
                }}
              />
            )}
            {type && (
              <Chip
                size="small"
                label={`Type : ${ASSET_TYPE_LABELS[type]}`}
                onDelete={() => {
                  setType('');
                  resetPage();
                }}
              />
            )}
            {criticality && (
              <Chip
                size="small"
                label={`Criticité : ${criticality}`}
                onDelete={() => {
                  setCriticality('');
                  resetPage();
                }}
              />
            )}
            {exposure && (
              <Chip
                size="small"
                label={`Exposition : ${EXPOSURE_LABELS[exposure]}`}
                onDelete={() => {
                  setExposure('');
                  resetPage();
                }}
              />
            )}
            {status && (
              <Chip
                size="small"
                label={`Statut : ${ASSET_STATUS_LABELS[status]}`}
                onDelete={() => {
                  setStatus('');
                  resetPage();
                }}
              />
            )}
          </Stack>
        )}
      </Paper>

      {isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}
      {isError && (
        <Alert severity="error">{problemDetail(error, 'Impossible de charger les actifs.')}</Alert>
      )}

      {data && (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 3 }}>
          <Table size="small" aria-label="Inventaire des actifs">
            <TableHead>
              <TableRow sx={{ '& th': { bgcolor: 'action.hover' } }}>
                <TableCell sx={{ fontWeight: 700 }}>Hostname</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Nom</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Type</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Criticité</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Exposition</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Statut</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Connexion</TableCell>
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
                  sx={{
                    cursor: 'pointer',
                    borderLeft: '3px solid',
                    borderLeftColor: alpha(
                      severityColors[asset.criticality.toLowerCase() as keyof typeof severityColors],
                      0.6,
                    ),
                  }}
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
