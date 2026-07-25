import { useEffect, useState } from 'react';
import axios from 'axios';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import Stack from '@mui/material/Stack';
import TablePagination from '@mui/material/TablePagination';
import Typography from '@mui/material/Typography';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
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

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
        {label}
      </Typography>
      {children}
    </Box>
  );
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
            <Stack
              direction="row"
              spacing={1}
              useFlexGap
              sx={{ mb: 1, alignItems: 'center', flexWrap: 'wrap' }}
            >
              <Chip
                label={technique.attackId}
                size="small"
                sx={{ fontFamily: 'monospace', fontWeight: 600 }}
              />
              {technique.subTechnique && (
                <Chip label="Sous-technique" size="small" variant="outlined" />
              )}
              {technique.deprecated && <DeprecatedChip />}
            </Stack>
            <Typography variant="subtitle1" sx={{ mb: 2 }}>
              {technique.name}
            </Typography>

            <Field label="Tactiques">
              <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
                {technique.tactics.map((tactic) => (
                  <TacticChip key={tactic} name={tactic} />
                ))}
              </Stack>
            </Field>
            {technique.subTechnique && technique.parentId && (
              <Field label="Technique parente">
                <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                  {technique.parentId}
                </Typography>
              </Field>
            )}
            {technique.description && (
              <Field label="Description">
                <Typography variant="body2">{technique.description}</Typography>
              </Field>
            )}
            {technique.attackVersion && (
              <Field label="Version ATT&CK">
                <Typography variant="body2">{technique.attackVersion}</Typography>
              </Field>
            )}
            {technique.url && (
              <Button
                component="a"
                href={technique.url}
                target="_blank"
                rel="noopener noreferrer"
                size="small"
                variant="outlined"
                startIcon={<OpenInNewIcon />}
                sx={{ mb: 1 }}
              >
                Voir sur attack.mitre.org
              </Button>
            )}

            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Alertes citant cette technique{correlated ? ` (${correlated.totalElements})` : ''}
            </Typography>
            {correlated && correlated.items.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                Aucune alerte ne cite cette technique.
              </Typography>
            )}
            {correlated?.items.map((alert) => (
              <Stack
                key={alert.id}
                direction="row"
                spacing={1}
                sx={{ alignItems: 'center', mb: 0.5 }}
              >
                <SeverityChip severity={alert.severity} />
                <Typography variant="body2" sx={{ flexGrow: 1 }} noWrap>
                  {alert.title}
                </Typography>
                <StatusChip status={alert.status} />
                <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
                  {formatDate(alert.detectedAt)}
                </Typography>
              </Stack>
            ))}
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
          </>
        )}
      </Box>
    </Drawer>
  );
}

export default TechniqueDetailDrawer;
