import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import SecurityOutlinedIcon from '@mui/icons-material/SecurityOutlined';
import VpnKeyOutlinedIcon from '@mui/icons-material/VpnKeyOutlined';
import { useQuery } from '@tanstack/react-query';
import SettingsCard, { SettingsRow } from '../../../shared/components/SettingsCard';
import { severityColors } from '../../../app/theme';
import { problemDetail } from '../../../shared/api/client';
import { ConfiguredChip } from '../settingsChips';
import { getSecuritySettings } from '../settingsApi';

function SecuritySection() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['settings-security'],
    queryFn: getSecuritySettings,
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
        <CircularProgress size={28} />
      </Box>
    );
  }
  if (error || !data) {
    return (
      <Alert severity="error">{problemDetail(error, 'Réglages de sécurité indisponibles')}</Alert>
    );
  }

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
      <SettingsCard
        title="Jetons d'authentification (JWT)"
        description="Durées de validité configurées côté backend — non modifiables depuis cette console (ADR-005), la variable d'environnement reste la seule source de vérité."
        icon={<SecurityOutlinedIcon />}
        color={severityColors.critical}
      >
        <SettingsRow label="Jeton d'accès" value={`${data.jwtAccessTokenExpirationMinutes} min`} />
        <SettingsRow
          label="Jeton de rafraîchissement"
          value={`${data.jwtRefreshTokenExpirationDays} j`}
        />
      </SettingsCard>
      <SettingsCard
        title="Clés d'API sensibles"
        description="Présence de la clé, jamais sa valeur — les secrets ne transitent pas par cette interface."
        icon={<VpnKeyOutlinedIcon />}
        color={severityColors.high}
      >
        <SettingsRow
          label="Webhook d'ingestion (SOC)"
          value={<ConfiguredChip configured={data.ingestWebhookConfigured} />}
        />
        <SettingsRow
          label="Outils de l'assistant IA"
          value={<ConfiguredChip configured={data.aiToolsApiKeyConfigured} />}
        />
        {!data.ingestWebhookConfigured && (
          <Alert severity="warning" sx={{ mt: 1.5 }} variant="outlined">
            L'ingestion d'alertes par webhook est désactivée tant qu'aucune clé n'est configurée.
          </Alert>
        )}
      </SettingsCard>
    </Box>
  );
}

export default SecuritySection;
