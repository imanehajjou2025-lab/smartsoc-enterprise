import { useEffect, useState } from 'react';
import axios from 'axios';
import EditIcon from '@mui/icons-material/Edit';
import LockIcon from '@mui/icons-material/Lock';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
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
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import { SeverityChip, StatusChip } from '../alerts/chips';
import {
  AgentConnectionStatusChip,
  AssetStatusChip,
  CriticalityChip,
  ExposureChip,
  ASSET_TYPE_LABELS,
} from './assetChips';
import EditAssetDialog from './EditAssetDialog';
import RestartAgentDialog from './RestartAgentDialog';
import { decommissionAsset, getAsset, listCorrelatedAlerts, reactivateAsset } from './assetsApi';
import { VulnerabilitySeverityChip } from '../vulnerabilities/vulnerabilityChips';
import { listVulnerabilities } from '../vulnerabilities/vulnerabilitiesApi';

interface Props {
  assetId: string | null;
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
 * Fiche d'un actif : identité, classification, actions d'état (un actif
 * décommissionné est en lecture seule — mais son historique de
 * corrélation reste consultable) et alertes corrélées paginées, dont le
 * totalElements EST le compteur de corrélation.
 */
function AssetDetailDrawer({ assetId, onClose }: Props) {
  const queryClient = useQueryClient();
  const role = useAppSelector((state) => state.auth.user?.role);
  const canWrite = role === 'ADMIN' || role === 'SOC_MANAGER' || role === 'SOC_ANALYST';
  const [editOpen, setEditOpen] = useState(false);
  const [restartOpen, setRestartOpen] = useState(false);
  const [alertsPage, setAlertsPage] = useState(0);
  const [vulnsPage, setVulnsPage] = useState(0);

  const {
    data: asset,
    isPending,
    isError,
    error,
  } = useQuery({
    queryKey: ['asset', assetId],
    queryFn: () => getAsset(assetId!),
    enabled: Boolean(assetId),
    retry: (failureCount, err) =>
      !(axios.isAxiosError(err) && err.response?.status === 404) && failureCount < 2,
  });

  // Id inconnu (lien profond périmé ou partagé à tort) : comportement
  // propre — le tiroir se referme sans message, la liste reste.
  const notFound = isError && axios.isAxiosError(error) && error.response?.status === 404;
  useEffect(() => {
    if (notFound) {
      onClose();
    }
  }, [notFound, onClose]);

  const { data: correlated } = useQuery({
    queryKey: ['asset', assetId, 'alerts', alertsPage],
    queryFn: () => listCorrelatedAlerts(assetId!, alertsPage, 10),
    enabled: Boolean(assetId),
    placeholderData: keepPreviousData,
  });

  const { data: vulnerabilities } = useQuery({
    queryKey: ['asset', assetId, 'vulnerabilities', vulnsPage],
    queryFn: () => listVulnerabilities({ assetId: assetId!, page: vulnsPage, size: 10 }),
    enabled: Boolean(assetId),
    placeholderData: keepPreviousData,
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
    void queryClient.invalidateQueries({ queryKey: ['assets'] });
  };

  const decommissionMutation = useMutation({
    mutationFn: () => decommissionAsset(assetId!),
    onSuccess: invalidate,
  });
  const reactivateMutation = useMutation({
    mutationFn: () => reactivateAsset(assetId!),
    onSuccess: invalidate,
  });

  const mutationError = decommissionMutation.error ?? reactivateMutation.error;
  const isDecommissioned = asset?.status === 'DECOMMISSIONED';

  return (
    <Drawer anchor="right" open={Boolean(assetId)} onClose={onClose}>
      <Box sx={{ width: 560, maxWidth: '92vw', p: 3 }}>
        {isPending && (
          <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
            <CircularProgress />
          </Box>
        )}
        {isError && !notFound && (
          <Alert severity="error">{problemDetail(error, 'Chargement impossible.')}</Alert>
        )}

        {asset && (
          <>
            <Stack
              direction="row"
              spacing={1}
              useFlexGap
              sx={{ mb: 1, alignItems: 'center', flexWrap: 'wrap' }}
            >
              <Typography variant="subtitle2" sx={{ fontFamily: 'monospace' }}>
                {asset.hostname}
              </Typography>
              <CriticalityChip criticality={asset.criticality} />
              <ExposureChip exposure={asset.exposure} />
              <AssetStatusChip status={asset.status} />
              <AgentConnectionStatusChip status={asset.agentConnectionStatus} />
              {isDecommissioned && (
                <Chip icon={<LockIcon />} label="Lecture seule" size="small" color="default" />
              )}
            </Stack>
            <Typography variant="h6" sx={{ mb: 2 }}>
              {asset.displayName}
            </Typography>

            {mutationError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {problemDetail(mutationError, 'Action impossible.')}
              </Alert>
            )}

            {canWrite && (
              <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
                {!isDecommissioned && (
                  <>
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={<EditIcon />}
                      onClick={() => setEditOpen(true)}
                    >
                      Modifier
                    </Button>
                    <Button
                      size="small"
                      variant="outlined"
                      color="error"
                      disabled={decommissionMutation.isPending}
                      onClick={() => decommissionMutation.mutate()}
                    >
                      Décommissionner
                    </Button>
                    {asset.externalSource === 'wazuh' && (
                      <Button
                        size="small"
                        variant="outlined"
                        color="warning"
                        startIcon={<RestartAltIcon />}
                        onClick={() => setRestartOpen(true)}
                      >
                        Redémarrer l'agent
                      </Button>
                    )}
                  </>
                )}
                {isDecommissioned && (
                  <Button
                    size="small"
                    variant="outlined"
                    disabled={reactivateMutation.isPending}
                    onClick={() => reactivateMutation.mutate()}
                  >
                    Réactiver
                  </Button>
                )}
              </Stack>
            )}

            <Field label="Type">
              <Typography variant="body2">{ASSET_TYPE_LABELS[asset.type]}</Typography>
            </Field>
            {asset.ipAddress && (
              <Field label="Adresse IP">
                <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                  {asset.ipAddress}
                </Typography>
              </Field>
            )}
            {asset.owner && (
              <Field label="Propriétaire">
                <Typography variant="body2">{asset.owner}</Typography>
              </Field>
            )}
            {asset.operatingSystem && (
              <Field label="Système d'exploitation">
                <Typography variant="body2">{asset.operatingSystem}</Typography>
              </Field>
            )}
            {asset.hardwareSummary && (
              <Field label="Matériel">
                <Typography variant="body2">{asset.hardwareSummary}</Typography>
              </Field>
            )}
            {asset.lastSeenAt && (
              <Field label="Dernier contact">
                <Typography variant="body2">{formatDate(asset.lastSeenAt)}</Typography>
              </Field>
            )}
            {asset.description && (
              <Field label="Description">
                <Typography variant="body2">{asset.description}</Typography>
              </Field>
            )}
            <Field label="Inventorié le">
              <Typography variant="body2">{formatDate(asset.registeredAt)}</Typography>
            </Field>
            {asset.decommissionedAt && (
              <Field label="Décommissionné le">
                <Typography variant="body2">{formatDate(asset.decommissionedAt)}</Typography>
              </Field>
            )}

            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Alertes corrélées{correlated ? ` (${correlated.totalElements})` : ''}
            </Typography>
            {correlated && correlated.items.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                Aucune alerte pour ce hostname.
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

            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Vulnérabilités{vulnerabilities ? ` (${vulnerabilities.totalElements})` : ''}
            </Typography>
            {vulnerabilities && vulnerabilities.items.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                Aucune vulnérabilité connue pour cet actif.
              </Typography>
            )}
            {vulnerabilities?.items.map((vuln) => (
              <Stack
                key={vuln.id}
                direction="row"
                spacing={1}
                sx={{ alignItems: 'center', mb: 0.5 }}
              >
                <VulnerabilitySeverityChip severity={vuln.severity} />
                <Typography variant="body2" sx={{ flexGrow: 1 }} noWrap>
                  {vuln.cveId}
                  {vuln.packageName ? ` — ${vuln.packageName}` : ''}
                </Typography>
                {vuln.status === 'RESOLVED' && (
                  <Chip label="Résolue" size="small" variant="outlined" />
                )}
                <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
                  {formatDate(vuln.lastSeenAt)}
                </Typography>
              </Stack>
            ))}
            {vulnerabilities && vulnerabilities.totalElements > 10 && (
              <TablePagination
                component="div"
                count={vulnerabilities.totalElements}
                page={vulnsPage}
                onPageChange={(_, newPage) => setVulnsPage(newPage)}
                rowsPerPage={10}
                rowsPerPageOptions={[10]}
                labelRowsPerPage=""
              />
            )}

            {canWrite && !isDecommissioned && (
              <EditAssetDialog
                key={`edit-${asset.id}-${editOpen}`}
                asset={asset}
                open={editOpen}
                onClose={() => setEditOpen(false)}
              />
            )}
            {canWrite && !isDecommissioned && asset.externalSource === 'wazuh' && (
              <RestartAgentDialog
                key={`restart-${asset.id}`}
                asset={asset}
                open={restartOpen}
                onClose={() => setRestartOpen(false)}
              />
            )}
          </>
        )}
      </Box>
    </Drawer>
  );
}

export default AssetDetailDrawer;
