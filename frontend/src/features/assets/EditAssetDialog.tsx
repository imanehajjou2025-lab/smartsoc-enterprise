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
import { ASSET_TYPE_LABELS, EXPOSURE_LABELS } from './assetChips';
import {
  updateAsset,
  type Asset,
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

/**
 * Édition d'un actif : tout sauf le hostname (immuable — clé de
 * corrélation) et le statut (endpoints dédiés). Pré-rempli depuis la
 * fiche ; la clé du state force la réinitialisation à chaque ouverture.
 */
function EditAssetDialog({
  asset,
  open,
  onClose,
}: {
  asset: Asset;
  open: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    displayName: asset.displayName,
    ipAddress: asset.ipAddress ?? '',
    owner: asset.owner ?? '',
    description: asset.description ?? '',
    type: asset.type,
    criticality: asset.criticality,
    exposure: asset.exposure,
  });

  const mutation = useMutation({
    mutationFn: () =>
      updateAsset(asset.id, {
        displayName: form.displayName || undefined,
        ipAddress: form.ipAddress || undefined,
        owner: form.owner || undefined,
        description: form.description || undefined,
        type: form.type,
        criticality: form.criticality,
        exposure: form.exposure,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['assets'] });
      void queryClient.invalidateQueries({ queryKey: ['asset', asset.id] });
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

  const set = (field: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Modifier {asset.hostname}</DialogTitle>
        <DialogContent>
          {mutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {problemDetail(mutation.error, 'Modification impossible. Réessayez.')}
            </Alert>
          )}
          <TextField
            label="Nom d'affichage"
            value={form.displayName}
            onChange={set('displayName')}
            fullWidth
            autoFocus
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

export default EditAssetDialog;
