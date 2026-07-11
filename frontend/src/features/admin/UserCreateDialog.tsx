import { useState, type FormEvent } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import type { Role } from '../auth/authApi';
import { ROLE_OPTIONS } from '../auth/roles';
import { createUser } from './usersApi';

interface Props {
  open: boolean;
  onClose: () => void;
}

const EMPTY_FORM = {
  username: '',
  email: '',
  fullName: '',
  password: '',
  role: 'SOC_ANALYST' as Role,
};

function UserCreateDialog({ open, onClose }: Props) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(EMPTY_FORM);

  const mutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      setForm(EMPTY_FORM);
      onClose();
    },
  });

  const set = (field: keyof typeof EMPTY_FORM) => (value: string) =>
    setForm((f) => ({ ...f, [field]: value }));

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    mutation.mutate(form);
  };

  const handleClose = () => {
    mutation.reset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Nouvel utilisateur</DialogTitle>
        <DialogContent>
          {mutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {problemDetail(mutation.error, 'Création impossible. Réessayez.')}
            </Alert>
          )}
          <TextField
            label="Nom d'utilisateur"
            value={form.username}
            onChange={(e) => set('username')(e.target.value)}
            fullWidth
            required
            margin="dense"
            autoFocus
          />
          <TextField
            label="Email"
            type="email"
            value={form.email}
            onChange={(e) => set('email')(e.target.value)}
            fullWidth
            required
            margin="dense"
          />
          <TextField
            label="Nom complet"
            value={form.fullName}
            onChange={(e) => set('fullName')(e.target.value)}
            fullWidth
            required
            margin="dense"
          />
          <TextField
            label="Mot de passe (12 caractères minimum)"
            type="password"
            value={form.password}
            onChange={(e) => set('password')(e.target.value)}
            fullWidth
            required
            margin="dense"
            slotProps={{ htmlInput: { minLength: 12, maxLength: 128 } }}
          />
          <TextField
            label="Rôle"
            select
            value={form.role}
            onChange={(e) => set('role')(e.target.value)}
            fullWidth
            required
            margin="dense"
          >
            {ROLE_OPTIONS.map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Annuler</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            {mutation.isPending ? 'Création…' : 'Créer'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default UserCreateDialog;
