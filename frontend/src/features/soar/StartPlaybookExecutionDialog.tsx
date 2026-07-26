import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import { useMutation, useQuery } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import { listPlaybooks, startExecution, type PlaybookExecution } from './soarApi';

interface Props {
  open: boolean;
  onClose: () => void;
  incidentId: string;
  onStarted: (execution: PlaybookExecution) => void;
}

/** Choisit un playbook actif et démarre son suivi contre l'incident — fige une copie des étapes. */
function StartPlaybookExecutionDialog({ open, onClose, incidentId, onStarted }: Props) {
  const [playbookId, setPlaybookId] = useState('');

  const { data: playbooks, isPending } = useQuery({
    queryKey: ['playbooks', '', false, 'picker'],
    queryFn: () => listPlaybooks('', false, 0, 100),
    enabled: open,
  });

  const mutation = useMutation({
    mutationFn: () => startExecution(incidentId, playbookId),
    onSuccess: (execution) => {
      setPlaybookId('');
      onStarted(execution);
    },
  });

  const handleClose = () => {
    mutation.reset();
    setPlaybookId('');
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <DialogTitle>Exécuter un playbook</DialogTitle>
      <DialogContent>
        {mutation.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {problemDetail(mutation.error, 'Démarrage impossible.')}
          </Alert>
        )}
        {isPending ? (
          <CircularProgress size={24} />
        ) : (
          <TextField
            select
            label="Playbook"
            value={playbookId}
            onChange={(e) => setPlaybookId(e.target.value)}
            fullWidth
            margin="dense"
          >
            {(playbooks?.items ?? []).map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.name} (v{p.version})
              </MenuItem>
            ))}
            {playbooks?.items.length === 0 && (
              <MenuItem disabled value="">
                Aucun playbook disponible
              </MenuItem>
            )}
          </TextField>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Annuler</Button>
        <Button
          variant="contained"
          disabled={!playbookId || mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          {mutation.isPending ? 'Démarrage…' : 'Démarrer'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default StartPlaybookExecutionDialog;
