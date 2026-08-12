import type { ReactNode } from 'react';
import Typography from '@mui/material/Typography';

/**
 * Détail secondaire (dates, identifiants...) — texte estompé pour laisser
 * la valeur principale dominer. Extrait des tiroirs de détail (Alertes...)
 * pour un traitement visuel identique partout.
 */
function MutedText({ children }: { children: ReactNode }) {
  return (
    <Typography variant="body2" color="text.secondary" sx={{ opacity: 0.7 }}>
      {children}
    </Typography>
  );
}

export default MutedText;
