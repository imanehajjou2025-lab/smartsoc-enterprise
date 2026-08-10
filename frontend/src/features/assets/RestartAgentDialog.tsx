import { useState } from 'react';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import { restartAgent, type Asset } from './assetsApi';

interface Props {
  asset: Asset;
  open: boolean;
  onClose: () => void;
}

/**
 * Confirmation d'une action à EFFET RÉEL (ADR-014 phase 5) : la cible
 * doit être NOMMÉE en la retapant, pas juste validée d'un clic — même
 * garde-fou que le serveur applique de toute façon (confirmation
 * explicite, jamais implicite), ici pour qu'une erreur se voie AVANT
 * l'envoi plutôt que de dépendre uniquement du refus serveur.
 */
function RestartAgentDialog({ asset, open, onClose }: Props) {
  const queryClient = useQueryClient();
  const [confirmHostname, setConfirmHostname] = useState('');
  const [reason, setReason] = useState('');

  const mutation = useMutation({
    mutationFn: () => restartAgent(asset.id, confirmHostname.trim(), reason.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['asset', asset.id] });
      setConfirmHostname('');
      setReason('');
      onClose();
    },
  });

  const hostnameMatches = confirmHostname.trim().toLowerCase() === asset.hostname.toLowerCase();
  const canSubmit = hostnameMatches && reason.trim() !== '' && !mutation.isPending;

  const handleClose = () => {
    setConfirmHostname('');
    setReason('');
    mutation.reset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <WarningAmberIcon color="warning" fontSize="small" />
          Redémarrer l'agent Wazuh
        </Stack>
      </DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Cette action redémarre RÉELLEMENT l'agent Wazuh sur <strong>{asset.displayName}</strong>.
          Pour confirmer, retapez son hostname exact.
        </Typography>
        {mutation.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {problemDetail(mutation.error, 'Action impossible.')}
          </Alert>
        )}
        <TextField
          label={`Hostname (tapez "${asset.hostname}")`}
          fullWidth
          required
          value={confirmHostname}
          onChange={(e) => setConfirmHostname(e.target.value)}
          placeholder={asset.hostname}
          error={confirmHostname !== '' && !hostnameMatches}
          helperText={
            confirmHostname !== '' && !hostnameMatches
              ? 'Ne correspond pas au hostname de cet actif'
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
          placeholder="Ex. agent bloqué, redémarrage demandé suite au ticket #123"
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Annuler</Button>
        <Button
          color="warning"
          variant="contained"
          disabled={!canSubmit}
          onClick={() => mutation.mutate()}
        >
          {mutation.isPending ? 'Redémarrage…' : 'Confirmer le redémarrage'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default RestartAgentDialog;
