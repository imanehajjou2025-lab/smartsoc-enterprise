import { useState } from 'react';
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
import CheckCircleOutlineOutlinedIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import CloseIcon from '@mui/icons-material/Close';
import FilterListIcon from '@mui/icons-material/FilterList';
import LockOpenOutlinedIcon from '@mui/icons-material/LockOpenOutlined';
import PersonOutlineOutlinedIcon from '@mui/icons-material/PersonOutlineOutlined';
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import { resolveChipColor } from '../../shared/components/chipStyles';
import KpiTile from '../../shared/components/KpiTile';
import PageHeaderBanner from '../../shared/components/PageHeaderBanner';
import type { AlertSeverity } from '../alerts/alertsApi';
import { SeverityChip } from '../alerts/chips';
import { CaseStatusChip, CASE_STATUS_LABELS } from './caseChips';
import CaseDetailDrawer from './CaseDetailDrawer';
import CreateCaseDialog from './CreateCaseDialog';
import { listCases, type CaseStatus } from './investigationsApi';

const PRIORITIES: AlertSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const STATUSES: CaseStatus[] = ['OPEN', 'IN_PROGRESS', 'CLOSED'];

const STATUS_TILE_LABEL: Record<CaseStatus, string> = {
  OPEN: 'Cas ouverts',
  IN_PROGRESS: 'Cas en cours',
  CLOSED: 'Cas clôturés',
};

const STATUS_TILE_ICON: Record<CaseStatus, React.ReactNode> = {
  OPEN: <LockOpenOutlinedIcon />,
  IN_PROGRESS: <TravelExploreIcon />,
  CLOSED: <CheckCircleOutlineOutlinedIcon />,
};

const STATUS_TILE_COLOR: Record<CaseStatus, 'info' | 'warning' | 'default'> = {
  OPEN: 'info',
  IN_PROGRESS: 'warning',
  CLOSED: 'default',
};

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Nombre total de cas pour un statut donné (ou tous) — dérivé d'une pagination réelle (size=1), pas d'endpoint dédié. */
function useCaseCount(status?: CaseStatus) {
  return useQuery({
    queryKey: ['investigations-count', status ?? 'ALL'],
    queryFn: () => listCases({ status: status ?? '', priority: '', page: 0, size: 1 }),
    select: (d) => d.totalElements,
    staleTime: 30_000,
  });
}

/** Module Investigations : les cas d'enquête au-dessus des incidents. */
function InvestigationsPage() {
  const theme = useTheme();
  const [status, setStatus] = useState<CaseStatus | ''>('');
  const [priority, setPriority] = useState<AlertSeverity | ''>('');
  const [assignee, setAssignee] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const hasActiveFilters = Boolean(status || priority || assignee);
  const resetFilters = () => {
    setStatus('');
    setPriority('');
    setAssignee('');
    setPage(0);
  };

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['investigations', { status, priority, assignee, page, size }],
    queryFn: () => listCases({ status, priority, assignee: assignee || undefined, page, size }),
    placeholderData: keepPreviousData,
  });

  const totalCount = useCaseCount();
  const openCount = useCaseCount('OPEN');
  const inProgressCount = useCaseCount('IN_PROGRESS');
  const closedCount = useCaseCount('CLOSED');
  const statusCounts: Record<CaseStatus, number | undefined> = {
    OPEN: openCount.data,
    IN_PROGRESS: inProgressCount.data,
    CLOSED: closedCount.data,
  };

  return (
    <Box>
      <PageHeaderBanner
        icon={<TravelExploreIcon sx={{ color: theme.palette.primary.main, fontSize: 28 }} />}
        title="Investigations"
        subtitle="Cas d'enquête approfondie : au-dessus des incidents, jusqu'à la conclusion."
        action={{ label: 'Nouveau cas', icon: <AddIcon />, onClick: () => setCreateOpen(true) }}
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
          label="Total"
          value={totalCount.data == null ? '…' : String(totalCount.data)}
          hint="tous statuts confondus"
          color={severityColors.info}
          icon={<TravelExploreIcon />}
        />
        {STATUSES.map((s) => (
          <KpiTile
            key={s}
            label={STATUS_TILE_LABEL[s]}
            value={statusCounts[s] == null ? '…' : String(statusCounts[s])}
            hint={
              totalCount.data && statusCounts[s] != null
                ? `${Math.round(((statusCounts[s] ?? 0) / totalCount.data) * 100)}% du total`
                : 'du total'
            }
            color={resolveChipColor(theme, STATUS_TILE_COLOR[s])}
            icon={STATUS_TILE_ICON[s]}
            onClick={() => {
              setStatus(s);
              setPage(0);
            }}
          />
        ))}
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
                ? `${data.totalElements} cas correspondant${data.totalElements > 1 ? 's' : ''}`
                : 'Affinez la liste'}
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
            gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(3, 1fr)' },
            gap: 1.5,
            '& .MuiOutlinedInput-root': { borderRadius: 2.5, bgcolor: 'background.paper' },
          }}
        >
          <TextField
            select
            label="Statut"
            size="small"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as CaseStatus | '');
              setPage(0);
            }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <TravelExploreIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
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
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <ReportProblemOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
          >
            <MenuItem value="">Toutes</MenuItem>
            {PRIORITIES.map((value) => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Assigné à"
            placeholder="nom d'utilisateur…"
            size="small"
            value={assignee}
            onChange={(e) => {
              setAssignee(e.target.value);
              setPage(0);
            }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <PersonOutlineOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
              },
            }}
          />
        </Box>

        {hasActiveFilters && (
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mt: 2 }}>
            {status && (
              <Chip
                size="small"
                label={`Statut : ${CASE_STATUS_LABELS[status]}`}
                onDelete={() => {
                  setStatus('');
                  setPage(0);
                }}
              />
            )}
            {priority && (
              <Chip
                size="small"
                label={`Priorité : ${priority}`}
                onDelete={() => {
                  setPriority('');
                  setPage(0);
                }}
              />
            )}
            {assignee && (
              <Chip
                size="small"
                label={`Assigné : ${assignee}`}
                onDelete={() => {
                  setAssignee('');
                  setPage(0);
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
          {problemDetail(error, 'Impossible de charger les cas d’investigation.')}
        </Alert>
      )}

      {data && (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 3 }}>
          <Table size="small" aria-label="Liste des cas d'investigation">
            <TableHead>
              <TableRow sx={{ '& th': { bgcolor: 'action.hover' } }}>
                <TableCell sx={{ fontWeight: 700 }}>Référence</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Priorité</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Titre</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Statut</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Assigné</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Ouvert le</TableCell>
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
                  sx={{
                    cursor: 'pointer',
                    borderLeft: '3px solid',
                    borderLeftColor: alpha(
                      severityColors[investigation.priority.toLowerCase() as keyof typeof severityColors],
                      0.6,
                    ),
                  }}
                  onClick={() => setSelectedId(investigation.id)}
                >
                  <TableCell sx={{ whiteSpace: 'nowrap', fontWeight: 700 }}>
                    {investigation.reference}
                  </TableCell>
                  <TableCell>
                    <SeverityChip severity={investigation.priority} />
                  </TableCell>
                  <TableCell sx={{ maxWidth: 360, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {investigation.title}
                  </TableCell>
                  <TableCell>
                    <CaseStatusChip status={investigation.status} />
                  </TableCell>
                  <TableCell>
                    {investigation.assigneeUsername ? (
                      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                        <PersonOutlineOutlinedIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                        <Typography variant="body2">{investigation.assigneeUsername}</Typography>
                      </Stack>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        —
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap', color: 'text.secondary' }}>
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
