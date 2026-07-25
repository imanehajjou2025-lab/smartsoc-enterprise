import { useState, type FormEvent } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import { useMutation } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import { createHunt, type HuntGroup, type HuntQuery } from './huntingApi';

interface Props {
  open: boolean;
  onClose: () => void;
  criteria: HuntGroup;
  onSaved: (hunt: HuntQuery) => void;
}

/** Sauvegarde les critères courants du constructeur sous un nouveau nom. */
function SaveHuntDialog({ open, onClose, criteria, onSaved }: Props) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const mutation = useMutation({
    mutationFn: () =>
      createHunt({ name: name.trim(), description: description.trim() || undefined, criteria }),
    onSuccess: (hunt) => {
      onSaved(hunt);
      setName('');
      setDescription('');
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
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Enregistrer la chasse</DialogTitle>
        <DialogContent>
          {mutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {problemDetail(mutation.error, 'Enregistrement impossible.')}
            </Alert>
          )}
          <TextField
            label="Nom"
            value={name}
            onChange={(e) => setName(e.target.value)}
            fullWidth
            required
            autoFocus
            margin="dense"
            placeholder="Ex. PowerShell suspect sur srv-web"
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            fullWidth
            multiline
            minRows={2}
            margin="dense"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Annuler</Button>
          <Button
            type="submit"
            variant="contained"
            disabled={name.trim() === '' || mutation.isPending}
          >
            {mutation.isPending ? 'Enregistrement…' : 'Enregistrer'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default SaveHuntDialog;
