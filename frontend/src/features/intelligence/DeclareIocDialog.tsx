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
import { IOC_TYPE_LABELS, TLP_LABELS } from './iocChips';
import { declareIoc, type IndicatorType, type TlpMarking } from './intelligenceApi';

const TYPES: IndicatorType[] = ['IPV4', 'IPV6', 'DOMAIN', 'URL', 'MD5', 'SHA1', 'SHA256', 'EMAIL'];
const TLPS: TlpMarking[] = ['CLEAR', 'GREEN', 'AMBER', 'RED'];

const EMPTY = {
  type: 'DOMAIN' as IndicatorType,
  value: '',
  confidence: '50',
  tlp: 'AMBER' as TlpMarking,
  description: '',
  tags: '',
  validUntil: '',
};

/** Le 409 est un cas métier attendu : message dédié, pas d'erreur brute. */
function declareError(error: unknown): string {
  if (axios.isAxiosError(error) && error.response?.status === 409) {
    return 'Un indicateur existe déjà pour ce type et cette valeur.';
  }
  return problemDetail(error, 'Déclaration impossible. Réessayez.');
}

/**
 * Déclaration manuelle d'un IOC par un analyste (source « manual »). La
 * valeur est envoyée BRUTE : la normalisation (refang, casse) appartient
 * au serveur, exactement comme pour les flux — l'indicateur apparaît
 * ensuite dans la liste sous sa forme normalisée.
 */
function DeclareIocDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(EMPTY);

  const mutation = useMutation({
    mutationFn: () => {
      const tags = form.tags
        .split(',')
        .map((t) => t.trim())
        .filter((t) => t !== '');
      return declareIoc({
        type: form.type,
        value: form.value.trim(),
        confidence: Number(form.confidence),
        tlp: form.tlp,
        description: form.description.trim() || undefined,
        tags: tags.length > 0 ? tags : undefined,
        // datetime-local est une heure locale sans fuseau ; on la convertit
        // en instant UTC ISO-8601, la forme attendue par le contrat.
        validUntil: form.validUntil ? new Date(form.validUntil).toISOString() : undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['iocs'] });
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
        <DialogTitle>Déclarer un indicateur</DialogTitle>
        <DialogContent>
          {mutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {declareError(mutation.error)}
            </Alert>
          )}
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
                {IOC_TYPE_LABELS[value]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Valeur"
            value={form.value}
            onChange={set('value')}
            fullWidth
            required
            autoFocus
            margin="dense"
            slotProps={{ htmlInput: { style: { fontFamily: 'monospace' } } }}
            helperText="Valeur brute — la plateforme normalise (refang, casse). Ex. evil[.]com"
          />
          <TextField
            label="Confiance"
            type="number"
            value={form.confidence}
            onChange={(e) =>
              setForm((f) => ({
                ...f,
                confidence: String(Math.max(0, Math.min(100, Number(e.target.value)))),
              }))
            }
            fullWidth
            required
            margin="dense"
            slotProps={{ htmlInput: { min: 0, max: 100 } }}
            helperText="0 à 100"
          />
          <TextField
            label="TLP"
            select
            value={form.tlp}
            onChange={set('tlp')}
            fullWidth
            required
            margin="dense"
            helperText="Contrainte de diffusion (défaut AMBER)"
          >
            {TLPS.map((value) => (
              <MenuItem key={value} value={value}>
                {TLP_LABELS[value]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Tags"
            value={form.tags}
            onChange={set('tags')}
            fullWidth
            margin="dense"
            placeholder="c2, ransomware"
            helperText="Séparés par des virgules"
          />
          <TextField
            label="Valide jusqu'à"
            type="datetime-local"
            value={form.validUntil}
            onChange={set('validUntil')}
            fullWidth
            margin="dense"
            slotProps={{ inputLabel: { shrink: true } }}
            helperText="Optionnel — sans date, l'indicateur n'expire pas"
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
          <Button
            type="submit"
            variant="contained"
            disabled={form.value.trim() === '' || mutation.isPending}
          >
            {mutation.isPending ? 'Déclaration…' : 'Déclarer'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default DeclareIocDialog;
