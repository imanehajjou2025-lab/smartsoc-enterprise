import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import BugReportOutlinedIcon from '@mui/icons-material/BugReportOutlined';
import HubOutlinedIcon from '@mui/icons-material/HubOutlined';
import ManageSearchOutlinedIcon from '@mui/icons-material/ManageSearchOutlined';
import ScheduleOutlinedIcon from '@mui/icons-material/ScheduleOutlined';
import SecurityOutlinedIcon from '@mui/icons-material/SecurityOutlined';
import { useQuery } from '@tanstack/react-query';
import SettingsCard, { SettingsRow } from '../../../shared/components/SettingsCard';
import { severityColors } from '../../../app/theme';
import { problemDetail } from '../../../shared/api/client';
import { ConnectorStatusChip } from '../settingsChips';
import {
  listConnectors,
  type ConnectorOverview,
  type ConnectorStatus,
  type ConnectorType,
} from '../settingsApi';

const CONNECTOR_STATUS_ACCENT: Record<ConnectorStatus, string> = {
  NOT_CONFIGURED: severityColors.info,
  DISABLED: severityColors.info,
  CONNECTED: severityColors.low,
  DEGRADED: severityColors.medium,
  DISCONNECTED: severityColors.critical,
};

const CAPABILITY_LABELS: Record<string, string> = {
  AGENT_INVENTORY: "Inventaire d'agents",
  SYSTEM_INVENTORY: 'Inventaire système',
  MANAGER_STATS: 'Statistiques du gestionnaire',
  VULNERABILITY_FEED: 'Flux de vulnérabilités',
  AGENT_CONTROL: 'Contrôle des agents',
  EVENT_SEARCH: "Recherche d'événements",
  THREAT_INTEL: 'Threat intelligence',
  OBSERVABLE_REPUTATION: "Réputation d'observables",
  WORKFLOW_TRIGGER: 'Déclenchement de workflow',
  WORKFLOW_STATUS: 'Statut de workflow',
};

interface ConnectorMeta {
  type: ConnectorType;
  label: string;
  description: string;
  icon: React.ReactNode;
  /** Seul un connecteur dont l'adaptateur existe côté backend peut avoir une ligne API. */
  implemented: boolean;
  plannedPhase: string;
}

const CONNECTORS: ConnectorMeta[] = [
  {
    type: 'WAZUH',
    label: 'Wazuh',
    description: 'Agents, santé du gestionnaire et inventaire système (ADR-014).',
    icon: <SecurityOutlinedIcon />,
    implemented: true,
    plannedPhase: 'Phase 1.2',
  },
  {
    type: 'OPENSEARCH',
    label: 'OpenSearch',
    description:
      'Vulnérabilités (Indexer Wazuh) — recherche d’événements (Threat Hunting) à venir.',
    icon: <ManageSearchOutlinedIcon />,
    implemented: true,
    plannedPhase: 'Phase 4',
  },
  {
    type: 'MISP',
    label: 'MISP',
    description: "Threat intelligence — alimente le modèle d'indicateurs existant.",
    icon: <HubOutlinedIcon />,
    implemented: true,
    plannedPhase: 'Phase 2',
  },
  {
    type: 'VIRUSTOTAL',
    label: 'VirusTotal',
    description: "Réputation d'observables (hash, IP, domaine), à la demande d'un analyste.",
    icon: <BugReportOutlinedIcon />,
    implemented: true,
    plannedPhase: 'Phase 3',
  },
  {
    type: 'SHUFFLE',
    label: 'Shuffle',
    description: 'Orchestration SOAR — déclenchement et suivi de workflows.',
    icon: <AccountTreeOutlinedIcon />,
    implemented: true,
    plannedPhase: 'Phase 5',
  },
];

function formatDate(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
}

const OUTCOME_LABELS: Record<string, string> = {
  SUCCESS: 'Succès',
  PARTIAL: 'Partiel',
  FAILURE: 'Échec',
};

