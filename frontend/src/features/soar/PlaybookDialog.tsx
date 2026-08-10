import { useEffect, useState } from 'react';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { problemDetail } from '../../shared/api/client';
import { createPlaybook, updatePlaybook, type Playbook } from './soarApi';

interface Props {
  open: boolean;
  onClose: () => void;
  /** Playbook à éditer, ou `null` pour en déclarer un nouveau. */
  playbook: Playbook | null;
}

interface StepDraft {
  title: string;
  description: string;
}

function toDrafts(playbook: Playbook | null): StepDraft[] {
  if (!playbook) return [{ title: '', description: '' }];
  return playbook.steps.map((s) => ({ title: s.title, description: s.description ?? '' }));
}

/** Déclare ou met à jour un playbook — le serveur dérive toujours l'ordre des étapes de leur position. */
function PlaybookDialog({ open, onClose, playbook }: Props) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [steps, setSteps] = useState<StepDraft[]>([{ title: '', description: '' }]);
  const [shuffleWorkflowId, setShuffleWorkflowId] = useState('');
  const [shuffleWebhookPath, setShuffleWebhookPath] = useState('');

  useEffect(() => {
    if (open) {
      setName(playbook?.name ?? '');
      setDescription(playbook?.description ?? '');
      setSteps(toDrafts(playbook));
      setShuffleWorkflowId(playbook?.shuffleWorkflowId ?? '');
      setShuffleWebhookPath(playbook?.shuffleWebhookPath ?? '');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, playbook?.id]);

  const mutation = useMutation({
    mutationFn: () => {
      const payload = {
        name: name.trim(),
        description: description.trim() || undefined,
        steps: steps.map((s, i) => ({
          order: i,
          title: s.title.trim(),
          description: s.description.trim(),
        })),
        shuffleWorkflowId: shuffleWorkflowId.trim() || undefined,
        shuffleWebhookPath: shuffleWebhookPath.trim() || undefined,
      };
      return playbook ? updatePlaybook(playbook.id, payload) : createPlaybook(payload);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['playbooks'] });
      onClose();
    },
  });

  const handleClose = () => {
    mutation.reset();
    onClose();
  };

  const validSteps = steps.every((s) => s.title.trim() !== '');
  const canSubmit = name.trim() !== '' && steps.length > 0 && validSteps;

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        {playbook ? `Modifier « ${playbook.name} »` : 'Déclarer un playbook'}
      </DialogTitle>
      <DialogContent>
        {mutation.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {problemDetail(mutation.error, 'Enregistrement impossible.')}
          </Alert>
        )}
        <TextField
          label="Nom"
          value={name}
          onChange={(e) => setName(e.target.value)}
          fullWidth
          required
          autoFocus
          margin="dense"
          placeholder="Ex. Confinement ransomware"
        />
        <TextField
          label="Description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          fullWidth
          multiline
          minRows={2}
          margin="dense"
        />

        <Typography variant="subtitle2" sx={{ mt: 2, mb: 1 }}>
          Étapes
        </Typography>
        <Stack spacing={1.5}>
          {steps.map((step, index) => (
            <Stack key={index} direction="row" spacing={1}>
              <Typography variant="body2" color="text.secondary" sx={{ pt: 1, minWidth: 20 }}>
                {index + 1}.
              </Typography>
              <Stack spacing={0.5} sx={{ flexGrow: 1 }}>
                <TextField
                  size="small"
                  placeholder="Titre de l'étape"
                  value={step.title}
                  onChange={(e) =>
                    setSteps((prev) =>
                      prev.map((s, i) => (i === index ? { ...s, title: e.target.value } : s)),
                    )
                  }
                  fullWidth
                />
                <TextField
                  size="small"
                  placeholder="Détail (optionnel)"
                  value={step.description}
                  onChange={(e) =>
                    setSteps((prev) =>
                      prev.map((s, i) => (i === index ? { ...s, description: e.target.value } : s)),
                    )
                  }
                  fullWidth
                />
              </Stack>
              <IconButton
                size="small"
                disabled={steps.length === 1}
                onClick={() => setSteps((prev) => prev.filter((_, i) => i !== index))}
              >
                <DeleteOutlineIcon fontSize="small" />
              </IconButton>
            </Stack>
          ))}
        </Stack>
        <Button
          size="small"
          startIcon={<AddIcon />}
          onClick={() => setSteps((prev) => [...prev, { title: '', description: '' }])}
          sx={{ mt: 1 }}
        >
          Ajouter une étape
        </Button>

        <Typography variant="subtitle2" sx={{ mt: 2, mb: 1 }}>
          Lien Shuffle (optionnel)
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          Les deux identifiants sont nécessaires pour activer le déclenchement réel — un playbook
          sans lien reste purement documentaire (suivi guidé manuel).
        </Typography>
        <TextField
          label="Identifiant du workflow Shuffle"
          value={shuffleWorkflowId}
          onChange={(e) => setShuffleWorkflowId(e.target.value)}
          fullWidth
          margin="dense"
          placeholder="Ex. fb0e09e3-402f-4d20-9bc1-f7fa845d4314"
        />
        <TextField
          label="Chemin du webhook de déclenchement"
          value={shuffleWebhookPath}
          onChange={(e) => setShuffleWebhookPath(e.target.value)}
          fullWidth
          margin="dense"
          placeholder="Ex. webhook_a0fa6c78-fa6c-41a1-ac56-3c7f514ba8f4"
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Annuler</Button>
        <Button
          variant="contained"
          disabled={!canSubmit || mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          {mutation.isPending ? 'Enregistrement…' : 'Enregistrer'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default PlaybookDialog;
