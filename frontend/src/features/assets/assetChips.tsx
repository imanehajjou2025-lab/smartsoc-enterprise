import Chip from '@mui/material/Chip';
import { useTheme } from '@mui/material/styles';
import PublicIcon from '@mui/icons-material/Public';
import { severityColors } from '../../app/theme';
import { softChipSx } from '../../shared/components/chipStyles';
import type { AssetCriticality, AssetExposure, AssetStatus, AssetType } from './assetsApi';

export const ASSET_TYPE_LABELS: Record<AssetType, string> = {
  SERVER: 'Serveur',
  WORKSTATION: 'Poste de travail',
  NETWORK_DEVICE: 'Équipement réseau',
  DATABASE: 'Base de données',
  APPLICATION: 'Application',
  CLOUD_RESOURCE: 'Ressource cloud',
  OTHER: 'Autre',
};

export const ASSET_STATUS_LABELS: Record<AssetStatus, string> = {
  ACTIVE: 'Actif',
  DECOMMISSIONED: 'Décommissionné',
};

export const EXPOSURE_LABELS: Record<AssetExposure, string> = {
  INTERNET_FACING: 'Exposé Internet',
  INTERNAL: 'Interne',
  ISOLATED: 'Isolé',
};

/**
 * Criticité d'un actif : composant DÉDIÉ (concept distinct de la
 * sévérité d'une alerte) mais couleurs issues de la même palette
 * centralisée — un CRITICAL se lit pareil partout dans la console.
 */
export function CriticalityChip({ criticality }: { criticality: AssetCriticality }) {
  const color = severityColors[criticality.toLowerCase() as 'critical' | 'high' | 'medium' | 'low'];
  return <Chip label={criticality} size="small" sx={{ ...softChipSx(color), minWidth: 82 }} />;
}

/**
 * Exposition : INTERNET_FACING est l'information qui fait réagir un
 * analyste — chip pleine, couleur d'alerte, icône. Les autres restent
 * discrètes.
 */
export function ExposureChip({ exposure }: { exposure: AssetExposure }) {
  if (exposure === 'INTERNET_FACING') {
    return (
      <Chip
        icon={<PublicIcon />}
        label={EXPOSURE_LABELS[exposure]}
        size="small"
        color="error"
        sx={{ fontWeight: 600 }}
      />
    );
  }
  return <Chip label={EXPOSURE_LABELS[exposure]} size="small" variant="outlined" />;
}

export function AssetStatusChip({ status }: { status: AssetStatus }) {
  const theme = useTheme();
  const color = status === 'ACTIVE' ? theme.palette.success.main : theme.palette.text.secondary;
  return <Chip label={ASSET_STATUS_LABELS[status]} size="small" sx={softChipSx(color)} />;
}
