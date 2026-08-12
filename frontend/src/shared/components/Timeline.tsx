import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { alpha } from '@mui/material/styles';
import { softChipSx } from './chipStyles';
import MutedText from './MutedText';

export interface TimelineRow {
  key: string;
  /** Court libellé d'événement affiché en puce colorée (ex. "Statut", "Note"). */
  label: string;
  /** Couleur sémantique de l'événement — même teinte pour le point et la puce. */
  color: string;
  message: string;
  /** Date déjà formatée (fr-FR) par l'appelant. */
  date: string;
  onClick?: () => void;
}

/**
 * Fil d'activité horodaté : point coloré relié par une ligne verticale +
 * puce d'événement + message + date — même patron que « Activité récente »
 * du Dashboard, avec des puces teintées (voir {@link softChipSx}) plutôt que
 * neutres pour rester cohérent avec le reste de la plateforme.
 */
function Timeline({ rows }: { rows: TimelineRow[] }) {
  if (rows.length === 0) {
    return <MutedText>Aucune activité.</MutedText>;
  }
  return (
    <Stack spacing={0}>
      {rows.map((row, index) => (
        <Stack
          key={row.key}
          direction="row"
          spacing={1.5}
          sx={{ cursor: row.onClick ? 'pointer' : 'default' }}
          onClick={row.onClick}
        >
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 12 }}>
            <Box
              sx={{
                width: 9,
                height: 9,
                borderRadius: '50%',
                flexShrink: 0,
                mt: 0.6,
                bgcolor: row.color,
                boxShadow: `0 0 6px 1px ${alpha(row.color, 0.55)}`,
              }}
            />
            {index < rows.length - 1 && (
              <Box sx={{ width: '2px', flexGrow: 1, bgcolor: 'divider', mt: 0.5 }} />
            )}
          </Box>
          <Stack
            direction="row"
            spacing={1.5}
            sx={{ alignItems: 'center', flexGrow: 1, minWidth: 0, pb: 1.5, flexWrap: 'wrap' }}
            useFlexGap
          >
            <Chip size="small" label={row.label} sx={{ ...softChipSx(row.color), minWidth: 96 }} />
            <Typography variant="body2" sx={{ flexGrow: 1, minWidth: 120 }}>
              {row.message}
            </Typography>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ whiteSpace: 'nowrap', opacity: 0.65 }}
            >
              {row.date}
            </Typography>
          </Stack>
        </Stack>
      ))}
    </Stack>
  );
}

export default Timeline;
