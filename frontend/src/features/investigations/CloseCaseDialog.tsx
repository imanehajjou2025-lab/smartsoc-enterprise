import { useState, type FormEvent } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import { closeCase } from './investigationsApi';

/**
 * Clôture formelle d'un cas : la conclusion est obligatoire (miroir de la
 * règle du domaine) et la clôture est DÉFINITIVE — la reprise passera par
 * un cas de suivi.
 */
function CloseCaseDialog({
  caseId,
  reference,
  open,
  onClose,
}: {
  caseId: string;
  reference: string;
  open: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [conclusion, setConclusion] = useState('');

  const mutation = useMutation({
    mutationFn: () => closeCase(caseId, conclusion),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['investigation', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['investigations'] });
      setConclusion('');
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
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Clôturer {reference}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            La clôture est définitive : le dossier devient immuable. Si l'enquête doit
            reprendre, un cas de suivi sera ouvert depuis ce dossier.
          </DialogContentText>
          {mutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {problemDetail(mutation.error, 'Clôture impossible. Réessayez.')}
            </Alert>
          )}
          <TextField
            label="Conclusion"
            value={conclusion}
            onChange={(e) => setConclusion(e.target.value)}
            fullWidth
            required
            autoFocus
            multiline
            minRows={3}
            margin="dense"
            placeholder="Ex. : Vrai positif — campagne confirmée, IOC bloqués."
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Annuler</Button>
          <Button
            type="submit"
            color="error"
            variant="contained"
            disabled={!conclusion.trim() || mutation.isPending}
          >
            {mutation.isPending ? 'Clôture…' : 'Clôturer définitivement'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default CloseCaseDialog;
