import { useEffect, useState } from 'react';
import axios from 'axios';
import BlockIcon from '@mui/icons-material/Block';
import DnsOutlinedIcon from '@mui/icons-material/DnsOutlined';
import EditIcon from '@mui/icons-material/Edit';
import LockIcon from '@mui/icons-material/Lock';
import PowerSettingsNewOutlinedIcon from '@mui/icons-material/PowerSettingsNewOutlined';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import Stack from '@mui/material/Stack';
import TablePagination from '@mui/material/TablePagination';
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
import {
  AgentConnectionStatusChip,
  AssetStatusChip,
  ExposureChip,
  ASSET_TYPE_LABELS,
} from './assetChips';
import BlockIpDialog from './BlockIpDialog';
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
  const [blockIpOpen, setBlockIpOpen] = useState(false);
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
            <DetailDrawerHeader
              color={severityColors[asset.criticality.toLowerCase() as keyof typeof severityColors]}
              icon={<DnsOutlinedIcon sx={{ fontSize: 26 }} />}
              title={asset.displayName}
              onClose={onClose}
            />
            <Stack
              direction="row"
              spacing={1}
              useFlexGap
              sx={{ mb: 2, alignItems: 'center', flexWrap: 'wrap' }}
            >
              <ExposureChip exposure={asset.exposure} />
              <AssetStatusChip status={asset.status} />
              <AgentConnectionStatusChip status={asset.agentConnectionStatus} />
              {isDecommissioned && (
                <Chip icon={<LockIcon />} label="Lecture seule" size="small" color="default" />
              )}
            </Stack>

            {mutationError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {problemDetail(mutationError, 'Action impossible.')}
              </Alert>
            )}

            {canWrite && (
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
                  gap: 1.25,
                  mb: 2,
                }}
              >
                {!isDecommissioned && (
                  <>
                    <ActionCard
                      icon={<EditIcon />}
                      title="Modifier"
                      description="Mettre à jour la fiche de l'actif"
                      color="#2f81f7"
                      onClick={() => setEditOpen(true)}
                    />
                    <ActionCard
                      icon={<PowerSettingsNewOutlinedIcon />}
                      title="Décommissionner"
                      description="Retirer cet actif du parc actif"
                      color={severityColors.critical}
                      disabled={decommissionMutation.isPending}
                      onClick={() => decommissionMutation.mutate()}
                    />
                    {asset.externalSource === 'wazuh' && (
                      <ActionCard
                        icon={<RestartAltIcon />}
                        title="Redémarrer l'agent"
                        description="Redémarrer l'agent Wazuh à distance"
                        color={severityColors.high}
                        onClick={() => setRestartOpen(true)}
                      />
                    )}
                    {asset.externalSource === 'wazuh' && (
                      <ActionCard
                        icon={<BlockIcon />}
                        title="Bloquer une IP"
                        description="Bloquer une IP sur le pare-feu local"
                        color={severityColors.high}
                        onClick={() => setBlockIpOpen(true)}
                      />
                    )}
                  </>
                )}
                {isDecommissioned && (
                  <ActionCard
                    icon={<PowerSettingsNewOutlinedIcon />}
                    title="Réactiver"
                    description="Remettre cet actif en service"
                    color={severityColors.low}
                    disabled={reactivateMutation.isPending}
                    onClick={() => reactivateMutation.mutate()}
                  />
                )}
              </Box>
            )}

            <DetailField label="Type">
              <Typography variant="body2">{ASSET_TYPE_LABELS[asset.type]}</Typography>
            </DetailField>
            {asset.ipAddress && (
              <DetailField label="Adresse IP">
                <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace', opacity: 0.85 }}>
                  {asset.ipAddress}
                </Typography>
              </DetailField>
            )}
            {asset.owner && (
              <DetailField label="Propriétaire">
                <Typography variant="body2">{asset.owner}</Typography>
              </DetailField>
            )}
            {asset.operatingSystem && (
              <DetailField label="Système d'exploitation">
                <Typography variant="body2">{asset.operatingSystem}</Typography>
              </DetailField>
            )}
            {asset.hardwareSummary && (
              <DetailField label="Matériel">
                <Typography variant="body2">{asset.hardwareSummary}</Typography>
              </DetailField>
            )}
            {asset.lastSeenAt && (
              <DetailField label="Dernier contact">
                <MutedText>{formatDate(asset.lastSeenAt)}</MutedText>
              </DetailField>
            )}
            {asset.description && (
              <DetailField label="Description">
                <Typography variant="body2">{asset.description}</Typography>
              </DetailField>
            )}
            <DetailField label="Inventorié le">
              <MutedText>{formatDate(asset.registeredAt)}</MutedText>
            </DetailField>
            {asset.decommissionedAt && (
              <DetailField label="Décommissionné le">
                <MutedText>{formatDate(asset.decommissionedAt)}</MutedText>
              </DetailField>
            )}

            <Divider sx={{ my: 2 }} />
            <SectionLabel color={severityColors.critical}>
              Alertes corrélées{correlated ? ` (${correlated.totalElements})` : ''}
            </SectionLabel>
            {correlated && correlated.items.length === 0 && (
              <MutedText>Aucune alerte pour ce hostname.</MutedText>
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
            <SectionLabel color={severityColors.high}>
              Vulnérabilités{vulnerabilities ? ` (${vulnerabilities.totalElements})` : ''}
            </SectionLabel>
            {vulnerabilities && vulnerabilities.items.length === 0 && (
              <MutedText>Aucune vulnérabilité connue pour cet actif.</MutedText>
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
            {canWrite && !isDecommissioned && asset.externalSource === 'wazuh' && (
              <BlockIpDialog
                key={`block-ip-${asset.id}`}
                asset={asset}
                open={blockIpOpen}
                onClose={() => setBlockIpOpen(false)}
              />
            )}
          </>
        )}
      </Box>
    </Drawer>
  );
}

export default AssetDetailDrawer;
