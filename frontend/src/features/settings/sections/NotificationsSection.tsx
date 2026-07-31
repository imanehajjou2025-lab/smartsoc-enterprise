import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import NotificationsOutlinedIcon from '@mui/icons-material/NotificationsOutlined';
import SendOutlinedIcon from '@mui/icons-material/SendOutlined';
import { useQuery } from '@tanstack/react-query';
import SettingsCard, { SettingsRow } from '../../../shared/components/SettingsCard';
import { problemDetail } from '../../../shared/api/client';
import { ConfiguredChip, ModeChip } from '../settingsChips';
import { getNotificationsSettings } from '../settingsApi';
import TestEmailDialog from './TestEmailDialog';

function NotificationsSection() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const { data, isLoading, error } = useQuery({
    queryKey: ['settings-notifications'],
    queryFn: getNotificationsSettings,
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
      <Alert severity="error">
        {problemDetail(error, 'Réglages de notifications indisponibles')}
      </Alert>
    );
  }

  return (
    <Box>
      <SettingsCard
        title="Notifications par e-mail"
        description="simulation = journalisée seulement, plateforme démontrable sans SMTP ; live = envoi réel (ADR-005)."
        icon={<NotificationsOutlinedIcon />}
        statusChip={<ModeChip mode={data.mode} />}
        actions={
          <Button
            size="small"
            variant="outlined"
            startIcon={<SendOutlinedIcon fontSize="small" />}
            disabled={data.mode === 'simulation'}
            onClick={() => setDialogOpen(true)}
          >
            Tester l'envoi
          </Button>
        }
      >
        <SettingsRow
          label="Adresse d'expédition"
          value={
            <Typography
              component="span"
              variant="body2"
              sx={{ fontFamily: 'monospace', fontWeight: 600 }}
            >
              {data.fromAddress}
            </Typography>
          }
        />
        <SettingsRow
          label="Serveur SMTP"
          value={<ConfiguredChip configured={data.smtpConfigured} />}
        />
        {data.mode === 'simulation' && (
          <Alert severity="info" variant="outlined" sx={{ mt: 1.5 }}>
            Aucun e-mail réel n'est envoyé en mode simulation — le bouton de test n'est disponible
            qu'en mode live.
          </Alert>
        )}
      </SettingsCard>
      <TestEmailDialog open={dialogOpen} onClose={() => setDialogOpen(false)} />
    </Box>
  );
}

export default NotificationsSection;
