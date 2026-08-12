import { useEffect, useState } from 'react';
import axios from 'axios';
import BlockIcon from '@mui/icons-material/Block';
import LockIcon from '@mui/icons-material/Lock';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import Stack from '@mui/material/Stack';
import TablePagination from '@mui/material/TablePagination';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAppSelector } from '../../app/hooks';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import ActionCard from '../../shared/components/ActionCard';
import DetailDrawerHeader from '../../shared/components/DetailDrawerHeader';
import DetailField from '../../shared/components/DetailField';
import MutedText from '../../shared/components/MutedText';
import SectionLabel from '../../shared/components/SectionLabel';
import { SeverityChip, StatusChip } from '../alerts/chips';
import { ConfidenceBar, IocStatusChip, ReputationVerdictChip, TlpChip } from './iocChips';
import { getIoc, getReputation, listMatchingAlerts, revokeIoc } from './intelligenceApi';

interface Props {
  iocId: string | null;
  onClose: () => void;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
}

/**
 * Fiche d'un indicateur : identité (type + valeur), métadonnées CTI,
 * révocation (décision d'analyste, motif obligatoire — un IOC révoqué
 * passe en lecture seule) et alertes citant l'indicateur (retro-hunt),
 * paginées, dont le totalElements EST le compteur de corrélation.
 */
