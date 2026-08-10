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
import { blockIp, type Asset } from './assetsApi';

interface Props {
  asset: Asset;
  open: boolean;
  onClose: () => void;
}

const IPV4_FORMAT = /^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$/;

/**
 * Confirmation d'une action à EFFET RÉEL (ADR-014 phase 5) : la cible
 * doit être NOMMÉE en la retapant, pas juste validée d'un clic — même
 * garde-fou que le serveur applique de toute façon (confirmation
 * explicite, jamais implicite), ici pour qu'une erreur se voie AVANT
 * l'envoi plutôt que de dépendre uniquement du refus serveur.
 */
function BlockIpDialog({ asset, open, onClose }: Props) {
  const queryClient = useQueryClient();
  const [confirmHostname, setConfirmHostname] = useState('');
  const [ipAddress, setIpAddress] = useState('');
  const [reason, setReason] = useState('');

  const mutation = useMutation({
    mutationFn: () => blockIp(asset.id, confirmHostname.trim(), ipAddress.trim(), reason.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['asset', asset.id] });
      setConfirmHostname('');
      setIpAddress('');
      setReason('');
      onClose();
    },
  });

  const hostnameMatches = confirmHostname.trim().toLowerCase() === asset.hostname.toLowerCase();
  const ipIsValid = IPV4_FORMAT.test(ipAddress.trim());
  const canSubmit = hostnameMatches && ipIsValid && reason.trim() !== '' && !mutation.isPending;

  const handleClose = () => {
    setConfirmHostname('');
    setIpAddress('');
    setReason('');
    mutation.reset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <WarningAmberIcon color="warning" fontSize="small" />
          Bloquer une IP (active-response)
        </Stack>
      </DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Cette action bloque RÉELLEMENT une IP source sur le pare-feu local de{' '}
          <strong>{asset.displayName}</strong> (commande Wazuh <code>firewall-drop</code>). Pour
          confirmer, retapez son hostname exact.
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
          label="Adresse IP à bloquer"
          fullWidth
          required
          value={ipAddress}
          onChange={(e) => setIpAddress(e.target.value)}
          placeholder="203.0.113.42"
          error={ipAddress !== '' && !ipIsValid}
          helperText={ipAddress !== '' && !ipIsValid ? 'Adresse IPv4 invalide' : ' '}
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
          placeholder="Ex. IP source d'une attaque en cours, voir alerte #123"
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
          {mutation.isPending ? 'Blocage…' : 'Confirmer le blocage'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default BlockIpDialog;
