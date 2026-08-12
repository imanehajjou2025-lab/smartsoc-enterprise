import type { ReactNode } from 'react';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

/**
 * Titre de sous-section — barre d'accent colorée + texte majuscule teinté,
 * pour qu'un intitulé de bloc ("Source", "Actif concerné"...) se distingue
 * immédiatement de la valeur qu'il introduit. Extrait du Dashboard pour
 * que toute page qui en a besoin (Alertes...) obtienne le même rendu.
 */
function SectionLabel({ children, color }: { children: ReactNode; color: string }) {
  return (
    <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', mb: 0.75 }}>
      <Box sx={{ width: 3, height: 12, borderRadius: 999, bgcolor: color, flexShrink: 0 }} />
      <Typography
        variant="caption"
        sx={{ fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.6, color }}
      >
        {children}
      </Typography>
    </Stack>
  );
}

export default SectionLabel;
