import { useState, type FormEvent } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import { useMutation } from '@tanstack/react-query';
import { problemDetail } from '../../../shared/api/client';
import { sendTestNotification } from '../settingsApi';

function TestEmailDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [recipientEmail, setRecipientEmail] = useState('');

  const mutation = useMutation({
    mutationFn: () => sendTestNotification(recipientEmail),
  });

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    mutation.mutate();
  };

  const handleClose = () => {
    mutation.reset();
    setRecipientEmail('');
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Tester l'envoi de notifications</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            Envoie un e-mail réel via le serveur SMTP configuré, pour vérifier qu'il fonctionne.
          </DialogContentText>
          {mutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {problemDetail(mutation.error, "Échec de l'envoi. Réessayez.")}
            </Alert>
          )}
          {mutation.isSuccess && (
            <Alert severity="success" sx={{ mb: 2 }}>
              E-mail envoyé avec succès à {recipientEmail}.
            </Alert>
          )}
          <TextField
            label="Adresse destinataire"
            type="email"
            value={recipientEmail}
            onChange={(e) => setRecipientEmail(e.target.value)}
            fullWidth
            required
            autoFocus
            margin="dense"
            placeholder="admin@smartsoc.local"
            disabled={mutation.isSuccess}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>{mutation.isSuccess ? 'Fermer' : 'Annuler'}</Button>
          {!mutation.isSuccess && (
            <Button
              type="submit"
              variant="contained"
              disabled={!recipientEmail.trim() || mutation.isPending}
            >
              {mutation.isPending ? 'Envoi…' : 'Envoyer'}
            </Button>
          )}
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default TestEmailDialog;
