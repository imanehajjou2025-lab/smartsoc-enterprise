import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import { deleteUser, type PlatformUser } from './usersApi';

interface Props {
  user: PlatformUser | null;
  onClose: () => void;
}

function UserDeleteDialog({ user, onClose }: Props) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => deleteUser(user!.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      onClose();
    },
  });

  const handleClose = () => {
    mutation.reset();
    onClose();
  };

  return (
    <Dialog open={Boolean(user)} onClose={handleClose} maxWidth="xs" fullWidth>
      <DialogTitle>Supprimer {user?.username} ?</DialogTitle>
      <DialogContent>
        {mutation.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {problemDetail(mutation.error, 'Suppression impossible. Réessayez.')}
          </Alert>
        )}
        <DialogContentText>
          Le compte sera désactivé et supprimé logiquement (soft delete) : toutes ses sessions
          seront révoquées immédiatement. Le nom d'utilisateur redeviendra disponible.
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Annuler</Button>
        <Button
          onClick={() => mutation.mutate()}
          color="error"
          variant="contained"
          disabled={mutation.isPending}
        >
          {mutation.isPending ? 'Suppression…' : 'Supprimer'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default UserDeleteDialog;
