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
import type { AlertSeverity } from '../alerts/alertsApi';
import { createCase, openFollowUpCase } from './investigationsApi';

const SEVERITIES: AlertSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const EMPTY = { title: '', description: '', priority: 'MEDIUM' as AlertSeverity };

/**
 * Création d'un cas d'investigation. Sert aussi à ouvrir un cas de SUIVI
 * (reprise d'une enquête clôturée) quand originCase est fourni : même
 * formulaire, endpoint /follow-up, titre pré-rempli.
 */
function CreateCaseDialog({
  open,
  onClose,
  originCase,
}: {
  open: boolean;
  onClose: () => void;
  originCase?: { id: string; reference: string; title: string } | null;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(EMPTY);
  const [prefilledFor, setPrefilledFor] = useState<string | null>(null);

  // Pré-remplissage du titre en mode suivi, une seule fois par cas d'origine.
  if (originCase && prefilledFor !== originCase.id) {
    setForm({ ...EMPTY, title: `Reprise : ${originCase.title}` });
    setPrefilledFor(originCase.id);
  }

  const mutation = useMutation({
    mutationFn: (payload: typeof EMPTY) =>
      originCase ? openFollowUpCase(originCase.id, payload) : createCase(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['investigations'] });
      // Un suivi modifie aussi le détail du cas d'origine (followUps + timeline).
      void queryClient.invalidateQueries({ queryKey: ['investigation'] });
      setForm(EMPTY);
      setPrefilledFor(null);
      onClose();
    },
  });

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    mutation.mutate(form);
  };

  const handleClose = () => {
    mutation.reset();
    setPrefilledFor(null);
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>
          {originCase ? `Cas de suivi de ${originCase.reference}` : 'Nouveau cas'}
        </DialogTitle>
        <DialogContent>
          {mutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {problemDetail(mutation.error, 'Création impossible. Réessayez.')}
            </Alert>
          )}
          <TextField
            label="Titre"
            value={form.title}
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
            fullWidth
            required
            autoFocus
            margin="dense"
          />
          <TextField
            label="Description"
            value={form.description}
            onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
            fullWidth
            multiline
            minRows={2}
            margin="dense"
          />
          <TextField
            label="Priorité"
            select
            value={form.priority}
            onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value as AlertSeverity }))}
            fullWidth
            required
            margin="dense"
          >
            {SEVERITIES.map((value) => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Annuler</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            {mutation.isPending ? 'Création…' : originCase ? 'Ouvrir le suivi' : 'Créer'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default CreateCaseDialog;