function ImplementedConnectorCard({
  meta,
  overview,
}: {
  meta: ConnectorMeta;
  overview: ConnectorOverview | undefined;
}) {
  if (!overview) {
    return (
      <SettingsCard
        title={meta.label}
        icon={meta.icon}
        color={severityColors.info}
        description={meta.description}
        statusChip={<Chip label="En attente du premier cycle" size="small" variant="outlined" />}
      >
        <Typography variant="body2" color="text.secondary">
          Aucune synchronisation n'a encore été exécutée depuis le démarrage de la plateforme.
        </Typography>
      </SettingsCard>
    );
  }

  return (
    <SettingsCard
      title={meta.label}
      icon={meta.icon}
      color={CONNECTOR_STATUS_ACCENT[overview.status]}
      description={meta.description}
      statusChip={<ConnectorStatusChip status={overview.status} />}
    >
      <SettingsRow label="Version détectée" value={overview.detectedVersion ?? 'Non détectée'} />
      <SettingsRow
        label="Capacités"
        value={
          overview.capabilities.length === 0 ? (
            'Aucune capacité confirmée pour l’instant'
          ) : (
            <Stack
              direction="row"
              spacing={0.5}
              sx={{ justifyContent: 'flex-end', flexWrap: 'wrap' }}
            >
              {overview.capabilities.map((capability) => (
                <Chip
                  key={capability}
                  label={CAPABILITY_LABELS[capability] ?? capability}
                  size="small"
                  variant="outlined"
                />
              ))}
            </Stack>
          )
        }
      />
      <SettingsRow label="Dernière sonde" value={formatDate(overview.lastCheckedAt)} />
      <SettingsRow
        label="Dernière synchronisation réussie"
        value={formatDate(overview.lastSuccessfulSyncAt)}
      />
      {overview.lastSync && (
        <SettingsRow
          label="Dernier cycle"
          value={`${overview.lastSync.outcome ? OUTCOME_LABELS[overview.lastSync.outcome] : '—'} · ${overview.lastSync.itemsProcessed} traités · ${overview.lastSync.itemsRejected} rejetés`}
        />
      )}
      {overview.lastError && (
        <Alert
          severity={overview.status === 'DEGRADED' ? 'warning' : 'error'}
          sx={{ mt: 1.5 }}
          variant="outlined"
        >
          {overview.lastError}
        </Alert>
      )}
    </SettingsCard>
  );
}

function PlannedConnectorCard({ meta }: { meta: ConnectorMeta }) {
  return (
    <SettingsCard
      title={meta.label}
      icon={meta.icon}
      color={severityColors.info}
      description={meta.description}
      statusChip={
        <Chip
          icon={<ScheduleOutlinedIcon fontSize="small" />}
          label={meta.plannedPhase}
          size="small"
          variant="outlined"
        />
      }
    >
      <Typography variant="body2" color="text.secondary">
        Pas encore intégré à la plateforme — cette carte n'affichera de vraies données que le jour
        où ce connecteur sera branché.
      </Typography>
    </SettingsCard>
  );
}

/**
 * État réel des connecteurs SOC (ADR-014). Les cinq connecteurs ont
 * désormais un adaptateur backend — `PlannedConnectorCard` reste en
 * réserve pour un futur connecteur sans adaptateur, plutôt qu'une
 * ligne NOT_CONFIGURED trompeuse.
 */
function ConnectorsSection() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['settings-connectors'],
    queryFn: listConnectors,
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
        <CircularProgress size={28} />
      </Box>
    );
  }
  if (error || !data) {
    return <Alert severity="error">{problemDetail(error, 'Connecteurs indisponibles')}</Alert>;
  }

  const byType = new Map(data.map((overview) => [overview.type, overview]));

  return (
    <Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        État réel de l'intégration de la plateforme aux outils SOC (ADR-014). Une désactivation ou
        une panne d'un connecteur ne bloque jamais la console — seules les données qu'il apporte
        deviennent indisponibles.
      </Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
        {CONNECTORS.map((meta) =>
          meta.implemented ? (
            <ImplementedConnectorCard
              key={meta.type}
              meta={meta}
              overview={byType.get(meta.type)}
            />
          ) : (
            <PlannedConnectorCard key={meta.type} meta={meta} />
          ),
        )}
      </Box>
    </Box>
  );
}

export default ConnectorsSection;
