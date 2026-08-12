import { useMemo, useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined';
import BlockOutlinedIcon from '@mui/icons-material/BlockOutlined';
import DeleteIcon from '@mui/icons-material/DeleteOutlined';
import EditIcon from '@mui/icons-material/EditOutlined';
import GroupOutlinedIcon from '@mui/icons-material/GroupOutlined';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { alpha, useTheme } from '@mui/material/styles';
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined';
import { useQuery } from '@tanstack/react-query';
import { severityColors } from '../../app/theme';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
import KpiTile from '../../shared/components/KpiTile';
import PageHeaderBanner from '../../shared/components/PageHeaderBanner';
import { ROLE_LABELS } from '../auth/roles';
import UserCreateDialog from './UserCreateDialog';
import UserDeleteDialog from './UserDeleteDialog';
import UserEditDialog from './UserEditDialog';
import { listUsers, type PlatformUser } from './usersApi';

const ROLE_CHIP_COLORS = {
  ADMIN: 'error',
  SOC_MANAGER: 'warning',
  SOC_ANALYST: 'info',
  VIEWER: 'default',
} as const;

/** Administration des comptes (ADMIN) — première feature complète de la console. */
function UsersPage() {
  const theme = useTheme();
  const currentUser = useAppSelector((state) => state.auth.user);
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<PlatformUser | null>(null);
  const [deleting, setDeleting] = useState<PlatformUser | null>(null);

  const {
    data: users,
    isPending,
    isError,
    error,
  } = useQuery({ queryKey: ['users'], queryFn: listUsers });

  // Comptés côté client : listUsers() renvoie déjà l'intégralité du parc
  // (pas de pagination serveur), ce sont donc de vrais totaux, pas des
  // comptes partiels sur une seule page.
  const stats = useMemo(() => {
    if (!users) return null;
    return {
      total: users.length,
      active: users.filter((u) => u.enabled).length,
      disabled: users.filter((u) => !u.enabled).length,
      admins: users.filter((u) => u.role === 'ADMIN').length,
    };
  }, [users]);

  return (
    <Box>
      <PageHeaderBanner
        icon={<GroupOutlinedIcon sx={{ color: theme.palette.primary.main, fontSize: 28 }} />}
        title="Utilisateurs"
        subtitle="Administration des comptes de la plateforme et de leurs rôles."
        action={{ label: 'Nouvel utilisateur', icon: <AddIcon />, onClick: () => setCreateOpen(true) }}
      />

      {stats && (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' },
            gap: 2,
            mb: 3,
          }}
        >
          <KpiTile
            label="Comptes"
            value={String(stats.total)}
            hint="au total"
            color={severityColors.info}
            icon={<GroupOutlinedIcon />}
          />
          <KpiTile
            label="Comptes actifs"
            value={String(stats.active)}
            hint="peuvent se connecter"
            color={severityColors.low}
            icon={<VerifiedUserOutlinedIcon />}
          />
          <KpiTile
            label="Comptes désactivés"
            value={String(stats.disabled)}
            hint="accès bloqué"
            color={severityColors.medium}
            icon={<BlockOutlinedIcon />}
          />
          <KpiTile
            label="Administrateurs"
            value={String(stats.admins)}
            hint="rôle ADMIN"
            color={severityColors.critical}
            icon={<AdminPanelSettingsOutlinedIcon />}
          />
        </Box>
      )}

      {isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}

      {isError && (
        <Alert severity="error">
          {problemDetail(error, 'Impossible de charger les utilisateurs.')}
        </Alert>
      )}

      {users && (
        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 3 }}>
          <Table size="small" aria-label="Liste des utilisateurs">
            <TableHead>
              <TableRow sx={{ '& th': { bgcolor: 'action.hover' } }}>
                <TableCell sx={{ fontWeight: 700 }}>Utilisateur</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Email</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Rôle</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Statut</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>
                  Actions
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {users.map((user) => {
                const isSelf = user.id === currentUser?.userId;
                return (
                  <TableRow
                    key={user.id}
                    hover
                    sx={{
                      borderLeft: '3px solid',
                      borderLeftColor: alpha(
                        user.enabled ? severityColors.low : theme.palette.text.secondary,
                        0.5,
                      ),
                    }}
                  >
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        {user.username}
                        {isSelf && (
                          <Chip label="vous" size="small" variant="outlined" sx={{ ml: 1 }} />
                        )}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {user.fullName}
                      </Typography>
                    </TableCell>
                    <TableCell>{user.email}</TableCell>
                    <TableCell>
                      <Chip
                        label={ROLE_LABELS[user.role]}
                        color={ROLE_CHIP_COLORS[user.role]}
                        size="small"
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={user.enabled ? 'Actif' : 'Désactivé'}
                        color={user.enabled ? 'success' : 'default'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Tooltip title="Modifier">
                        <IconButton
                          size="small"
                          aria-label={`Modifier ${user.username}`}
                          onClick={() => setEditing(user)}
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip
                        title={
                          isSelf ? 'Vous ne pouvez pas supprimer votre propre compte' : 'Supprimer'
                        }
                      >
                        <span>
                          <IconButton
                            size="small"
                            aria-label={`Supprimer ${user.username}`}
                            onClick={() => setDeleting(user)}
                            disabled={isSelf}
                          >
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <UserCreateDialog open={createOpen} onClose={() => setCreateOpen(false)} />
      <UserEditDialog
        user={editing}
        isSelf={editing?.id === currentUser?.userId}
        onClose={() => setEditing(null)}
      />
      <UserDeleteDialog user={deleting} onClose={() => setDeleting(null)} />
    </Box>
  );
}

export default UsersPage;
