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
import FilterListIcon from '@mui/icons-material/FilterList';
import GppGoodOutlinedIcon from '@mui/icons-material/GppGoodOutlined';
import GppMaybeOutlinedIcon from '@mui/icons-material/GppMaybeOutlined';
import LabelOutlinedIcon from '@mui/icons-material/LabelOutlined';
import SearchIcon from '@mui/icons-material/Search';
import SourceOutlinedIcon from '@mui/icons-material/SourceOutlined';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { severityColors } from '../../app/theme';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import KpiTile from '../../shared/components/KpiTile';
import PageHeaderBanner from '../../shared/components/PageHeaderBanner';
import DeclareIocDialog from './DeclareIocDialog';
import {
  ConfidenceBar,
  IocStatusChip,
  IocTypeChip,
  TlpChip,
  IOC_STATUS_LABELS,
  IOC_TYPE_LABELS,
} from './iocChips';
import IocDetailDrawer from './IocDetailDrawer';
import { listIocs, type IndicatorStatus, type IndicatorType } from './intelligenceApi';

const TYPES: IndicatorType[] = ['IPV4', 'IPV6', 'DOMAIN', 'URL', 'MD5', 'SHA1', 'SHA256', 'EMAIL'];
const STATUSES: IndicatorStatus[] = ['ACTIVE', 'EXPIRED', 'REVOKED'];

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Nombre d'IOC pour un filtre donné — dérivé d'une pagination réelle (size=1), pas d'endpoint dédié. */
function useIocCount(filters: { status?: IndicatorStatus; minConfidence?: number }) {
  return useQuery({
    queryKey: ['iocs-count', filters],
    queryFn: () =>
      listIocs({
        type: '',
        status: filters.status ?? '',
        feedSource: '',
        tag: '',
        minConfidence: filters.minConfidence ?? '',
        search: '',
        page: 0,
        size: 1,
      }),
    select: (d) => d.totalElements,
    staleTime: 30_000,
  });
}