function IocDetailDrawer({ iocId, onClose }: Props) {
  const queryClient = useQueryClient();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canWrite = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';
  const [alertsPage, setAlertsPage] = useState(0);
  const [revokeOpen, setRevokeOpen] = useState(false);
  const [reason, setReason] = useState('');

  const {
    data: ioc,
    isPending,
    isError,
    error,
  } = useQuery({
    queryKey: ['ioc', iocId],
    queryFn: () => getIoc(iocId!),
    enabled: Boolean(iocId),
    retry: (failureCount, err) =>
      !(axios.isAxiosError(err) && err.response?.status === 404) && failureCount < 2,
  });

  // Id inconnu (lien profond périmé) : le tiroir se referme proprement,
  // la liste reste — même comportement que la fiche d'actif.
  const notFound = isError && axios.isAxiosError(error) && error.response?.status === 404;
  useEffect(() => {
    if (notFound) {
      onClose();
    }
  }, [notFound, onClose]);

  const { data: correlated } = useQuery({
    queryKey: ['ioc', iocId, 'alerts', alertsPage],
    queryFn: () => listMatchingAlerts(iocId!, alertsPage, 10),
    enabled: Boolean(iocId),
    placeholderData: keepPreviousData,
  });

  const revokeMutation = useMutation({
    mutationFn: () => revokeIoc(iocId!, reason.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['ioc', iocId] });
      void queryClient.invalidateQueries({ queryKey: ['iocs'] });
      setRevokeOpen(false);
      setReason('');
    },
  });

  // Réputation VirusTotal : à la demande UNIQUEMENT, jamais au chargement du
  // tiroir (consomme un quota externe réel). Réinitialisée à chaque IOC
  // affiché pour ne jamais montrer le résultat d'un observable précédent.
  const reputationMutation = useMutation({
    mutationFn: () => getReputation(ioc!.type, ioc!.value),
  });
  useEffect(() => {
    reputationMutation.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [iocId]);

  const isRevoked = ioc?.status === 'REVOKED';

  return (
    <Drawer anchor="right" open={Boolean(iocId)} onClose={onClose}>
      <Box sx={{ width: 560, maxWidth: '92vw', p: 3 }}>
        {isPending && (
          <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
            <CircularProgress />
          </Box>
        )}
        {isError && !notFound && (
          <Alert severity="error">{problemDetail(error, 'Chargement impossible.')}</Alert>
        )}

        {ioc && (
          <>
            <DetailDrawerHeader
              color={
                ioc.status === 'ACTIVE'
                  ? severityColors.low
                  : ioc.status === 'REVOKED'
                    ? severityColors.critical
                    : severityColors.info
              }
              icon={<TravelExploreIcon sx={{ fontSize: 26 }} />}
              title={ioc.value}
              onClose={onClose}
            />
            <Stack
              direction="row"
              spacing={1}
              useFlexGap
              sx={{ mb: 2, alignItems: 'center', flexWrap: 'wrap' }}
            >
              <IocStatusChip status={ioc.status} />
              <TlpChip tlp={ioc.tlp} />
              {isRevoked && (
                <Chip icon={<LockIcon />} label="Lecture seule" size="small" color="default" />
              )}
            </Stack>

            {revokeMutation.isError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {problemDetail(revokeMutation.error, 'Révocation impossible.')}
              </Alert>
            )}

            {canWrite && !isRevoked && (
              <Box sx={{ mb: 2 }}>
                <ActionCard
                  icon={<BlockIcon />}
                  title="Révoquer"
                  description="Retirer cet indicateur de l'enrichissement"
                  color={severityColors.critical}
                  onClick={() => setRevokeOpen(true)}
                />
              </Box>
            )}

            {canWrite && (
              <>
                <Divider sx={{ mb: 2 }} />
                <SectionLabel color="#2f81f7">Réputation VirusTotal</SectionLabel>
                {ioc.type === 'EMAIL' ? (
                  <MutedText>VirusTotal n'analyse pas les adresses e-mail.</MutedText>
                ) : (
                  <Box sx={{ mb: 2 }}>
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={<TravelExploreIcon />}
                      disabled={reputationMutation.isPending}
                      onClick={() => reputationMutation.mutate()}
                    >
                      {reputationMutation.isPending ? 'Interrogation…' : 'Vérifier la réputation'}
                    </Button>
                    {reputationMutation.isError && (
                      <Alert severity="error" sx={{ mt: 1 }}>
                        {problemDetail(reputationMutation.error, 'Réputation indisponible.')}
                      </Alert>
                    )}
                    {reputationMutation.data && (
                      <Stack spacing={1} sx={{ mt: 1.5 }}>
                        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                          <ReputationVerdictChip verdict={reputationMutation.data.verdict} />
                          <MutedText>Relevé le {formatDate(reputationMutation.data.checkedAt)}</MutedText>
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          {reputationMutation.data.maliciousCount} malveillant(s) ·{' '}
                          {reputationMutation.data.suspiciousCount} suspect(s) ·{' '}
                          {reputationMutation.data.harmlessCount} inoffensif(s) ·{' '}
                          {reputationMutation.data.undetectedCount} non détecté(s)
                        </Typography>
                      </Stack>
                    )}
                  </Box>
                )}
              </>
            )}

            <DetailField label="Confiance" color={severityColors.medium}>
              <ConfidenceBar confidence={ioc.confidence} />
            </DetailField>
            <DetailField label="Source">
              <Typography variant="body2">{ioc.feedSource}</Typography>
            </DetailField>
            {ioc.externalId && (
              <DetailField label="Identifiant externe">
                <MutedText>{ioc.externalId}</MutedText>
              </DetailField>
            )}
            {ioc.description && (
              <DetailField label="Description">
                <Typography variant="body2">{ioc.description}</Typography>
              </DetailField>
            )}
            {ioc.tags.length > 0 && (
              <DetailField label="Tags">
                <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
                  {ioc.tags.map((tag) => (
                    <Chip key={tag} label={tag} size="small" variant="outlined" />
                  ))}
                </Stack>
              </DetailField>
            )}
            <DetailField label="Première observation">
              <MutedText>{formatDate(ioc.firstSeen)}</MutedText>
            </DetailField>
            <DetailField label="Dernière observation">
              <MutedText>{formatDate(ioc.lastSeen)}</MutedText>
            </DetailField>
            <DetailField label="Valide jusqu'à">
              <MutedText>{ioc.validUntil ? formatDate(ioc.validUntil) : 'Sans péremption'}</MutedText>
            </DetailField>
            {isRevoked && ioc.revocationReason && (
              <DetailField label="Motif de révocation" color={severityColors.critical}>
                <Typography variant="body2">
                  {ioc.revocationReason}
                  {ioc.revokedAt ? ` — ${formatDate(ioc.revokedAt)}` : ''}
                </Typography>
              </DetailField>
            )}

            <Divider sx={{ my: 2 }} />
            <SectionLabel color={severityColors.critical}>
              Alertes citant cet indicateur{correlated ? ` (${correlated.totalElements})` : ''}
            </SectionLabel>
            {correlated && correlated.items.length === 0 && (
              <MutedText>Aucune alerte ne cite cet indicateur.</MutedText>
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

      <Dialog open={revokeOpen} onClose={() => setRevokeOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Révoquer l'indicateur</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Un indicateur révoqué n'enrichit plus aucune alerte. La décision survit aux
            ré-observations du flux ; le motif est conservé pour la traçabilité.
          </Typography>
          <TextField
            label="Motif"
            fullWidth
            required
            multiline
            minRows={2}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Ex. plage d'IP interne légitime — faux positif confirmé"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRevokeOpen(false)}>Annuler</Button>
          <Button
            color="error"
            variant="contained"
            disabled={reason.trim() === '' || revokeMutation.isPending}
            onClick={() => revokeMutation.mutate()}
          >
            Révoquer
          </Button>
        </DialogActions>
      </Dialog>
    </Drawer>
  );
}

export default IocDetailDrawer;
