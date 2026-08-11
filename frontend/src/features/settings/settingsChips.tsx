import Chip from '@mui/material/Chip';
import { useTheme } from '@mui/material/styles';
import {
  resolveChipColor,
  softChipSx,
  type ChipPaletteColor,
} from '../../shared/components/chipStyles';
import type { AiServiceHealthStatus, AuditAction, ConnectorStatus } from './settingsApi';

const STATUS_LABELS: Record<AiServiceHealthStatus, string> = {
  UP: 'Disponible',
  DOWN: 'Indisponible',
  NOT_CONFIGURED: 'Non configuré',
};

const STATUS_COLORS: Record<AiServiceHealthStatus, ChipPaletteColor> = {
  UP: 'success',
  DOWN: 'error',
  NOT_CONFIGURED: 'default',
};

export function ServiceStatusChip({ status }: { status: AiServiceHealthStatus }) {
  const theme = useTheme();
  const color = resolveChipColor(theme, STATUS_COLORS[status]);
  return <Chip label={STATUS_LABELS[status]} size="small" sx={softChipSx(color)} />;
}

export function ModeChip({ mode }: { mode: 'simulation' | 'live' }) {
  const theme = useTheme();
  const color = resolveChipColor(theme, mode === 'live' ? 'success' : 'secondary');
  return (
    <Chip
      label={mode === 'live' ? 'Réel (live)' : 'Simulation'}
      size="small"
      sx={softChipSx(color)}
    />
  );
}

export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  LOGIN_SUCCEEDED: 'Connexion réussie',
  LOGIN_FAILED: 'Connexion refusée',
  USER_CREATED: 'Utilisateur créé',
  USER_UPDATED: 'Utilisateur modifié',
  USER_ROLE_CHANGED: 'Rôle modifié',
  USER_ENABLED: 'Utilisateur activé',
  USER_DISABLED: 'Utilisateur désactivé',
  USER_DELETED: 'Utilisateur supprimé',
  BACKUP_EXPORTED: 'Sauvegarde exportée',
  WAZUH_AGENT_RESTART_REQUESTED: "Redémarrage d'agent demandé",
  WAZUH_AGENT_FIREWALL_DROP_REQUESTED: 'Blocage IP demandé',
  SHUFFLE_WORKFLOW_TRIGGER_REQUESTED: 'Déclenchement workflow Shuffle',
};

const AUDIT_ACTION_COLORS: Record<AuditAction, ChipPaletteColor> = {
  LOGIN_SUCCEEDED: 'success',
  LOGIN_FAILED: 'error',
  USER_CREATED: 'success',
  USER_UPDATED: 'info',
  USER_ROLE_CHANGED: 'warning',
  USER_ENABLED: 'success',
  USER_DISABLED: 'warning',
  USER_DELETED: 'error',
  BACKUP_EXPORTED: 'secondary',
  WAZUH_AGENT_RESTART_REQUESTED: 'warning',
  WAZUH_AGENT_FIREWALL_DROP_REQUESTED: 'error',
  SHUFFLE_WORKFLOW_TRIGGER_REQUESTED: 'info',
};

export function AuditActionChip({ action }: { action: AuditAction }) {
  const theme = useTheme();
  const color = resolveChipColor(theme, AUDIT_ACTION_COLORS[action]);
  return <Chip label={AUDIT_ACTION_LABELS[action]} size="small" sx={softChipSx(color)} />;
}

const CONNECTOR_STATUS_LABELS: Record<ConnectorStatus, string> = {
  NOT_CONFIGURED: 'Non configuré',
  DISABLED: 'Désactivé',
  CONNECTED: 'Connecté',
  DEGRADED: 'Dégradé',
  DISCONNECTED: 'Déconnecté',
};

const CONNECTOR_STATUS_COLORS: Record<ConnectorStatus, ChipPaletteColor> = {
  NOT_CONFIGURED: 'default',
  DISABLED: 'default',
  CONNECTED: 'success',
  DEGRADED: 'warning',
  DISCONNECTED: 'error',
};

export function ConnectorStatusChip({ status }: { status: ConnectorStatus }) {
  const theme = useTheme();
  const color = resolveChipColor(theme, CONNECTOR_STATUS_COLORS[status]);
  return <Chip label={CONNECTOR_STATUS_LABELS[status]} size="small" sx={softChipSx(color)} />;
}

export function ConfiguredChip({ configured }: { configured: boolean }) {
  const theme = useTheme();
  const color = resolveChipColor(theme, configured ? 'success' : 'default');
  return (
    <Chip label={configured ? 'Configuré' : 'Non configuré'} size="small" sx={softChipSx(color)} />
  );
}
