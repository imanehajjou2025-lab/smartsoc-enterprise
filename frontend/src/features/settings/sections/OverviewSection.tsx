import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { alpha } from '@mui/material/styles';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined';
import MonitorHeartOutlinedIcon from '@mui/icons-material/MonitorHeartOutlined';
import NotificationsOutlinedIcon from '@mui/icons-material/NotificationsOutlined';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import { useQuery } from '@tanstack/react-query';
import { problemDetail } from '../../../shared/api/client';
import { listUsers } from '../../admin/usersApi';
import {
  getAiSettings,
  getNotificationsSettings,
  getPlatformHealth,
  listAuditLogs,
} from '../settingsApi';

function KpiTile({
  label,
  value,
  hint,
  color,
  icon,
}: {
  label: string;
  value: string;
  hint: string;
  color: string;
  icon: React.ReactNode;
}) {
  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2,
        display: 'flex',
        gap: 1.75,
        alignItems: 'center',
        borderRadius: 3,
        borderColor: alpha(color, 0.25),
        background: (t) =>
          `linear-gradient(135deg, ${alpha(color, t.palette.mode === 'dark' ? 0.18 : 0.12)} 0%, ${t.palette.background.paper} 70%)`,
      }}
    >
      <Box
        sx={{
          width: 44,
          height: 44,
          borderRadius: '50%',
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color,
          backgroundColor: alpha(color, 0.16),
        }}
      >
        {icon}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ textTransform: 'uppercase', letterSpacing: 0.4, fontWeight: 700 }}
        >
          {label}
        </Typography>
        <Typography variant="h5" sx={{ fontWeight: 800, lineHeight: 1.15, color }}>
          {value}
        </Typography>
        <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
          {hint}
        </Typography>
      </Box>
    </Paper>
  );
}

function startOfTodayIso(): string {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString();
}

function OverviewSection() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['settings-overview'],
    queryFn: async () => {
      const [ai, notifications, users, health, todayAudit] = await Promise.all([
        getAiSettings(),
        getNotificationsSettings(),
        listUsers(),
        getPlatformHealth(),
        listAuditLogs({ from: startOfTodayIso(), page: 0, size: 1 }),
      ]);
      return { ai, notifications, users, health, todayAuditCount: todayAudit.totalElements };
    },
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
        <CircularProgress size={28} />
      </Box>
    );
  }
  if (error || !data) {
    return <Alert severity="error">{problemDetail(error, 'Vue générale indisponible')}</Alert>;
  }

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' },
        gap: 2,
      }}
    >
      <KpiTile
        label="Santé plateforme"
        value={data.health.status === 'UP' ? 'Opérationnelle' : 'Dégradée'}
        hint="Backend + base de données"
        color={data.health.status === 'UP' ? '#3fb950' : '#f85149'}
        icon={<MonitorHeartOutlinedIcon />}
      />
      <KpiTile
        label="Intelligence artificielle"
        value={data.ai.mode === 'live' ? 'Mode réel' : 'Simulation'}
        hint={`Classifieur ${data.ai.classifier.status.toLowerCase()} · Assistant ${data.ai.assistant.status.toLowerCase()}`}
        color="#2f81f7"
        icon={<SmartToyOutlinedIcon />}
      />
      <KpiTile
        label="Utilisateurs"
        value={String(data.users.length)}
        hint={`${data.users.filter((u) => u.enabled).length} compte(s) actif(s)`}
        color="#8957e5"
        icon={<ManageAccountsOutlinedIcon />}
      />
      <KpiTile
        label="Notifications"
        value={data.notifications.mode === 'live' ? 'E-mail réel' : 'Simulation'}
        hint={data.notifications.fromAddress}
        color="#39c5cf"
        icon={<NotificationsOutlinedIcon />}
      />
      <Box sx={{ gridColumn: '1 / -1' }}>
        <Paper
          variant="outlined"
          sx={{ p: 2, borderRadius: 3, display: 'flex', alignItems: 'center', gap: 1.5 }}
        >
          <HistoryOutlinedIcon color="action" />
          <Typography variant="body2" color="text.secondary">
            <strong>{data.todayAuditCount}</strong> action{data.todayAuditCount > 1 ? 's' : ''}{' '}
            sensible{data.todayAuditCount > 1 ? 's' : ''} enregistrée
            {data.todayAuditCount > 1 ? 's' : ''} aujourd'hui dans le journal d'audit.
          </Typography>
        </Paper>
      </Box>
    </Box>
  );
}

export default OverviewSection;
