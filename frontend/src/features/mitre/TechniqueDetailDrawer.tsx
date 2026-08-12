import { useEffect, useState } from 'react';
import axios from 'axios';
import GpsFixedIcon from '@mui/icons-material/GpsFixed';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import Stack from '@mui/material/Stack';
import TablePagination from '@mui/material/TablePagination';
import Typography from '@mui/material/Typography';
import { alpha } from '@mui/material/styles';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import ActionCard from '../../shared/components/ActionCard';
import DetailDrawerHeader from '../../shared/components/DetailDrawerHeader';
import DetailField from '../../shared/components/DetailField';
import MutedText from '../../shared/components/MutedText';
import SectionLabel from '../../shared/components/SectionLabel';
import { SeverityChip, StatusChip } from '../alerts/chips';
import { DeprecatedChip, TacticChip } from './mitreChips';
import { getTechnique, listTechniqueAlerts } from './mitreApi';

interface Props {
  attackId: string | null;
  onClose: () => void;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/**
 * Fiche d'une technique ATT&CK : métadonnées du catalogue (tactiques,
 * dépréciation, lien attack.mitre.org) et alertes citant la technique
 * (retro-hunt), paginées — le totalElements EST le compteur de corrélation,
 * calculé à la lecture (une alerte d'hier remonte sans rattrapage).
 */
function TechniqueDetailDrawer({ attackId, onClose }: Props) {
  const [alertsPage, setAlertsPage] = useState(0);

  const {
    data: technique,
    isPending,
    isError,
    error,
  } = useQuery({
    queryKey: ['mitre-technique', attackId],
    queryFn: () => getTechnique(attackId!),
    enabled: Boolean(attackId),
    retry: (failureCount, err) =>
      !(axios.isAxiosError(err) && err.response?.status === 404) && failureCount < 2,
  });

  // Identifiant inconnu (lien profond périmé) : le tiroir se referme
  // proprement, la matrice reste — même comportement que la fiche d'IOC.
  const notFound = isError && axios.isAxiosError(error) && error.response?.status === 404;
  useEffect(() => {
    if (notFound) {
      onClose();
    }
  }, [notFound, onClose]);

  const { data: correlated } = useQuery({
    queryKey: ['mitre-technique', attackId, 'alerts', alertsPage],
    queryFn: () => listTechniqueAlerts(attackId!, alertsPage, 10),
    enabled: Boolean(attackId),
    placeholderData: keepPreviousData,
  });

  const accent = technique?.deprecated ? severityColors.medium : severityColors.critical;

  return (
    <Drawer anchor="right" open={Boolean(attackId)} onClose={onClose}>
      <Box sx={{ width: 560, maxWidth: '92vw', p: 3 }}>
        {isPending && (
          <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
            <CircularProgress />
          </Box>
        )}
        {isError && !notFound && (
          <Alert severity="error">{problemDetail(error, 'Chargement impossible.')}</Alert>
        )}

        {technique && (
          <>
            <DetailDrawerHeader
              color={accent}
              icon={<GpsFixedIcon />}
              title={technique.name}
              onClose={onClose}
            />

            {(technique.subTechnique || technique.deprecated) && (
              <Stack direction="row" spacing={1} useFlexGap sx={{ mb: 2, flexWrap: 'wrap' }}>
                {technique.subTechnique && (
                  <Chip label="Sous-technique" size="small" variant="outlined" />
                )}
                {technique.deprecated && <DeprecatedChip />}
              </Stack>
            )}

            <DetailField label="Tactiques" color={accent}>
              <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
                {technique.tactics.map((tactic) => (
                  <TacticChip key={tactic} name={tactic} />
                ))}
              </Stack>
            </DetailField>

            {technique.subTechnique && technique.parentId && (
              <DetailField label="Technique parente" color={accent}>
                <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                  {technique.parentId}
                </Typography>
              </DetailField>
            )}

            {technique.description && (
              <DetailField label="Description" color={accent}>
                <MutedText>{technique.description}</MutedText>
              </DetailField>
            )}

            {technique.url && (
              <Box sx={{ mb: 2 }}>
                <ActionCard
                  icon={<OpenInNewIcon />}
                  title="Voir sur attack.mitre.org"
                  description="Documentation officielle MITRE ATT&CK pour cette technique."
                  color={accent}
                  onClick={() => window.open(technique.url!, '_blank', 'noopener,noreferrer')}
                />
              </Box>
            )}

            <Divider sx={{ my: 2 }} />

            <SectionLabel color={severityColors.info}>
              {`Alertes citant cette technique${correlated ? ` (${correlated.totalElements})` : ''}`}
            </SectionLabel>
            <Box sx={{ mt: 1.5 }}>
              {correlated && correlated.items.length === 0 && (
                <MutedText>Aucune alerte ne cite cette technique.</MutedText>
              )}
              <Stack spacing={0.5}>
                {correlated?.items.map((alert) => (
                  <Stack
                    key={alert.id}
                    direction="row"
                    spacing={1}
                    sx={{
                      alignItems: 'center',
                      p: 0.75,
                      borderRadius: 2,
                      borderLeft: '3px solid',
                      borderLeftColor: alpha(severityColors.info, 0.4),
                      transition: 'background-color 120ms ease',
                      '&:hover': { bgcolor: (t) => alpha(t.palette.text.primary, 0.05) },
                    }}
                  >
                    <SeverityChip severity={alert.severity} />
                    <Typography variant="body2" sx={{ flexGrow: 1 }} noWrap>
                      {alert.title}
                    </Typography>
                    <StatusChip status={alert.status} />
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ whiteSpace: 'nowrap' }}
                    >
                      {formatDate(alert.detectedAt)}
                    </Typography>
                  </Stack>
                ))}
              </Stack>
              {correlated && correlated.totalElements > 10 && (
                <TablePagination
                  component="div"
                  count={correlated.totalElements}
                  page={alertsPage}
                  onPageChange={(_, newPage) => setAlertsPage(newPage)}
                  rowsPerPage={10}
                  rowsPerPageOptions={[10]}
                  labelRowsPerPage=""
                />
              )}
            </Box>
          </>
        )}
      </Box>
    </Drawer>
  );
}

export default TechniqueDetailDrawer;
