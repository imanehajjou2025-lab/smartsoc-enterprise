import Box from '@mui/material/Box';
import { useThemeMode } from '../../app/ThemeModeProvider';

interface Props {
  /** 'mark' = bouclier seul (sidebar repliée, favicon-like) ; 'full' = lockup complet (login). */
  variant?: 'mark' | 'full';
  height?: number;
}

/**
 * Identité visuelle ISIX (logos fournis — voir docs/brand). Le lockup
 * complet existe en deux variantes de contraste (texte foncé / texte
 * clair) car il embarque le texte "ISIX" directement dans l'image :
 * on bascule sur celle du mode courant plutôt que de recomposer le texte
 * en CSS, pour rester pixel-identique au logo fourni.
 */
function BrandLogo({ variant = 'mark', height = 32 }: Props) {
  const { mode } = useThemeMode();
  const src =
    variant === 'mark' ? '/isix-mark.webp' : mode === 'dark' ? '/isix-logo-dark.png' : '/isix-logo-light.png';
  return (
    <Box component="img" src={src} alt="ISIX" sx={{ height, width: 'auto', display: 'block' }} />
  );
}

export default BrandLogo;
