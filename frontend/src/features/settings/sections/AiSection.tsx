import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import SupportAgentOutlinedIcon from '@mui/icons-material/SupportAgentOutlined';
import { useQuery } from '@tanstack/react-query';
import SettingsCard, { SettingsRow } from '../../../shared/components/SettingsCard';
import { severityColors } from '../../../app/theme';
import { problemDetail } from '../../../shared/api/client';
import { ConfiguredChip, ModeChip, ServiceStatusChip } from '../settingsChips';
import { getAiSettings, type AiServiceStatus } from '../settingsApi';

function ServiceCard({
  title,
  icon,
  service,
}: {
  title: string;
  icon: React.ReactNode;
  service: AiServiceStatus;
}) {
  return (
    <SettingsCard
      title={title}
      icon={icon}
      color={service.status === 'UP' ? severityColors.low : severityColors.critical}
      statusChip={<ServiceStatusChip status={service.status} />}
    >
      <SettingsRow
        label="Clé configurée"
        value={<ConfiguredChip configured={service.configured} />}
      />
      <SettingsRow
        label="Adresse"
        value={
          <Typography
            component="span"
            variant="body2"
            sx={{ fontFamily: 'monospace', fontWeight: 600 }}
          >
            {service.url}
          </Typography>
        }
      />
      {service.status === 'DOWN' && (
        <Alert severity="warning" sx={{ mt: 1.5 }} variant="outlined">
          Injoignable pour l'instant — les alertes/conversations restent traitées sans IA
          (dégradation gracieuse), aucune donnée n'est bloquée.
        </Alert>
      )}
    </SettingsCard>
  );
}

function AiSection() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['settings-ai'],
    queryFn: getAiSettings,
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
        <CircularProgress size={28} />
      </Box>
    );
  }
  if (error || !data) {
    return <Alert severity="error">{problemDetail(error, 'Réglages IA indisponibles')}</Alert>;
  }

  return (
    <Box>
      <SettingsCard
        title="Mode d'intégration"
        description="simulation = stubs embarqués, plateforme démontrable sans service externe ; live = appels réels aux services IA ci-dessous (ADR-008)."
        icon={<SmartToyOutlinedIcon />}
        color="#8957e5"
        statusChip={<ModeChip mode={data.mode} />}
      />
      <Box
        sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2, mt: 2 }}
      >
        <ServiceCard
          title="Classifieur TP/FP"
          icon={<SmartToyOutlinedIcon />}
          service={data.classifier}
        />
        <ServiceCard
          title="Assistant conversationnel"
          icon={<SupportAgentOutlinedIcon />}
          service={data.assistant}
        />
      </Box>
    </Box>
  );
}

export default AiSection;