/** Référentiel des indicateurs de compromission (IOC) — le renseignement du SOC. */
function IntelligencePage() {
  const theme = useTheme();
  const [type, setType] = useState<IndicatorType | ''>('');
  const [status, setStatus] = useState<IndicatorStatus | ''>('');
  const [feedSource, setFeedSource] = useState('');
  const [tag, setTag] = useState('');
  const [minConfidence, setMinConfidence] = useState<number | ''>('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [declareOpen, setDeclareOpen] = useState(false);
  const role = useAppSelector((state) => state.auth.user?.role);
  const canWrite = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';
  // Lien profond /intelligence?selected={id} (ex. depuis le tiroir d'une
  // alerte enrichie, ou URL partagée) : le tiroir s'ouvre dès le premier
  // rendu.
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedId, setSelectedId] = useState<string | null>(searchParams.get('selected'));

  const closeDrawer = () => {
    setSelectedId(null);
    if (searchParams.has('selected')) {
      setSearchParams({}, { replace: true });
    }
  };

  const hasActiveFilters = Boolean(type || status || feedSource || tag || minConfidence || search);
  const resetFilters = () => {
    setType('');
    setStatus('');
    setFeedSource('');
    setTag('');
    setMinConfidence('');
    setSearch('');
    setPage(0);
  };

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['iocs', { type, status, feedSource, tag, minConfidence, search, page, size }],
    queryFn: () => listIocs({ type, status, feedSource, tag, minConfidence, search, page, size }),
    placeholderData: keepPreviousData,
  });

  const totalCount = useIocCount({});
  const activeCount = useIocCount({ status: 'ACTIVE' });
  const highConfidenceCount = useIocCount({ minConfidence: 75 });
  const revokedCount = useIocCount({ status: 'REVOKED' });

  const resetPage = () => setPage(0);

  return (
    <Box>
      <PageHeaderBanner
        icon={<TravelExploreIcon sx={{ color: theme.palette.primary.main, fontSize: 28 }} />}
        title="Threat Intelligence"
        subtitle="Référentiel des indicateurs de compromission (IOC) — le renseignement du SOC."
        action={
          canWrite
            ? { label: 'Déclarer un IOC', icon: <AddIcon />, onClick: () => setDeclareOpen(true) }
            : undefined
        }
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
          label="Indicateurs"
          value={totalCount.data == null ? '…' : String(totalCount.data)}
          hint="au total"
          color={severityColors.info}
          icon={<TravelExploreIcon />}
        />
        <KpiTile
          label="IOC actifs"
          value={activeCount.data == null ? '…' : String(activeCount.data)}
          hint="enrichissent encore les alertes"
          color={severityColors.low}
          icon={<VerifiedUserOutlinedIcon />}
          onClick={() => {
            setStatus('ACTIVE');
            resetPage();
          }}
        />
        <KpiTile
          label="Haute confiance"
          value={highConfidenceCount.data == null ? '…' : String(highConfidenceCount.data)}
          hint="confiance ≥ 75"
          color={severityColors.critical}
          icon={<GppMaybeOutlinedIcon />}
          onClick={() => {
            setMinConfidence(75);
            resetPage();
          }}
        />
        <KpiTile
          label="IOC révoqués"
          value={revokedCount.data == null ? '…' : String(revokedCount.data)}
          hint="ne doivent plus enrichir"
          color={severityColors.medium}
          icon={<GppGoodOutlinedIcon />}
          onClick={() => {
            setStatus('REVOKED');
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
                ? `${data.totalElements} indicateur${data.totalElements > 1 ? 's' : ''} correspondant${data.totalElements > 1 ? 's' : ''}`
                : 'Affinez le référentiel'}
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
            gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(3, 1fr)', lg: 'repeat(6, 1fr)' },
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
            placeholder="valeur ou description"
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
              setType(e.target.value as IndicatorType | '');
              resetPage();
            }}
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
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SourceOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
          />
          <TextField
            label="Tag (exact)"
            size="small"
            value={tag}
            onChange={(e) => {
              setTag(e.target.value);
              resetPage();
            }}
            placeholder="ex. ransomware"
            helperText="Correspondance exacte — le libellé attaché à l'IOC lors de sa déclaration"
            slotProps={{
              formHelperText: { sx: { fontSize: 10, lineHeight: 1.3, mt: 0.5, maxWidth: 160 } },
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <LabelOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
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
          />
        </Box>

        {hasActiveFilters && (
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mt: 2 }}>
            {search && (
              <Chip size="small" label={`Recherche : ${search}`} onDelete={() => { setSearch(''); resetPage(); }} />
            )}
            {type && (
              <Chip size="small" label={`Type : ${IOC_TYPE_LABELS[type]}`} onDelete={() => { setType(''); resetPage(); }} />
            )}
            {status && (
              <Chip size="small" label={`Statut : ${IOC_STATUS_LABELS[status]}`} onDelete={() => { setStatus(''); resetPage(); }} />
            )}
            {feedSource && (
              <Chip size="small" label={`Source : ${feedSource}`} onDelete={() => { setFeedSource(''); resetPage(); }} />
            )}
            {tag && <Chip size="small" label={`Tag : ${tag}`} onDelete={() => { setTag(''); resetPage(); }} />}
            {minConfidence !== '' && (
              <Chip
                size="small"
                label={`Confiance ≥ ${minConfidence}`}
                onDelete={() => {
                  setMinConfidence('');
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
        <Alert severity="error">
          {problemDetail(error, 'Impossible de charger les indicateurs.')}
        </Alert>
      )}

      {data && (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 3 }}>
          <Table size="small" aria-label="Référentiel des indicateurs">
            <TableHead>
              <TableRow sx={{ '& th': { bgcolor: 'action.hover' } }}>
                <TableCell sx={{ fontWeight: 700 }}>Type</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Valeur</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Statut</TableCell>
                <TableCell sx={{ fontWeight: 700, minWidth: 110 }}>Confiance</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>TLP</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Source</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Dernière obs.</TableCell>
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
                <TableRow
                  key={ioc.id}
                  hover
                  sx={{
                    cursor: 'pointer',
                    borderLeft: '3px solid',
                    borderLeftColor: alpha(
                      ioc.status === 'ACTIVE'
                        ? severityColors.low
                        : ioc.status === 'REVOKED'
                          ? severityColors.critical
                          : severityColors.info,
                      0.6,
                    ),
                  }}
                  onClick={() => setSelectedId(ioc.id)}
                >
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
                  <TableCell sx={{ whiteSpace: 'nowrap', color: 'text.secondary' }}>
                    {formatDate(ioc.lastSeen)}
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

      <DeclareIocDialog open={declareOpen} onClose={() => setDeclareOpen(false)} />
      <IocDetailDrawer iocId={selectedId} onClose={closeDrawer} />
    </Box>
  );
}

export default IntelligencePage;
