import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import AddIcon from '@mui/icons-material/Add';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import GenerateReportDialog from './GenerateReportDialog';
import ReportDetailDrawer from './ReportDetailDrawer';
import { downloadReportCsv, downloadReportPdf, listReports } from './reportsApi';

const PAGE_SIZE = 25;

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Rapports SOC — instantanés figés d'indicateurs sur une période passée (ADR-013). Génération réservée à l'encadrement. */
function ReportsPage() {
  const role = useAppSelector((state) => state.auth.user?.role);
  const canGenerate = role === 'ADMIN' || role === 'SOC_MANAGER';

  const [dialogOpen, setDialogOpen] = useState(false);

  // Lien profond /reports?selected={id} : le tiroir de détail s'ouvre dès
  // le premier rendu, même patron que /assets et /mitre.
  const [searchParams, setSearchParams] = useSearchParams();
  const selected = searchParams.get('selected');
  const closeDrawer = () => setSearchParams({}, { replace: true });

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['reports'],
    queryFn: () => listReports(0, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Box>
          <Typography variant="h5" component="h2">
            Rapports
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Instantanés figés d&apos;indicateurs sur une période passée — distincts du tableau de
            bord
          </Typography>
        </Box>
        {canGenerate && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
            Générer un rapport
          </Button>
        )}
      </Box>

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
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Titre</TableCell>
                <TableCell>Période</TableCell>
                <TableCell>Généré par</TableCell>
                <TableCell>Généré le</TableCell>
                <TableCell align="right">Export</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5}>
                    <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                      Aucun rapport — générez le premier instantané.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {data.items.map((report) => (
                <TableRow
                  key={report.id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => setSearchParams({ selected: report.id })}
                >
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {report.title}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption" color="text.secondary">
                      {formatDate(report.periodStart)} → {formatDate(report.periodEnd)}
                    </Typography>
                  </TableCell>
                  <TableCell>{report.generatedBy}</TableCell>
                  <TableCell>{formatDate(report.generatedAt)}</TableCell>
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
