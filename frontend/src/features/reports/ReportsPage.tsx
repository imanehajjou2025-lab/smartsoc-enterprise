import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import AddIcon from '@mui/icons-material/Add';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import EventOutlinedIcon from '@mui/icons-material/EventOutlined';
import PersonOutlineOutlinedIcon from '@mui/icons-material/PersonOutlineOutlined';
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined';
import SearchIcon from '@mui/icons-material/Search';
import SummarizeOutlinedIcon from '@mui/icons-material/SummarizeOutlined';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
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
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { severityColors } from '../../app/theme';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import KpiTile from '../../shared/components/KpiTile';
import PageHeaderBanner from '../../shared/components/PageHeaderBanner';
import GenerateReportDialog from './GenerateReportDialog';
import ReportDetailDrawer from './ReportDetailDrawer';
import { downloadReportCsv, downloadReportPdf, listReports } from './reportsApi';

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Rapports SOC — instantanés figés d'indicateurs sur une période passée (ADR-013). Génération réservée à l'encadrement. */
function ReportsPage() {
  const theme = useTheme();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canGenerate = role === 'ADMIN' || role === 'SOC_MANAGER';

  const [dialogOpen, setDialogOpen] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [search, setSearch] = useState('');

  // Lien profond /reports?selected={id} : le tiroir de détail s'ouvre dès
  // le premier rendu, même patron que /assets et /mitre.
  const [searchParams, setSearchParams] = useSearchParams();
  const selected = searchParams.get('selected');
  const closeDrawer = () => setSearchParams({}, { replace: true });

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['reports', page, size],
    queryFn: () => listReports(page, size),
    placeholderData: keepPreviousData,
  });

  // Filtrage local (titre / auteur) sur la page chargée — le serveur trie
  // toujours du plus récent au plus ancien mais n'expose pas encore de
  // recherche plein texte ; ce filtre affine ce qui est déjà à l'écran.
  const filteredItems = useMemo(() => {
    if (!data) return [];
    const needle = search.trim().toLowerCase();
    if (!needle) return data.items;
    return data.items.filter(
      (r) => r.title.toLowerCase().includes(needle) || r.generatedBy.toLowerCase().includes(needle),
    );
  }, [data, search]);

  const latest = data?.items[0] ?? null;
  const distinctAuthors = data
    ? new Set(data.items.map((r) => r.generatedBy)).size
    : 0;
  const latestSpanDays = latest
    ? Math.max(
        1,
        Math.round(
          (new Date(latest.periodEnd).getTime() - new Date(latest.periodStart).getTime()) /
            86_400_000,
        ),
      )
    : null;

  return (
    <Box>
      <PageHeaderBanner
        icon={<SummarizeOutlinedIcon sx={{ color: theme.palette.primary.main, fontSize: 28 }} />}
        title="Rapports"
        subtitle="Instantanés figés d'indicateurs sur une période passée — distincts du tableau de bord."
        action={
          canGenerate
            ? { label: 'Générer un rapport', icon: <AddIcon />, onClick: () => setDialogOpen(true) }
            : undefined
        }
      />

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' },
          gap: 2,
          mb: 3,
        }}
      >
        <KpiTile
          label="Rapports générés"
          value={data ? String(data.totalElements) : '…'}
          hint="au total"
          color={severityColors.info}
          icon={<SummarizeOutlinedIcon />}
        />
        <KpiTile
          label="Dernier rapport"
          value={latest ? formatDate(latest.generatedAt) : '—'}
          hint={latestSpanDays ? `période de ${latestSpanDays} j` : 'aucun rapport'}
          color={severityColors.low}
          icon={<EventOutlinedIcon />}
        />
        <KpiTile
          label="Auteurs"
          value={String(distinctAuthors)}
          hint="ont généré un rapport (page)"
          color={severityColors.medium}
          icon={<PersonOutlineOutlinedIcon />}
        />
      </Box>

      <Paper
        elevation={0}
        sx={{
          p: 2,
          mb: 2,
          borderRadius: 3,
          border: '1px solid',
          borderColor: alpha(theme.palette.primary.main, 0.18),
        }}
      >
        <TextField
          fullWidth
          size="small"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Filtrer par titre ou auteur (sur cette page)…"
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
      </Paper>

      {isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}
      {isError && (
        <Alert severity="error">
          {problemDetail(error, 'Impossible de charger les rapports.')}
        </Alert>
      )}

      {data && (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 3 }}>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ '& th': { bgcolor: 'action.hover' } }}>
                <TableCell sx={{ fontWeight: 700 }}>Titre</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Période</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Généré par</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Généré le</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>
                  Export
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredItems.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5}>
                    <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                      {data.items.length === 0
                        ? 'Aucun rapport — générez le premier instantané.'
                        : 'Aucun rapport ne correspond à ce filtre.'}
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {filteredItems.map((report) => (
                <TableRow
                  key={report.id}
                  hover
                  sx={{
                    cursor: 'pointer',
                    borderLeft: '3px solid',
                    borderLeftColor: alpha(theme.palette.primary.main, 0.4),
                  }}
                  onClick={() => setSearchParams({ selected: report.id })}
                >
                  <TableCell>
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <DescriptionOutlinedIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        {report.title}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      variant="outlined"
                      label={`${formatDate(report.periodStart)} → ${formatDate(report.periodEnd)}`}
                    />
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                      <PersonOutlineOutlinedIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                      <Typography variant="body2">{report.generatedBy}</Typography>
                    </Stack>
                  </TableCell>
                  <TableCell sx={{ color: 'text.secondary', whiteSpace: 'nowrap' }}>
                    {formatDate(report.generatedAt)}
                  </TableCell>
                  <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                    <IconButton
                      size="small"
                      title="Export CSV"
                      onClick={() => void downloadReportCsv(report)}
                    >
                      <DescriptionOutlinedIcon fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      title="Export PDF"
                      onClick={() => void downloadReportPdf(report)}
                    >
                      <PictureAsPdfOutlinedIcon fontSize="small" />
                    </IconButton>
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

      <GenerateReportDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onGenerated={(report) => {
          setDialogOpen(false);
          setSearchParams({ selected: report.id });
        }}
      />
      <ReportDetailDrawer reportId={selected} onClose={closeDrawer} />
    </Box>
  );
}

export default ReportsPage;
