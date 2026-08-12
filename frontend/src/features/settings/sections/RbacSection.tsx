import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import SettingsCard, { SettingsRow } from '../../../shared/components/SettingsCard';
import { severityColors } from '../../../app/theme';
import { problemDetail } from '../../../shared/api/client';
import { listUsers } from '../../admin/usersApi';
import type { Role } from '../../auth/authApi';

const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateurs',
  SOC_MANAGER: 'Managers SOC',
  SOC_ANALYST: 'Analystes SOC',
  VIEWER: 'Lecteurs',
};

const ROLE_ORDER: Role[] = ['ADMIN', 'SOC_MANAGER', 'SOC_ANALYST', 'VIEWER'];

function RbacSection() {
  const navigate = useNavigate();
  const { data, isLoading, error } = useQuery({
    queryKey: ['settings-users-summary'],
    queryFn: listUsers,
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
      <Alert severity="error">{problemDetail(error, 'Liste des utilisateurs indisponible')}</Alert>
    );
  }

  const disabledCount = data.filter((u) => !u.enabled).length;
  const byRole = ROLE_ORDER.map((role) => ({
    role,
    count: data.filter((u) => u.role === role).length,
  }));

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
      <SettingsCard
        title="Effectif de la plateforme"
        description={`${data.length} compte${data.length > 1 ? 's' : ''} au total, répartis par rôle (RBAC à 4 niveaux).`}
        icon={<ManageAccountsOutlinedIcon />}
        color={severityColors.info}
        actions={
          <Button
            size="small"
            startIcon={<ManageAccountsOutlinedIcon fontSize="small" />}
            onClick={() => navigate('/admin/users')}
          >
            Gérer les utilisateurs
          </Button>
        }
      >
        {byRole.map(({ role, count }) => (
          <SettingsRow key={role} label={ROLE_LABELS[role]} value={count} />
        ))}
      </SettingsCard>
      <SettingsCard
        title="État des comptes"
        icon={<ManageAccountsOutlinedIcon />}
        color={severityColors.low}
        description="Un compte désactivé perd immédiatement toutes ses sessions actives."
      >
        <SettingsRow label="Comptes actifs" value={data.length - disabledCount} />
        <SettingsRow label="Comptes désactivés" value={disabledCount} />
        {disabledCount > 0 && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            Voir le détail dans le module Utilisateurs.
          </Typography>
        )}
      </SettingsCard>
    </Box>
  );
}

export default RbacSection;
