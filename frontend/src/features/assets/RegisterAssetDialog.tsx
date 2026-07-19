import { useState, type FormEvent } from 'react';
import axios from 'axios';
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
import { ASSET_TYPE_LABELS, EXPOSURE_LABELS } from './assetChips';
import {
  registerAsset,
  type AssetCriticality,
  type AssetExposure,
  type AssetType,
} from './assetsApi';

const TYPES: AssetType[] = [
  'SERVER',
  'WORKSTATION',
  'NETWORK_DEVICE',
  'DATABASE',
  'APPLICATION',
  'CLOUD_RESOURCE',
  'OTHER',
];
const CRITICALITIES: AssetCriticality[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const EXPOSURES: AssetExposure[] = ['INTERNET_FACING', 'INTERNAL', 'ISOLATED'];

const EMPTY = {
  hostname: '',
  displayName: '',
  type: 'SERVER' as AssetType,
  criticality: 'MEDIUM' as AssetCriticality,
  exposure: 'INTERNAL' as AssetExposure,
  ipAddress: '',
  owner: '',
  description: '',
};

/** Le 409 est un cas métier attendu : message dédié, pas d'erreur brute. */
function registrationError(error: unknown): string {
  if (axios.isAxiosError(error) && error.response?.status === 409) {
    return 'Un actif est déjà inventorié pour ce hostname.';
  }
  return problemDetail(error, 'Enregistrement impossible. Réessayez.');
}

function RegisterAssetDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(EMPTY);

  const mutation = useMutation({
    mutationFn: () =>
      registerAsset({
        hostname: form.hostname,
        displayName: form.displayName || undefined,
        type: form.type,
        criticality: form.criticality,
        exposure: form.exposure,
        ipAddress: form.ipAddress || undefined,
        owner: form.owner || undefined,
        description: form.description || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['assets'] });
      setForm(EMPTY);
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

  const set = (field: keyof typeof EMPTY) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Enregistrer un actif</DialogTitle>
        <DialogContent>
          {mutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {registrationError(mutation.error)}
            </Alert>
          )}
          <TextField
            label="Hostname"
            value={form.hostname}
            onChange={set('hostname')}
            fullWidth
            required
            autoFocus
            margin="dense"
            helperText="Clé de corrélation avec les alertes — tel que rapporté par la source"
          />
          <TextField
            label="Nom d'affichage"
            value={form.displayName}
            onChange={set('displayName')}
            fullWidth
            margin="dense"
          />
          <TextField
            label="Type"
            select
            value={form.type}
            onChange={set('type')}
            fullWidth
            required
            margin="dense"
          >
            {TYPES.map((value) => (
              <MenuItem key={value} value={value}>
                {ASSET_TYPE_LABELS[value]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Criticité"
            select
            value={form.criticality}
            onChange={set('criticality')}
            fullWidth
            required
            margin="dense"
          >
            {CRITICALITIES.map((value) => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Exposition"
            select
            value={form.exposure}
            onChange={set('exposure')}
            fullWidth
            required
            margin="dense"
          >
            {EXPOSURES.map((value) => (
              <MenuItem key={value} value={value}>
                {EXPOSURE_LABELS[value]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Adresse IP"
            value={form.ipAddress}
            onChange={set('ipAddress')}
            fullWidth
            margin="dense"
          />
          <TextField
            label="Propriétaire"
            value={form.owner}
            onChange={set('owner')}
            fullWidth
            margin="dense"
          />
          <TextField
            label="Description"
            value={form.description}
            onChange={set('description')}
            fullWidth
            multiline
            minRows={2}
            margin="dense"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Annuler</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            {mutation.isPending ? 'Enregistrement…' : 'Enregistrer'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default RegisterAssetDialog;
