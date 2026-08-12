import type { ReactNode } from 'react';
import Box from '@mui/material/Box';
import SectionLabel from './SectionLabel';

/** Couleur par défaut d'un intitulé de champ de détail — bleu doux, neutre, cohérent avec le Dashboard. */
export const DETAIL_FIELD_DEFAULT_COLOR = '#8ecfff';

/**
 * Section d'un tiroir de détail : intitulé coloré (voir {@link SectionLabel})
 * + valeur. Extrait du tiroir Alertes pour que tout tiroir de détail
 * (Incidents, Investigations, Actifs, Threat Intelligence...) ait la même
 * hiérarchie visuelle.
 */
function DetailField({
  label,
  color = DETAIL_FIELD_DEFAULT_COLOR,
  children,
}: {
  label: string;
  color?: string;
  children: ReactNode;
}) {
  return (
    <Box sx={{ mb: 1.75 }}>
      <SectionLabel color={color}>{label}</SectionLabel>
      {children}
    </Box>
  );
}

export default DetailField;
