import { alpha } from '@mui/material/styles';
import type { Theme } from '@mui/material/styles';
import type { SxProps } from '@mui/material/styles';

export type ChipPaletteColor = 'info' | 'warning' | 'secondary' | 'success' | 'error' | 'default';

/**
 * Badge "doux" : bordure + fond teinté à faible opacité, texte de la
 * couleur pleine — lisible en clair comme en sombre, sans le bloc plein
 * (trop criard) ni le simple `variant="outlined"` (invisible en clair).
 */
export function softChipSx(color: string): SxProps<Theme> {
  return {
    bgcolor: alpha(color, 0.14),
    color,
    border: '1px solid',
    borderColor: alpha(color, 0.55),
    fontWeight: 700,
  };
}

/** Résout un nom de couleur de palette MUI (ou "default") vers un hex exploitable par `softChipSx`. */
export function resolveChipColor(theme: Theme, name: ChipPaletteColor): string {
  if (name === 'default') return theme.palette.text.secondary;
  return theme.palette[name].main;
}
