import { useState, type FormEvent } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import { generateReport, type Report } from './reportsApi';

/** Champs datetime-local pré-remplis sur les 7 derniers jours — le cas d'usage le plus courant. */
function defaultForm() {
  const now = new Date();
  const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
  const toLocalInput = (d: Date) => {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  };
  return {
    title: `Rapport hebdomadaire ${now.toLocaleDateString('fr-FR')}`,
    periodStart: toLocalInput(weekAgo),
    periodEnd: toLocalInput(now),
  };
}

interface Props {
  open: boolean;
  onClose: () => void;
  onGenerated: (report: Report) => void;
}

/** Génère un instantané figé d'indicateurs sur la période choisie (ADR-013). */
function GenerateReportDialog({ open, onClose, onGenerated }: Props) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(defaultForm());

  const mutation = useMutation({
    mutationFn: () =>
      generateReport({
        title: form.title.trim(),
        // datetime-local est une heure locale sans fuseau ; conversion en
        // instant UTC ISO-8601, la forme attendue par le contrat.
        periodStart: new Date(form.periodStart).toISOString(),
        periodEnd: new Date(form.periodEnd).toISOString(),
      }),
    onSuccess: (report) => {
      void queryClient.invalidateQueries({ queryKey: ['reports'] });
      setForm(defaultForm());
      onGenerated(report);
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

  const set = (field: keyof ReturnType<typeof defaultForm>) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  const periodInvalid =
    form.periodStart !== '' && form.periodEnd !== '' && form.periodStart >= form.periodEnd;

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Générer un rapport</DialogTitle>
        <DialogContent>
          {mutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {problemDetail(mutation.error, 'Génération impossible.')}
            </Alert>
          )}
          {periodInvalid && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              La fin de période doit être postérieure au début.
            </Alert>
          )}
          <TextField
            label="Titre"
            value={form.title}
            onChange={set('title')}
            fullWidth
            required
            autoFocus
            margin="dense"
          />
          <TextField
            label="Début de la période"
            type="datetime-local"
            value={form.periodStart}
            onChange={set('periodStart')}
            fullWidth
            required
            margin="dense"
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="Fin de la période"
            type="datetime-local"
            value={form.periodEnd}
            onChange={set('periodEnd')}
            fullWidth
            required
            margin="dense"
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Annuler</Button>
          <Button
            type="submit"
            variant="contained"
            disabled={form.title.trim() === '' || periodInvalid || mutation.isPending}
          >
            {mutation.isPending ? 'Génération…' : 'Générer'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default GenerateReportDialog;
