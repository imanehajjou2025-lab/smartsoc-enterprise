import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Chip from '@mui/material/Chip';
import DnsOutlinedIcon from '@mui/icons-material/DnsOutlined';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import SupportAgentOutlinedIcon from '@mui/icons-material/SupportAgentOutlined';
import { useTheme } from '@mui/material/styles';
import { useQuery } from '@tanstack/react-query';
import SettingsCard from '../../../shared/components/SettingsCard';
import { resolveChipColor, softChipSx } from '../../../shared/components/chipStyles';
import { problemDetail } from '../../../shared/api/client';
import { ServiceStatusChip } from '../settingsChips';
import { getAiSettings, getPlatformHealth } from '../settingsApi';

function PlatformStatusChip({ status }: { status: 'UP' | 'DOWN' | 'UNKNOWN' }) {
  const theme = useTheme();
  const color = resolveChipColor(theme, status === 'UP' ? 'success' : 'error');
  return (
    <Chip
      label={status === 'UP' ? 'Opérationnelle' : status === 'DOWN' ? 'Dégradée' : 'Inconnue'}
      size="small"
      sx={softChipSx(color)}
    />
  );
}

function HealthSection() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['settings-health'],
    queryFn: async () => {
      const [platform, ai] = await Promise.all([getPlatformHealth(), getAiSettings()]);
      return { platform, ai };
    },
    refetchInterval: 30_000,
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
      <Alert severity="error">{problemDetail(error, 'Santé des services indisponible')}</Alert>
    );
  }

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 2 }}>
      <SettingsCard
        title="Backend & base de données"
        icon={<DnsOutlinedIcon />}
        statusChip={<PlatformStatusChip status={data.platform.status} />}
        description="Actualisé automatiquement toutes les 30 secondes."
      />
      <SettingsCard
        title="Classifieur TP/FP"
        icon={<SmartToyOutlinedIcon />}
        statusChip={<ServiceStatusChip status={data.ai.classifier.status} />}
        description={
          data.ai.mode === 'live' ? 'Mode live' : "Mode simulation — ce service n'est pas appelé"
        }
      />
      <SettingsCard
        title="Assistant conversationnel"
        icon={<SupportAgentOutlinedIcon />}
        statusChip={<ServiceStatusChip status={data.ai.assistant.status} />}
        description={
          data.ai.mode === 'live' ? 'Mode live' : "Mode simulation — ce service n'est pas appelé"
        }
      />
    </Box>
  );
}

export default HealthSection;
