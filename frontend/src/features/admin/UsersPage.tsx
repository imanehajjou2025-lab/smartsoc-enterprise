import { useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import DeleteIcon from '@mui/icons-material/DeleteOutlined';
import EditIcon from '@mui/icons-material/EditOutlined';
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
import { useQuery } from '@tanstack/react-query';
import { useAppSelector } from '../../app/hooks';
import { problemDetail } from '../../shared/api/client';
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

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h5" component="h2">
          Utilisateurs
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
          Nouvel utilisateur
        </Button>
      </Box>

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
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label="Liste des utilisateurs">
            <TableHead>
              <TableRow>
                <TableCell>Utilisateur</TableCell>
                <TableCell>Email</TableCell>
                <TableCell>Rôle</TableCell>
                <TableCell>Statut</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {users.map((user) => {
                const isSelf = user.id === currentUser?.userId;
                return (
                  <TableRow key={user.id} hover>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
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
