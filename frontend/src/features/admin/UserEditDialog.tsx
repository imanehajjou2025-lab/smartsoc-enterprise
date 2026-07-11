import { useEffect, useState, type FormEvent } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import type { Role } from '../auth/authApi';
import { ROLE_OPTIONS } from '../auth/roles';
import { updateUser, type PlatformUser } from './usersApi';

interface Props {
  user: PlatformUser | null;
  isSelf: boolean;
  onClose: () => void;
}

function UserEditDialog({ user, isSelf, onClose }: Props) {
  const queryClient = useQueryClient();
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState<Role>('SOC_ANALYST');
  const [enabled, setEnabled] = useState(true);

  useEffect(() => {
    if (user) {
      setFullName(user.fullName);
      setRole(user.role);
      setEnabled(user.enabled);
    }
  }, [user]);

  const mutation = useMutation({
    mutationFn: () => updateUser(user!.id, { fullName, role, enabled }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      onClose();
    },
  });

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    mutation.mutate();
  };

  const handleClose = () => {
    mutation.reset();
    onClose();
  };

  return (
    <Dialog open={Boolean(user)} onClose={handleClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Modifier {user?.username}</DialogTitle>
        <DialogContent>
          {mutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {problemDetail(mutation.error, 'Modification impossible. Réessayez.')}
            </Alert>
          )}
          <TextField
            label="Nom complet"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            fullWidth
            required
            margin="dense"
            autoFocus
          />
          <TextField
            label="Rôle"
            select
            value={role}
            onChange={(e) => setRole(e.target.value as Role)}
            fullWidth
            margin="dense"
            disabled={isSelf}
            helperText={isSelf ? 'Vous ne pouvez pas modifier votre propre rôle.' : undefined}
          >
            {ROLE_OPTIONS.map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>
          <FormControlLabel
            control={
              <Switch
                checked={enabled}
                onChange={(e) => setEnabled(e.target.checked)}
                disabled={isSelf}
              />
            }
            label="Compte actif"
            sx={{ mt: 1 }}
          />
          {!enabled && user?.enabled && (
            <Typography variant="caption" color="warning.main" sx={{ display: 'block' }}>
              La désactivation révoque immédiatement toutes les sessions de l'utilisateur.
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Annuler</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            {mutation.isPending ? 'Enregistrement…' : 'Enregistrer'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default UserEditDialog;
