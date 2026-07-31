import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import { useQuery } from '@tanstack/react-query';
import SettingsCard, { SettingsRow } from '../../../shared/components/SettingsCard';
import { problemDetail } from '../../../shared/api/client';
import { getAboutInfo } from '../settingsApi';

function formatUptime(seconds: number | null): string {
  if (seconds == null) return '—';
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours === 0) return `${minutes} min`;
  return `${hours} h ${minutes} min`;
}

function formatDate(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
}

function AboutSection() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['settings-about'],
    queryFn: getAboutInfo,
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
        {problemDetail(error, 'Informations plateforme indisponibles')}
      </Alert>
    );
  }

  return (
    <SettingsCard title="ISIX Enterprise — SmartSOC" icon={<InfoOutlinedIcon />}>
      <SettingsRow label="Version" value={data.version} />
      <SettingsRow label="Environnement" value={data.activeProfile} />
      <SettingsRow label="Java" value={data.javaVersion} />
      <SettingsRow label="Démarré le" value={formatDate(data.startedAt)} />
      <SettingsRow label="Disponible depuis" value={formatUptime(data.uptimeSeconds)} />
    </SettingsCard>
  );
}

export default AboutSection;
