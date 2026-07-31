import Box from '@mui/material/Box';
import Divider from '@mui/material/Divider';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { alpha } from '@mui/material/styles';

interface SettingsCardProps {
  title: string;
  description?: string;
  icon?: React.ReactNode;
  /** Puce d'état affichée à droite du titre (ex. AiZoneChip, StatusChip…). */
  statusChip?: React.ReactNode;
  /** Zone de boutons d'action, séparée du contenu par un filet. */
  actions?: React.ReactNode;
  children?: React.ReactNode;
}

/**
 * Carte "moderne" réutilisable de la console Paramètres : même patron
 * visuel dans toutes les sections (titre + icône + état, contenu,
 * actions optionnelles) — le composant que chaque section assemble,
 * jamais un style ad hoc par section.
 */
function SettingsCard({
  title,
  description,
  icon,
  statusChip,
  actions,
  children,
}: SettingsCardProps) {
  return (
    <Paper variant="outlined" sx={{ borderRadius: 3, overflow: 'hidden' }}>
      <Box sx={{ p: 2.5 }}>
        <Stack
          direction="row"
          spacing={1.5}
          sx={{ alignItems: 'flex-start', mb: description ? 1 : 0 }}
        >
          {icon && (
            <Box
              sx={{
                width: 40,
                height: 40,
                borderRadius: 2,
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'primary.main',
                bgcolor: (t) => alpha(t.palette.primary.main, 0.12),
              }}
            >
              {icon}
            </Box>
          )}
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                {title}
              </Typography>
              {statusChip}
            </Stack>
          </Box>
        </Stack>
        {description && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: children ? 2 : 0 }}>
            {description}
          </Typography>
        )}
        {children}
      </Box>
      {actions && (
        <>
          <Divider />
          <Stack
            direction="row"
            spacing={1}
            sx={{
              p: 1.5,
              justifyContent: 'flex-end',
              flexWrap: 'wrap',
              bgcolor: 'background.default',
            }}
          >
            {actions}
          </Stack>
        </>
      )}
    </Paper>
  );
}

/** Ligne label/valeur alignée — le contenu type d'une SettingsCard informative. */
export function SettingsRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Stack
      direction="row"
      spacing={2}
      sx={{ justifyContent: 'space-between', alignItems: 'center', py: 0.75 }}
    >
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      {/* Box, pas Typography : `value` peut contenir un Chip (une <div>),
          invalide dans un <p> — voir la doctrine "aucune erreur console". */}
      <Box sx={{ typography: 'body2', fontWeight: 600, textAlign: 'right' }}>{value}</Box>
    </Stack>
  );
}

export default SettingsCard;
