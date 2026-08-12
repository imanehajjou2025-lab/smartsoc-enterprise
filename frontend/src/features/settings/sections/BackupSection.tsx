import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import { useMutation } from '@tanstack/react-query';
import SettingsCard from '../../../shared/components/SettingsCard';
import { severityColors } from '../../../app/theme';
import { problemDetail } from '../../../shared/api/client';
import { downloadBackup } from '../settingsApi';

function BackupSection() {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const mutation = useMutation({
    mutationFn: downloadBackup,
    onSuccess: () => setConfirmOpen(false),
  });

  return (
    <Box>
      <SettingsCard
        title="Sauvegarde de la base de données"
        icon={<SaveOutlinedIcon />}
        color={severityColors.low}
        description="Produit un instantané complet (pg_dump, format personnalisé) et le télécharge dans le navigateur. Chaque export est tracé dans le journal d'audit."
        actions={
          <Button
            variant="contained"
            startIcon={<SaveOutlinedIcon fontSize="small" />}
            onClick={() => setConfirmOpen(true)}
          >
            Télécharger une sauvegarde
          </Button>
        }
      >
        <Alert severity="info" variant="outlined">
          La restauration n'est pas disponible depuis cette console — action jugée trop sensible
          pour être déclenchée en un clic. Contactez l'équipe DevSecOps pour une restauration.
        </Alert>
      </SettingsCard>

      <Dialog
        open={confirmOpen}
        onClose={() => (mutation.isPending ? undefined : setConfirmOpen(false))}
      >
        <DialogTitle>Confirmer la sauvegarde</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            Un export complet de la base de production va être généré (pg_dump) puis téléchargé.
            Cette opération peut prendre quelques instants selon le volume de données.
          </DialogContentText>
          {mutation.isError && (
            <Alert severity="error">
              {problemDetail(
                mutation.error,
                "Échec de la sauvegarde. L'outil pg_dump est peut-être indisponible.",
              )}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)} disabled={mutation.isPending}>
            Annuler
          </Button>
          <Button
            variant="contained"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending}
          >
            {mutation.isPending ? 'Génération…' : 'Confirmer'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

export default BackupSection;
