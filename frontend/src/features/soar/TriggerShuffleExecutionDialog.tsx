import { useState } from 'react';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import {
  isLinkedToShuffle,
  listPlaybooks,
  triggerShuffleExecution,
  type PlaybookExecution,
} from './soarApi';

interface Props {
  open: boolean;
  onClose: () => void;
  incidentId: string;
  onTriggered: (execution: PlaybookExecution) => void;
}

/**
 * Confirmation d'une action à EFFET RÉEL (ADR-014 phase 5) : la cible
 * doit être NOMMÉE en retapant le nom exact du playbook, pas juste
 * validée d'un clic — même garde-fou que le contrôle d'agents Wazuh
 * (le serveur revalide de toute façon, ce contrôle client n'est qu'un
 * confort).
 */
function TriggerShuffleExecutionDialog({ open, onClose, incidentId, onTriggered }: Props) {
  const [playbookId, setPlaybookId] = useState('');
  const [confirmPlaybookName, setConfirmPlaybookName] = useState('');
  const [reason, setReason] = useState('');

  const { data: playbooks, isPending } = useQuery({
    queryKey: ['playbooks', '', false, 'shuffle-picker'],
    queryFn: () => listPlaybooks('', false, 0, 100),
    enabled: open,
  });

  const linkedPlaybooks = (playbooks?.items ?? []).filter(isLinkedToShuffle);
  const selectedPlaybook = linkedPlaybooks.find((p) => p.id === playbookId) ?? null;

  const mutation = useMutation({
    mutationFn: () =>
      triggerShuffleExecution(incidentId, playbookId, confirmPlaybookName.trim(), reason.trim()),
    onSuccess: (execution) => {
      reset();
      onTriggered(execution);
    },
  });

  const reset = () => {
    setPlaybookId('');
    setConfirmPlaybookName('');
    setReason('');
  };

  const handleClose = () => {
    mutation.reset();
    reset();
    onClose();
  };

  const nameMatches =
    selectedPlaybook !== null &&
    confirmPlaybookName.trim().toLowerCase() === selectedPlaybook.name.trim().toLowerCase();
  const canSubmit =
    selectedPlaybook !== null && nameMatches && reason.trim() !== '' && !mutation.isPending;

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <WarningAmberIcon color="warning" fontSize="small" />
          Déclencher un workflow Shuffle
        </Stack>
      </DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Cette action déclenche RÉELLEMENT un workflow Shuffle. Pour confirmer, retapez le nom
          exact du playbook ciblé.
        </Typography>
        {mutation.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {problemDetail(mutation.error, 'Déclenchement impossible.')}
          </Alert>
        )}
        {isPending ? (
          <CircularProgress size={24} />
        ) : (
          <TextField
            select
            label="Playbook lié à Shuffle"
            value={playbookId}
            onChange={(e) => {
              setPlaybookId(e.target.value);
              setConfirmPlaybookName('');
            }}
            fullWidth
            margin="dense"
            sx={{ mb: 2 }}
          >
            {linkedPlaybooks.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.name} (v{p.version})
              </MenuItem>
            ))}
            {linkedPlaybooks.length === 0 && (
              <MenuItem disabled value="">
                Aucun playbook lié à un workflow Shuffle
              </MenuItem>
            )}
          </TextField>
        )}
        {selectedPlaybook && (
          <>
            <TextField
              label={`Nom du playbook (tapez "${selectedPlaybook.name}")`}
              fullWidth
              required
              value={confirmPlaybookName}
              onChange={(e) => setConfirmPlaybookName(e.target.value)}
              placeholder={selectedPlaybook.name}
              error={confirmPlaybookName !== '' && !nameMatches}
              helperText={
                confirmPlaybookName !== '' && !nameMatches
                  ? 'Ne correspond pas au nom de ce playbook'
                  : ' '
              }
              sx={{ mb: 2 }}
            />
            <TextField
              label="Motif"
              fullWidth
              required
              multiline
              minRows={2}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Ex. confinement automatisé demandé suite au ticket #123"
            />
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Annuler</Button>
        <Button
          color="warning"
          variant="contained"
          disabled={!canSubmit}
          onClick={() => mutation.mutate()}
        >
          {mutation.isPending ? 'Déclenchement…' : 'Confirmer le déclenchement'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default TriggerShuffleExecutionDialog;
